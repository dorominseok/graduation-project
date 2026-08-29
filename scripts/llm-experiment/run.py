#!/usr/bin/env python3
"""
① 분석 해설 LLM 통제 실험 하네스 (Gemini 무료 티어)

사용법:
    python scripts/llm-experiment/run.py            # 전체 10케이스, 실제 호출
    python scripts/llm-experiment/run.py 3          # 앞 3케이스만
    python scripts/llm-experiment/run.py --dry-run  # API 호출 없이 프롬프트만 출력
    python scripts/llm-experiment/run.py --model gemini-2.5-flash-lite

키: 리포지토리 루트 .env 의 GEMINI_API_KEY 를 읽는다. 코드/로그에 키를 남기지 않는다.
결과: 같은 폴더에 results.json / results.md 저장. results.md 는 실험기록 §7.3에 붙여넣는 용도.
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

try:  # Windows 콘솔에서 한글 깨짐 방지
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

sys.path.insert(0, str(Path(__file__).resolve().parent))
from analysis import build_muscle_volume, build_balance  # noqa: E402
from cases import CASES  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
OUT_DIR = Path(__file__).resolve().parent
RUNS_DIR = OUT_DIR / "runs"          # 실행별 원본 보관 (git 미추적 — .gitignore)
PROMPT_VERSION = "v3"
DEFAULT_MODEL = "gemini-3.6-flash"   # 2.5-flash는 신규 계정에 404 (2026-08 확인)
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
CALL_INTERVAL_SEC = 7          # 무료 flash 10 RPM 대비
REQUEST_TIMEOUT_SEC = 30

# ---------------------------------------------------------------- 프롬프트

SYSTEM_PROMPT = """너는 헬스 앱의 분석 결과를 사용자에게 설명하는 역할이다. 아래 규칙을 예외 없이 지켜라.

[할 일]
- 입력으로 받은 판정 결과 JSON을 한국어로 풀어서 설명한다.
- 어느 부위가 부족/과다한지, 밀기·당기기나 상·하체 균형이 어떤지 '사실'만 전달한다.

[반드시 다룰 것 — 빠뜨리면 안 됨]
- verdict 가 INSUFFICIENT(부족) / EXCESSIVE(과다) 인 부위는 전부 언급한다.
- 그런 부위와 BELOW_RECOMMENDED(권장 이하) 부위를 합쳐 4곳 이상이면, 개별 나열 대신
  "전반적으로 볼륨이 부족합니다"처럼 요약한다(대표 1~2곳만 예로 든다).
- 균형(pairs)에 verdict 가 IMBALANCED(불균형) 인 쌍이 있으면 반드시 언급한다.
  이때 어느 쌍(밀기/당기기 또는 상체/하체)인지와 ratio 값(몇 배인지)을 문장에 넣는다.
  단 smallerSideZero 가 true 면 ratio 대신 "한쪽이 0세트라 비율을 낼 수 없다"고 말한다.
- confidence.level 이 LOW 이면, 판정 신뢰도가 낮다는 문장을 반드시 포함한다.

[전부 정상일 때만]
- 위 네 가지(부족/과다/권장 이하 부위, 불균형 쌍, LOW 신뢰도)가 하나도 없을 때에 한해
  "부위별 볼륨과 균형이 권장 범위 안에 있습니다" 한 문장만 낸다.
- 지적할 것이 하나라도 있으면 이 문장을 절대 쓰지 않는다. 지적 내용만 말한다.

[금지]
- 입력 JSON에 없는 수치를 만들어내지 않는다. 세트 수·비율·기간은 입력값만 인용한다.
- 판정 라벨(부족 / 권장 이하 / 최적 / 과다)을 입력과 다르게 바꾸지 않는다.
- 부위 이름은 입력 JSON의 label 값만 쓴다. 새 부위명(회전근개, 승모근 등)을 지어내지 않는다.
- 처방하지 않는다. "세트를 늘리세요", "무게를 올리세요", "보완해야 합니다", "권장합니다" 같은
  지시·명령·권고 표현을 쓰지 않는다. 현황만 알린다. 판단은 사용자 몫이다.
- 진단, 의학적 조언, 부상 위험 단정을 하지 않는다.

[출력 형식]
- JSON. claims 배열로 반환한다. 각 원소는:
  - sentence: 사용자에게 보일 한국어 문장 하나 (100자 이내)
  - citedTier: 그 문장이 근거로 삼은 부위의 label 값. 균형·신뢰도 문장이면 null.
  - citedWeeklySets: 그 문장이 인용한 주당 세트 수. 입력값을 그대로 복사한다. 없으면 null.
  - citedVerdict: 그 문장이 인용한 판정 라벨(부족/권장 이하/최적/과다). 없으면 null.
- claims는 2~4개. 일반론(수치·부위 근거 없는 문장)은 넣지 않는다.

[예시]
입력에 어깨(뒤) weeklySets 1.0, verdictLabel "부족", 밀기/당기기 ratio 2.5, verdict "불균형" 이 있으면:
{"claims":[
  {"sentence":"최근 4주간 어깨(뒤) 운동이 주 1세트로 부족 범위에 있습니다.","citedTier":"어깨(뒤)","citedWeeklySets":1.0,"citedVerdict":"부족"},
  {"sentence":"밀기가 당기기의 2.5배로 균형 기준(2배)을 넘었습니다.","citedTier":null,"citedWeeklySets":null,"citedVerdict":null}
]}
"""

RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "claims": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "sentence": {"type": "string"},
                    "citedTier": {"type": "string", "nullable": True},
                    "citedWeeklySets": {"type": "number", "nullable": True},
                    "citedVerdict": {"type": "string", "nullable": True},
                },
                "required": ["sentence"],
            },
        }
    },
    "required": ["claims"],
}


def build_user_prompt(mv, bal):
    return (
        "다음은 최근 4주 판정 결과다. 이 안의 수치와 부위 label 만 사용해서 설명해라.\n\n"
        "<판정결과>\n" + json.dumps(mv, ensure_ascii=False) + "\n</판정결과>\n\n"
        "<균형판정>\n" + json.dumps(bal, ensure_ascii=False) + "\n</균형판정>\n"
    )


# ---------------------------------------------------------------- Gemini 호출

def load_api_key():
    if os.environ.get("GEMINI_API_KEY"):
        return os.environ["GEMINI_API_KEY"].strip()
    if not ENV_PATH.exists():
        sys.exit(f"[중단] {ENV_PATH} 없음. .env 에 GEMINI_API_KEY=... 를 넣어라.")
    for line in ENV_PATH.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line.startswith("GEMINI_API_KEY="):
            v = line.split("=", 1)[1].strip().strip('"').strip("'")
            if v:
                return v
    sys.exit("[중단] .env 에 GEMINI_API_KEY 값이 비어 있다.")


class GeminiError(RuntimeError):
    """응답은 받았으나 사용할 수 없는 경우. raw 에 원본 응답을 담는다."""
    def __init__(self, msg, raw=None):
        super().__init__(msg)
        self.raw = raw


def call_gemini(system, user, model, key):
    """(parsed_dict, meta, raw) 반환. HTTP/네트워크 실패는 RuntimeError, 응답 문제는 GeminiError."""
    body = json.dumps({
        "systemInstruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": user}]}],
        "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": RESPONSE_SCHEMA,
            "temperature": 0,
            # gemini-3.x flash는 thinking이 켜져 있어 예산을 먼저 소모한다(케이스별 750~3000tok).
            # 끄는 파라미터가 v1beta에서 거부되므로 예산을 넉넉히 준다.
            "maxOutputTokens": 8000,
        },
    }).encode("utf-8")

    req = urllib.request.Request(
        ENDPOINT.format(model=model),
        data=body,
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )

    for attempt in (1, 2):
        try:
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SEC) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            break
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:400]
            if e.code in (429, 503) and attempt == 1:
                wait = 30 if e.code == 429 else 15
                print(f"    · HTTP {e.code} (일시적) → {wait}초 대기 후 1회 재시도")
                time.sleep(wait)
                continue
            raise RuntimeError(f"HTTP {e.code}: {detail}")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == 1:
                print(f"    · 네트워크 오류({e}) → 5초 후 재시도")
                time.sleep(5)
                continue
            raise RuntimeError(f"network: {e}")

    pf = data.get("promptFeedback", {})
    if pf.get("blockReason"):
        raise GeminiError(f"blocked: {pf['blockReason']}", raw=data)
    cands = data.get("candidates") or []
    if not cands:
        raise GeminiError("no candidates", raw=data)
    fr = cands[0].get("finishReason")
    usage = data.get("usageMetadata", {})
    meta = {
        "finishReason": fr,
        "promptTokens": usage.get("promptTokenCount"),
        "outputTokens": usage.get("candidatesTokenCount"),
        "thoughtsTokens": usage.get("thoughtsTokenCount"),
    }
    if fr == "MAX_TOKENS":
        raise GeminiError("MAX_TOKENS: 출력이 잘림 (thinking이 예산을 소모)", raw=data)
    if fr and fr != "STOP":
        raise GeminiError(f"finishReason={fr}", raw=data)
    parts = cands[0].get("content", {}).get("parts", [])
    text = "".join(p.get("text", "") for p in parts).strip()
    if not text:
        raise GeminiError("empty text", raw=data)
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as e:
        raise GeminiError(f"JSON 파싱 실패(V7): {e}", raw=data)
    return parsed, meta, data


# ---------------------------------------------------------------- 검증 (V1~V7)

PRESCRIPTIVE = re.compile(
    r"(하세요|하십시오|하시기 바랍|해야\s*(합니다|한다|해요|겠)|필요합니다|필요하다|"
    r"권장(합니다|한다|됩니다|해요|드립니다)|추천(합니다|해요|드립니다|드려요)|"
    r"보완하(세요|셔야|시고|시길|는 것이)|늘리(세요|셔야|시길|는 것이|는 것을)|"
    r"줄이(세요|셔야|시길)|추가하(세요|셔야)|높이(세요|셔야)|올리(세요|셔야)|채우(세요|셔야)|"
    r"하는 것이 좋(습니다|겠|아요)|바랍니다)"
)

INVENTED_PARTS = ["회전근개", "승모근", "광배근", "대흉근", "능형근", "척추기립근",
                  "전거근", "복직근", "장요근", "비복근", "가자미근"]


def _collect(mv, bal):
    label_weekly, label_verdicts, all_weekly = {}, {}, set()
    for t in mv["tiers"]:
        label_weekly[t["label"]] = t["weeklySets"]
        all_weekly.add(t["weeklySets"])
        vs = set()
        for c in t["children"]:
            label_weekly[c["label"]] = c["weeklySets"]
            all_weekly.add(c["weeklySets"])
            label_verdicts.setdefault(c["label"], set()).add(c["verdictLabel"])
            vs.add(c["verdictLabel"])
        label_verdicts[t["label"]] = vs
    for d in mv["displayOnly"]:
        label_weekly[d["label"]] = d["weeklySets"]
        all_weekly.add(d["weeklySets"])
    ratios = set()
    for p in bal["pairs"]:
        label_weekly[p["left"]["label"]] = p["left"]["weeklySets"]
        label_weekly[p["right"]["label"]] = p["right"]["weeklySets"]
        all_weekly.add(p["left"]["weeklySets"])
        all_weekly.add(p["right"]["weeklySets"])
        if p["ratio"] is not None:
            ratios.add(round(p["ratio"], 2))
    total_sets = {c["totalSets"] for t in mv["tiers"] for c in t["children"]}
    total_sets |= {t["totalSets"] for t in mv["tiers"]}
    total_sets |= {d["totalSets"] for d in mv["displayOnly"]}
    allowed_nums = set()
    for x in all_weekly | ratios | {float(n) for n in total_sets}:
        allowed_nums.add(round(float(x), 2))
    allowed_nums |= {2.0, 4.0, float(mv["confidence"]["doneSessionCount"]),
                     float(mv["confidence"]["threshold"])}
    return label_weekly, label_verdicts, all_weekly, allowed_nums


def validate(commentary, mv, bal):
    label_weekly, label_verdicts, all_weekly, allowed_nums = _collect(mv, bal)
    claims = commentary.get("claims", [])
    r = {}
    notes = []

    # V7 형식/스키마
    ok7 = isinstance(claims, list) and len(claims) >= 1 and all(
        isinstance(c, dict) and isinstance(c.get("sentence"), str) for c in claims)
    r["V7"] = "PASS" if ok7 else "FAIL"
    if not ok7:
        notes.append("V7: claims 구조가 스키마와 다름")
        return r, notes

    # V1 인용 수치가 입력에 존재
    bad1 = []
    for c in claims:
        w = c.get("citedWeeklySets")
        if w is None:
            continue
        if abs(w) > 1000:  # 26.0E00000...5 같은 지수표기 폭주 (flash-lite 직렬화 결함)
            bad1.append(f"[숫자 직렬화 결함] {c['sentence']} (citedWeeklySets={w})")
        elif not any(abs(w - x) < 0.05 for x in all_weekly):
            bad1.append(f"입력에 없는 세트 수 → {c['sentence']} (citedWeeklySets={w})")
    r["V1"] = "PASS" if not bad1 else "FAIL"
    notes += [f"V1: {s}" for s in bad1]

    # V2 인용 판정이 서버 판정과 일치 (부위 문장에만 적용 — citedTier None = 균형 문장은 대상 아님)
    bad2 = []
    for c in claims:
        cv, ct = c.get("citedVerdict"), c.get("citedTier")
        if cv is None or ct is None:
            continue
        allowed = label_verdicts.get(ct, set())
        if cv not in allowed:
            bad2.append(f"{ct}={cv} (서버: {'/'.join(sorted(allowed)) or '?'})")
    r["V2"] = "PASS" if not bad2 else "FAIL"
    notes += [f"V2: 판정 라벨 불일치 → {s}" for s in bad2]

    # V3 부위명 화이트리스트
    valid_labels = set(label_weekly) | {None}
    bad3 = [str(c.get("citedTier")) for c in claims if c.get("citedTier") not in valid_labels]
    invented = sorted({w for c in claims for w in INVENTED_PARTS if w in c["sentence"]})
    r["V3"] = "PASS" if not bad3 and not invented else "FAIL"
    notes += [f"V3: 잘못된 citedTier → {s}" for s in bad3]
    notes += [f"V3: 지어낸 부위명 → {w}" for w in invented]

    # V4 문장 속 숫자 토큰 (보조 — WARN)
    off = []
    for c in claims:
        for tok in re.findall(r"\d+(?:\.\d+)?", c["sentence"]):
            v = float(tok)
            if v in (1, 2, 3, 4):
                continue
            if not any(abs(v - a) < 0.01 for a in allowed_nums):
                off.append(f"{tok} @ {c['sentence']}")
    r["V4"] = "PASS" if not off else "WARN"
    notes += [f"V4: 근거 없는 숫자 → {s}" for s in off]

    # V5 처방 표현
    hits = [(m.group(0), c["sentence"]) for c in claims
            for m in [PRESCRIPTIVE.search(c["sentence"])] if m]
    r["V5"] = "PASS" if not hits else "FAIL"
    notes += [f"V5: 처방형 표현 '{h}' → {s}" for h, s in hits]

    # V6 길이 (순수 형식만 — 0개 / 6개 초과 / 120자 초과면 FAIL)
    long_s = [c["sentence"] for c in claims if len(c["sentence"]) > 120]
    n = len(claims)
    r["V6"] = "FAIL" if (long_s or n == 0 or n > 6) else "PASS"
    if long_s:
        notes.append(f"V6: 120자 초과 {len(long_s)}건")
    if n == 0 or n > 6:
        notes.append(f"V6: claims {n}개")

    # 커버리지 (통제 아님 — 반드시 다뤄야 할 걸 빠뜨렸는지. 통과/실패에 안 넣고 note만)
    joined = " ".join(c["sentence"] for c in claims)
    if mv["confidence"]["level"] == "LOW" and not re.search(r"신뢰(도|성)|믿|적어", joined):
        notes.append("[coverage] confidence LOW인데 신뢰도 문장 없음")
    if any(p["verdict"] == "IMBALANCED" for p in bal["pairs"]) and not re.search(r"불균형|비율|배|ratio", joined):
        notes.append("[coverage] IMBALANCED 쌍 미언급")
    summ = "전반적으로" in joined or "전체적으로" in joined
    missed = [c["label"] for t in mv["tiers"] for c in t["children"]
              if c["verdict"] in ("INSUFFICIENT", "EXCESSIVE") and c["label"] not in joined]
    if missed and not summ:
        notes.append(f"[coverage] 부족/과다 부위 미언급: {', '.join(missed)}")

    return r, notes


# ---------------------------------------------------------------- 규칙 기반 폴백

def rule_based_fallback(mv, bal):
    out = []
    for t in mv["tiers"]:
        for c in t["children"]:
            if c["verdict"] == "INSUFFICIENT":
                out.append(f"최근 4주간 {c['label']}이(가) 주 {c['weeklySets']}세트로 부족 범위입니다.")
            elif c["verdict"] == "EXCESSIVE":
                out.append(f"최근 4주간 {c['label']}이(가) 주 {c['weeklySets']}세트로 과다 범위입니다.")
    for p in bal["pairs"]:
        if p["verdict"] == "IMBALANCED":
            big = p["left"] if p["biggerSide"] == p["left"]["key"] else p["right"]
            small = p["right"] if big is p["left"] else p["left"]
            if p["smallerSideZero"]:
                out.append(f"{small['label']} 세트가 0이라 {big['label']}와의 비율을 낼 수 없습니다.")
            else:
                out.append(f"{big['label']} 세트가 {small['label']}의 {p['ratio']}배로 균형 기준(2배)을 넘었습니다.")
    if mv["confidence"]["level"] == "LOW":
        out.append(f"최근 4주 완료 운동이 {mv['confidence']['doneSessionCount']}회로 적어 판정 신뢰도가 낮습니다.")
    if not out:
        out.append("최근 4주간 부위별 볼륨과 균형이 권장 범위 안에 있습니다.")
    return " ".join(out[:4])


# ---------------------------------------------------------------- main

def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args
    model = DEFAULT_MODEL
    if "--model" in args:
        model = args[args.index("--model") + 1]
    limit = next((int(a) for a in args if a.isdigit()), len(CASES))
    cases = CASES[:limit]

    if dry:
        mv = build_muscle_volume(cases[0]["weekly"], cases[0]["done"])
        bal = build_balance(cases[0]["weekly"])
        print("=== SYSTEM ===\n" + SYSTEM_PROMPT)
        print("=== USER (case 1) ===\n" + build_user_prompt(mv, bal))
        return

    key = load_api_key()
    print(f"모델: {model} · 케이스: {len(cases)}개 · 호출 간격: {CALL_INTERVAL_SEC}s\n")

    results = []
    for i, case in enumerate(cases, 1):
        mv = build_muscle_volume(case["weekly"], case["done"])
        bal = build_balance(case["weekly"])
        fb = rule_based_fallback(mv, bal)
        print(f"[{i}/{len(cases)}] {case['id']} — {case['desc']}")

        rec = {"id": case["id"], "desc": case["desc"], "fallback": fb,
               "input": {"muscleVolume": mv, "balance": bal}, "raw": None}
        try:
            t0 = time.time()
            commentary, meta, raw = call_gemini(SYSTEM_PROMPT, build_user_prompt(mv, bal), model, key)
            rec["latencyMs"] = round((time.time() - t0) * 1000)
            rec["meta"], rec["raw"] = meta, raw
            rec["claims"] = commentary.get("claims", [])
            verd, notes = validate(commentary, mv, bal)
            rec["validation"], rec["notes"] = verd, notes
            for c in rec["claims"]:
                print(f"    · {c['sentence']}")
            print(f"    {verd}  ({rec['latencyMs']}ms, in {meta.get('promptTokens')} / "
                  f"out {meta.get('outputTokens')} / think {meta.get('thoughtsTokens')})")
            for n in notes:
                print(f"      ! {n}")
        except GeminiError as e:
            rec["error"], rec["raw"] = str(e), e.raw
            rec["validation"] = {"V7": "FAIL"} if "V7" in str(e) else {"ERROR": "FAIL"}
            if e.raw:
                u = e.raw.get("usageMetadata", {})
                rec["meta"] = {"finishReason": (e.raw.get("candidates") or [{}])[0].get("finishReason"),
                               "promptTokens": u.get("promptTokenCount"),
                               "outputTokens": u.get("candidatesTokenCount"),
                               "thoughtsTokens": u.get("thoughtsTokenCount")}
            print(f"    [오류] {e}  → 폴백 사용")
        except Exception as e:  # noqa: BLE001
            rec["error"] = str(e)
            rec["validation"] = {"ERROR": "FAIL"}
            print(f"    [오류] {e}  → 폴백 사용")
        print(f"    (폴백) {fb}\n")
        results.append(rec)
        if i < len(cases):
            time.sleep(CALL_INTERVAL_SEC)

    _write_outputs(model, results)


def _summary_md(model, results):
    keys = ["V1", "V2", "V3", "V4", "V5", "V6", "V7"]
    n = len(results)
    lines = [f"# 실험 결과 — {model} / 프롬프트 {PROMPT_VERSION}",
             f"실행: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M')}", "",
             "| 케이스 | " + " | ".join(keys) + " | 오류 | 지연ms | in/out/think tok |",
             "|" + "---|" * (len(keys) + 4)]
    tally = {k: 0 for k in keys}
    n_err = 0
    for r in results:
        v = r.get("validation", {})
        row = [r["id"]] + [v.get(k, "-") for k in keys]
        for k in keys:
            if v.get(k) == "PASS":
                tally[k] += 1
        err = "Y" if r.get("error") else ""
        n_err += 1 if err else 0
        m = r.get("meta", {})
        row += [err, str(r.get("latencyMs", "")),
                f"{m.get('promptTokens', '')}/{m.get('outputTokens', '')}/{m.get('thoughtsTokens', '')}"]
        lines.append("| " + " | ".join(row) + " |")
    lines += ["", f"PASS 집계 (n={n}): " + ", ".join(f"{k} {tally[k]}/{n}" for k in keys),
              f"호출 오류: {n_err}/{n}", "", "## claims 전문 / 폴백 비교", ""]
    for r in results:
        lines.append(f"### {r['id']} — {r['desc']}")
        if r.get("error"):
            lines.append(f"- 오류: {r['error']}")
        for c in r.get("claims", []):
            lines.append(f"- LLM: {c['sentence']}  "
                         f"`(tier={c.get('citedTier')}, ws={c.get('citedWeeklySets')}, v={c.get('citedVerdict')})`")
        lines.append(f"- 폴백: {r['fallback']}")
        for note in r.get("notes", []):
            lines.append(f"- ! {note}")
        lines.append("")
    return "\n".join(lines)


def _write_outputs(model, results):
    ts = datetime.datetime.now().strftime("%Y-%m-%d_%H%M")
    run_dir = RUNS_DIR / f"{ts}_{model}_{PROMPT_VERSION}"
    run_dir.mkdir(parents=True, exist_ok=True)

    # raw.jsonl — 케이스별 Gemini 원본 응답 + 입력 (보고서·발표 근거)
    with (run_dir / "raw.jsonl").open("w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps({
                "id": r["id"], "desc": r["desc"],
                "input": r.get("input"),
                "geminiResponse": r.get("raw"),
                "claims": r.get("claims"), "meta": r.get("meta"),
                "validation": r.get("validation"), "notes": r.get("notes"),
                "error": r.get("error"), "fallback": r["fallback"],
                "latencyMs": r.get("latencyMs"),
            }, ensure_ascii=False) + "\n")

    md = _summary_md(model, results)
    (run_dir / "summary.md").write_text(md, encoding="utf-8")

    lean_keys = ("id", "desc", "claims", "meta", "validation", "notes", "error", "fallback", "latencyMs")
    (run_dir / "results.json").write_text(json.dumps(
        {"model": model, "promptVersion": PROMPT_VERSION, "ts": ts,
         "results": [{k: r.get(k) for k in lean_keys} for r in results]},
        ensure_ascii=False, indent=2), encoding="utf-8")

    c0 = CASES[0]
    sample = build_user_prompt(build_muscle_volume(c0["weekly"], c0["done"]), build_balance(c0["weekly"]))
    (run_dir / "prompt.txt").write_text(
        f"모델: {model}\n프롬프트 버전: {PROMPT_VERSION}\n"
        f"generationConfig: temperature 0, maxOutputTokens 8000, responseSchema(구조화 출력)\n\n"
        f"=== SYSTEM ===\n{SYSTEM_PROMPT}\n\n=== USER (예: {c0['id']}) ===\n{sample}\n",
        encoding="utf-8")

    (OUT_DIR / "results.md").write_text(md, encoding="utf-8")  # 최신 실행 편의 사본
    print(f"\n보관: {run_dir}")
    for name, what in [("summary.md", "결과 표 + claims 전문"),
                       ("raw.jsonl", "케이스별 Gemini 원본 응답 + 입력 (보고서용)"),
                       ("results.json", "검증·메타 요약"),
                       ("prompt.txt", "이번 실행 프롬프트·설정 스냅샷")]:
        print(f"  · {name:<13} {what}")


if __name__ == "__main__":
    main()
