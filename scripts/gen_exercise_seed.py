#!/usr/bin/env python3
"""운동 종목 CSV → Flyway 반복 마이그레이션(R__seed_exercises.sql) 생성기.

종목 데이터의 원본은 `exercises.csv`이고, 실제 적재는 Flyway가 한다.
CSV를 고친 뒤 이 스크립트를 다시 돌려 SQL을 갱신하면, 체크섬이 바뀌므로
Flyway가 다음 기동에서 반복 마이그레이션을 재실행해 UPSERT한다.

    python scripts/gen_exercise_seed.py

주의: CSV에서 행을 "삭제"해도 DB 행은 지워지지 않는다(UPSERT이므로).
      종목 폐기는 workout_sets FK 영향을 검토해 별도 버전 마이그레이션으로 처리한다.
"""

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "backend/src/main/resources/data/exercises.csv"
SQL_PATH = ROOT / "backend/src/main/resources/db/migration/R__seed_exercises.sql"

COLUMNS = [
    "name_ko", "name_en", "body_part", "primary_muscle",
    "push_pull", "measure_type", "equipment", "delt_region",
]
# UPSERT 시 갱신 대상 (자연키인 name_ko 제외)
UPDATED = COLUMNS[1:]


def literal(value: str) -> str:
    """CSV 값을 SQL 리터럴로. 빈 문자열은 NULL, 작은따옴표는 이스케이프."""
    if value == "":
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    missing = [c for c in COLUMNS if c not in rows[0]]
    if missing:
        raise SystemExit(f"CSV에 없는 컬럼: {missing}")

    values = ",\n".join(
        "    (" + ", ".join(literal(r[c]) for c in COLUMNS) + ")" for r in rows
    )
    updates = ",\n".join(f"    {c:<14} = EXCLUDED.{c}" for c in UPDATED)

    sql = f"""-- =====================================================
-- R__seed_exercises: 운동 종목 시드 (반복 마이그레이션)
--
-- 자동 생성 파일 — 직접 수정하지 말 것.
--   원본 : backend/src/main/resources/data/exercises.csv ({len(rows)}종목)
--   생성 : python scripts/gen_exercise_seed.py
--
-- 자연키 name_ko 기준 UPSERT. CSV를 고치고 재생성하면 체크섬이 바뀌어
-- Flyway가 자동으로 재실행한다.
-- delt_region 배분 근거: 「설계 변경 로그」 LOG-09
-- =====================================================

INSERT INTO exercises ({", ".join(COLUMNS)})
VALUES
{values}
ON CONFLICT (name_ko) DO UPDATE SET
{updates};
"""

    SQL_PATH.write_text(sql, encoding="utf-8", newline="\n")
    print(f"generated: {SQL_PATH.relative_to(ROOT)} ({len(rows)} rows)")


if __name__ == "__main__":
    main()
