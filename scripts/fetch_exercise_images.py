#!/usr/bin/env python3
"""종목 동작 그림 내려받기 → webp 변환.

두 곳에서 받는다. 먼저 everkinetic의 선화를 쓰고, 거기 없는 종목만
free-exercise-db의 사진으로 메운다.

    pip install pillow
    python scripts/fetch_exercise_images.py          # 그림이 없는 종목만
    python scripts/fetch_exercise_images.py --all    # 전부 다시

선화를 앞세우는 것은 목록이 2열 그리드라 한 화면에 열 몇 장이 깔리기 때문이다.
체육관 실사는 장마다 배경·조명·인물이 달라 목록이 산만해진다. 흰 바탕 선화는
카드 배경과 이어져 그림 둘레에 네모가 생기지 않는다.

파일명은 DB의 `id`가 아니라 **영문명 슬러그**다. `exercises.id`는 BIGSERIAL이고
시드에 번호가 박혀 있지 않아, CSV 순서를 바꾼 뒤 DB를 새로 만들면 번호가 밀린다.
그러면 벤치프레스 자리에 스쿼트 그림이 조용히 나온다. 슬러그는 어긋나면 그림이
안 뜰 뿐이라 바로 눈에 띈다. 같은 규칙이 프론트 `exerciseImage.ts`에도 있다.

출처와 라이선스는 `frontend/public/exercises/CREDITS.md`에 적어둔다.
"""

import csv
import io
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("pillow가 필요하다: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "backend/src/main/resources/data/exercises.csv"
OUT_DIR = ROOT / "frontend/public/exercises"

EVER = "https://raw.githubusercontent.com/everkinetic/data/master/dist/"
PHOTO = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"

# 카드와 상세가 같은 파일을 쓴다. 더 키우면 용량이 배로 늘고, 줄이면 상세가 흐리다.
WIDTH = 400
QUALITY = 80
# 이 아래 점수로 짝지으면 다른 운동일 위험이 커서 사진으로 넘긴다.
THRESHOLD = 0.75

# everkinetic과 우리 CSV는 이름 체계가 다르다("Bench Press: Barbell" 대
# "Barbell Bench Press - Medium Grip"). 자동 대조가 놓친 것은 후보를 눈으로 보고
# 사람이 짝지었다. 기구까지 같은 것만 넣는다 — 칩에는 "케이블"이라 떠 있는데
# 그림에 바벨이 있으면 오히려 헷갈린다.
MANUAL = {
    "Pushups": "Push Up: Body Weight",
    "Butterfly": "Butterfly Machine",
    "Barbell Deadlift": "Dead Lifts: Barbell",
    "Romanian Deadlift": "Romanian Dead Lift",
    "Stiff-Legged Dumbbell Deadlift": "Dead Lifts: Dumbbell",
    "Hyperextensions With No Hyperextension Bench": "Hyperextensions",
    "Glute Kickback": "One Legged Cable Kickback",
    "Barbell Lunge": "Lunges: Barbell",
    "Reverse Flyes": "Lying Rear Lateral Raise",
    "Parallel Bar Dip": "Chest Dips",
    "Bodyweight Squat": "Squats: Dumbbells",
    # 2026-11-16 추가분
    "Hyperextensions (Back Extensions)": "Hyperextensions",
    "Seated Calf Raise": "Seated Calf Raise using Machine",
    "Preacher Curl": "Preacher Curl: Barbell",
    # free-exercise-db에 없는 변형 — 동작이 같은 원래 종목의 선화를 같이 쓴다
    "Weighted Hyperextensions": "Hyperextensions",
    "Weighted Dips": "Triceps Dips: Body Weight",
    "Dumbbell Romanian Deadlift": "Dead Lifts: Dumbbell",
}

# 어디에도 맞는 그림이 없는 변형 — 이미 받아 둔 원래 종목의 그림 파일을 복사한다.
# everkinetic의 "Chin Ups"는 그림 파일이 비어 있다.
SAME_AS = {"Weighted Chin-Up": "Chin-Up"}

# 자동 대조가 엉뚱한 선화를 고르는 종목. 선화를 건너뛰고 사진으로 간다(사진도 없으면 그림 없음).
# 2026-11-16 추가분을 눈으로 확인해 골랐다 — 클로즈그립 랫풀다운에 벤치프레스, 원암 랫풀다운에
# 스내치가 붙는 식이다. 이름이 비슷한 다른 동작이거나 기구·각도가 다른 그림이다.
NO_DRAWING = {
    "Dumbbell Bench Press with Neutral Grip", "Standing Cable Chest Press", "Low Cable Crossover",
    "Single-Arm Cable Crossover", "V-Bar Pullup", "Close-Grip Front Lat Pulldown", "One Arm Lat Pulldown",
    "Rope Straight-Arm Pulldown", "Bent-Arm Barbell Pullover", "Incline Bench Pull", "Bent Over Two-Dumbbell Row",
    "Dumbbell Incline Row", "Seated One-arm Cable Pulley Rows", "One-Arm Long Bar Row",
    "Smith Machine Bent Over Row", "Plie Dumbbell Squat", "Split Squat with Dumbbells", "Single-Leg Leg Extension",
    "Stiff-Legged Barbell Deadlift", "Smith Machine Stiff-Legged Deadlift", "Single Leg Glute Bridge",
    "Smith Machine Calf Raise", "Smith Machine Overhead Shoulder Press", "Seated Side Lateral Raise",
    "One-Arm Incline Lateral Raise", "Reverse Machine Flyes", "Cable Rear Delt Fly",
    "Seated Bent-Over Rear Delt Raise", "Cable Rope Rear-Delt Rows", "Incline Hammer Curls",
    "Cable Rope Overhead Triceps Extension", "Palms-Down Dumbbell Wrist Curl Over A Bench", "Oblique Crunches",
    "Hanging Leg Raise", "Standing Cable Wood Chop",
    # free-exercise-db에 없는 종목 — 맞는 선화가 없다
    "Low Row Machine", "Back Extension Machine", "V Squat Machine", "Belt Squat Machine",
    "Pendulum Squat Machine", "Dumbbell Bulgarian Split Squat", "Smith Machine Hip Thrust",
    "Machine Lateral Raise", "Assisted Dip Machine", "Assisted Pull-Up Machine", "Front Pulldown Machine",
    "Hip Thrust Machine", "Glute Kickback Machine", "Landmine Press",
}

# 띄어쓰기가 갈리는 동작어를 하나로 붙인다. everkinetic은 "Dead Lift"로 띄어 쓴다.
JOIN = [("dead lift", "deadlift"), ("push up", "pushup"), ("pull up", "pullup"),
        ("sit up", "situp"), ("chin up", "chinup"), ("push down", "pushdown"),
        ("pull down", "pulldown"), ("step up", "stepup"), ("kick back", "kickback"),
        ("roll out", "rollout"), ("cross over", "crossover")]
STOP = {"with", "the", "and", "a", "an", "on", "in", "of", "to", "or", "version"}
SYN = {"flye": "fly", "flys": "fly", "flyes": "fly", "dip": "dips",
       "bicep": "biceps", "tricep": "triceps", "ab": "abs", "abdominal": "abs"}


def slug(name_en: str) -> str:
    """영문명 → 파일명. 프론트 `exerciseImage.ts`의 같은 규칙과 일치해야 한다."""
    return re.sub(r"[^a-z0-9]+", "-", name_en.lower()).strip("-")


def tokens(name: str) -> set[str]:
    text = name.lower().replace("-", " ")
    for before, after in JOIN:
        text = text.replace(before, after)
    out = set()
    for token in re.split(r"[^a-z0-9]+", text):
        if not token or token in STOP:
            continue
        if len(token) > 3 and token.endswith("es"):
            token = token[:-2]
        elif len(token) > 3 and token.endswith("s"):
            token = token[:-1]
        out.add(SYN.get(token, token))
    return out


def fetch(url: str) -> bytes | None:
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.read()
    except urllib.error.HTTPError:
        return None


def save(raw: bytes, path: Path) -> int:
    """흰 바탕 위에 올려 폭을 맞추고 webp로 저장한다."""
    with Image.open(io.BytesIO(raw)) as image:
        if image.mode in ("RGBA", "LA", "P"):
            rgba = image.convert("RGBA")
            flat = Image.new("RGB", rgba.size, "white")
            flat.paste(rgba, mask=rgba.split()[3])
        else:
            flat = image.convert("RGB")
        height = round(flat.height * WIDTH / flat.width)
        flat.resize((WIDTH, height), Image.LANCZOS).save(
            path, "WEBP", quality=QUALITY, method=6
        )
    return path.stat().st_size


def photo_path(name_en: str) -> str:
    """free-exercise-db의 폴더명. 경로에 못 쓰는 글자를 뺀다."""
    out = name_en.replace(" ", "_").replace("/", "_")
    for ch in "'()":
        out = out.replace(ch, "")
    return out


def main() -> None:
    redo_all = "--all" in sys.argv[1:]
    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    ever = json.loads(fetch(EVER + "exercises.json").decode())
    by_title = {e["title"]: e for e in ever}
    candidates = [(tokens(e["title"]), e) for e in ever]

    drawn, shot, kept, missing, total = 0, 0, 0, [], 0
    seen: dict[str, str] = {}

    for row in rows:
        name_en = row["name_en"]
        key = slug(name_en)
        if key in seen:
            sys.exit(f"슬러그 충돌: {name_en!r} 와 {seen[key]!r} 가 모두 {key!r}")
        seen[key] = name_en

        # 이미 받은 그림은 그대로 둔다. 새로 받으면 원본이 바뀐 경우 기존 그림까지 조용히 달라진다
        if not redo_all and (OUT_DIR / f"{key}-1.webp").exists():
            kept += 1
            continue

        if name_en in SAME_AS:
            source = slug(SAME_AS[name_en])
            for index in (1, 2):
                original = OUT_DIR / f"{source}-{index}.webp"
                if original.exists():
                    copy = OUT_DIR / f"{key}-{index}.webp"
                    copy.write_bytes(original.read_bytes())
                    total += copy.stat().st_size
            drawn += 1
            print(f"  복사  {row['name_ko']}  <-  {SAME_AS[name_en]}", flush=True)
            continue

        # ── 1순위: everkinetic 선화
        best = by_title.get(MANUAL.get(name_en, ""))
        if best is None and name_en not in NO_DRAWING:
            ours = tokens(name_en)
            score = 0.0
            for theirs, exercise in candidates:
                if not ours or not theirs:
                    continue
                s = len(ours & theirs) / min(len(ours), len(theirs))
                # 한쪽 이름이 훨씬 길면 다른 운동일 확률이 높다
                s *= min(len(ours), len(theirs)) / max(len(ours), len(theirs)) ** 0.5
                if s > score:
                    best, score = exercise, s
            if score < THRESHOLD:
                best = None
        # 그림 파일이 빈 항목도 있다(everkinetic "Chin Ups"). 그러면 사진으로 간다
        if best is not None and not best["png"]:
            best = None

        if best is not None:
            for index, png in enumerate(best["png"][:2]):
                raw = fetch(EVER + png)
                if raw:
                    total += save(raw, OUT_DIR / f"{key}-{index + 1}.webp")
            drawn += 1
            print(f"  선화  {row['name_ko']}  <-  {best['title']}", flush=True)
            continue

        # ── 2순위: free-exercise-db 사진
        found = 0
        for index in (0, 1):
            raw = fetch(f"{PHOTO}/{photo_path(name_en)}/{index}.jpg")
            if raw:
                total += save(raw, OUT_DIR / f"{key}-{index + 1}.webp")
                found += 1
        if found:
            shot += 1
            print(f"  사진  {row['name_ko']}", flush=True)
        else:
            missing.append(row["name_ko"])

    print()
    print(f"종목 {len(rows)}개 — 있던 그림 {kept} / 선화 {drawn} / 사진 {shot} / 없음 {len(missing)}")
    print(f"합계 {total / 1024 / 1024:.1f} MB  ({OUT_DIR.relative_to(ROOT)})")
    if missing:
        print("\n그림이 없는 종목:")
        for name in missing:
            print("  " + name)


if __name__ == "__main__":
    main()
