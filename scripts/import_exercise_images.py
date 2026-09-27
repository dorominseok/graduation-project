#!/usr/bin/env python3
"""직접 만든 종목 그림을 앱에 넣는다.

`scripts/incoming/`에 **한글 종목명**으로 저장하면 된다. 확장자는 png·jpg·webp
아무거나 되고, 크기도 맞출 필요 없다.

    scripts/incoming/바벨 데드리프트-1.png     ← 시작 자세
    scripts/incoming/바벨 데드리프트-2.png     ← 끝 자세

    python scripts/import_exercise_images.py

폭 400px webp로 줄여 `frontend/public/exercises/{영문명 슬러그}-{1,2}.webp`로
넣는다. 파일명 슬러그는 `fetch_exercise_images.py`·프론트 `exerciseImage.ts`와
같은 규칙이며, 셋 중 하나만 바뀌면 그림이 통째로 안 뜬다.

투명 배경은 흰색 위에 올린다 — 카드 배경이 흰색이라 그래야 이어져 보인다.
"""

import csv
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("pillow가 필요하다: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "backend/src/main/resources/data/exercises.csv"
IN_DIR = Path(__file__).resolve().parent / "incoming"
OUT_DIR = ROOT / "frontend/public/exercises"

WIDTH = 400
QUALITY = 80
SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"}


def slug(name_en: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name_en.lower()).strip("-")


def main() -> None:
    if not IN_DIR.exists():
        IN_DIR.mkdir(parents=True)
        print(f"{IN_DIR.relative_to(ROOT)} 를 만들었다. 여기에 그림을 넣고 다시 돌려라.")
        return

    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        by_ko = {row["name_ko"]: row["name_en"] for row in csv.DictReader(f)}

    done, problems = 0, []

    for path in sorted(IN_DIR.iterdir()):
        if path.suffix.lower() not in SUFFIXES:
            continue

        # "바벨 데드리프트-1" 에서 이름과 장수를 가른다
        match = re.fullmatch(r"(.+)-([12])", path.stem)
        if match is None:
            problems.append((path.name, "이름이 '종목명-1' 꼴이 아니다"))
            continue

        name_ko, shot = match.group(1).strip(), match.group(2)
        name_en = by_ko.get(name_ko)
        if name_en is None:
            problems.append((path.name, f"CSV에 없는 종목명: {name_ko}"))
            continue

        with Image.open(path) as image:
            # 투명 배경이면 흰 바탕에 얹는다
            if image.mode in ("RGBA", "LA", "P"):
                rgba = image.convert("RGBA")
                flat = Image.new("RGB", rgba.size, "white")
                flat.paste(rgba, mask=rgba.split()[3])
            else:
                flat = image.convert("RGB")

            height = round(flat.height * WIDTH / flat.width)
            out = OUT_DIR / f"{slug(name_en)}-{shot}.webp"
            flat.resize((WIDTH, height), Image.LANCZOS).save(
                out, "WEBP", quality=QUALITY, method=6
            )

        print(f"  {name_ko} ({shot}) -> {out.name}  {out.stat().st_size / 1024:.1f} KB")
        done += 1

    print(f"\n넣은 그림 {done}장")
    if problems:
        print(f"\n건너뛴 파일 {len(problems)}개:")
        for name, why in problems:
            print(f"  {name} — {why}")


if __name__ == "__main__":
    main()
