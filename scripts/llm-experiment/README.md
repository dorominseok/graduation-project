# ① 분석 해설 LLM 통제 실험 하네스

`docs/LLM_연동_실험기록.md` §7의 실험을 자동으로 돌린다. Gemini 무료 티어 사용.
합성 판정 케이스 → Gemini 호출(구조화 출력) → 원칙 위반 자동 채점(V1~V7) → 규칙 폴백과 비교.

## 구성

| 파일 | 역할 |
|---|---|
| `analysis.py` | 부위별 주당 세트 수 → 8.2/8.3 응답 JSON 생성. `API_명세서.md` §8 판정 로직의 참조 구현 |
| `cases.py` | 실험 케이스 10개 (전부 최적 / 여러 부위 부족 / 한쪽 0세트 / 신뢰도 낮음 / 과다 / MIXED 배지 / 상하체 불균형 등) |
| `run.py` | 호출 + 검증 + 결과 표 출력 + 실행별 원본 보관 |
| `runs/{일시}_{모델}_{프롬프트버전}/` | **실행마다 새로 생성** (덮어쓰지 않음). git 미추적 — 보고서·발표용으로 로컬에 모아 둠 |
| `results.md` | 가장 최근 실행의 summary 사본 (편의용) |

### `runs/` 안에 들어가는 것

| 파일 | 내용 |
|---|---|
| `raw.jsonl` | 케이스별 **Gemini 원본 응답 전체** + 보낸 입력 JSON + 검증 결과. 한 줄 = 한 케이스 |
| `summary.md` | 결과 표(V1~V7 PASS/FAIL) + claims 전문 + 폴백 비교 |
| `results.json` | 검증·메타(토큰·지연) 요약 |
| `prompt.txt` | 그 실행에 쓴 SYSTEM 프롬프트·설정 스냅샷 (버전마다 다름) |

## 준비

1. [aistudio.google.com](https://aistudio.google.com)에서 API 키 발급 (무료, 카드 불필요)
2. 리포지토리 루트 `.env`에 추가:
   ```
   GEMINI_API_KEY=발급받은_키
   ```
   `.env`는 `.gitignore`에 있어 커밋되지 않는다. 키를 코드/문서/로그/커밋에 남기지 말 것.
3. Python 3.9+ (표준 라이브러리만 사용, `pip install` 불필요)

## 실행

```bash
python scripts/llm-experiment/run.py            # 전체 10케이스
python scripts/llm-experiment/run.py 3          # 앞 3케이스만 (빠른 확인)
python scripts/llm-experiment/run.py --dry-run  # API 호출 없이 프롬프트만 출력
python scripts/llm-experiment/run.py --model gemini-3.5-flash-lite
```

무료 티어 RPM 제한 때문에 호출 사이 7초 대기한다. 10케이스 ≈ 2분.
`429`/`503`이 뜨면 잠깐 쉬고 1회 재시도, 그래도 실패하면 그 케이스는 폴백으로 넘어간다.

> 모델: `gemini-3.6-flash` 기본. `gemini-2.5-flash`는 2026-08 기준 신규 계정에 404다.
> lite 비교는 `--model gemini-3.5-flash-lite`.

## 검증 항목 (콘솔·`results.md`에 PASS/FAIL/WARN)

| | 검사 | 실패 = |
|---|---|---|
| V1 | 인용한 세트 수가 입력 JSON에 실제로 존재 | 수치 지어냄 |
| V2 | 인용한 판정 라벨이 서버 판정과 일치 | 부족↔권장 이하 등 바꿔 말함 |
| V3 | `citedTier`가 입력 label 목록 안 / 지어낸 부위명 없음 | "회전근개" 등 |
| V4 | 문장 속 숫자가 전부 입력 수치 집합 안 (보조지표 → WARN) | 근거 없는 "20%", "3배" |
| V5 | 처방형 종결 어미 없음 (`~하세요`, `~해야`, `~권장합니다`, `~보완하…` 등) | "세트를 늘리세요" |
| V6 | claims 2~3개, 각 120자 이내 | 장황 |
| V7 | 구조화 출력이 스키마대로 파싱됨 | 형식 불안정 |

호출 자체 실패(타임아웃·차단·429 2회)는 `오류` 열에 표시되고 폴백 문장으로 대체된다.

## 결과를 문서에 반영

`results.md`의 표를 `docs/LLM_연동_실험기록.md` §7.3 "프롬프트 버전별" 표에 붙여넣고,
실패 유형을 보고 `run.py`의 `SYSTEM_PROMPT`를 고쳐 재실행한다. 통과율이 안정되면
`--model`을 바꿔 `gemini-3.6-flash` vs `flash-lite` 통과율·지연을 비교한다.

## 주의

- 이 하네스는 **버릴 코드**다. 본 앱(`backend/`)에 들어가는 게 아니라, 통제가 되는지 먼저 보는 용도.
- 실제 호출은 네 Gemini 무료 쿼터를 소모한다(비용 0, 일 250회 한도).
- 무료 티어는 프롬프트가 Google 모델 학습에 쓰일 수 있다. 여기 입력은 전부 합성 데이터라 무방하지만,
  실제 사용자 데이터로 옮길 때는 재검토(실험기록 §10).
