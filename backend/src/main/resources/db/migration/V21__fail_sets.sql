-- 실패 세트(2026-11-28 사용자 요청, 결정 기록 D-15). 목표 횟수를 두고, 해낸 횟수는 그대로 reps에 둔다(10회 목표에 9회 → reps 9, target_reps 10).
-- 분석은 해낸 횟수의 본세트로 센다 — 워밍업이 아니므로 기존 집계 조건(is_warmup = false)에 그대로 들어간다.
-- 워밍업은 실패 세트가 될 수 없고, 목표는 해낸 횟수보다 커야 한다(같거나 적으면 실패가 아니다). 횟수가 없는 시간 종목에는 없다.
ALTER TABLE workout_sets ADD COLUMN target_reps SMALLINT;
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_target_reps
    CHECK (target_reps IS NULL OR (NOT is_warmup AND reps IS NOT NULL AND target_reps > reps));
