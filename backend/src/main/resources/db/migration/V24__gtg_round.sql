-- GTG(Grease the Groove) — 하루 중 틈틈이 하는 가벼운 세트(결정 기록 D-23, 2026-11-28 친구 피드백).
--
-- 회차의 한 종류로 둔다. 회차 번호 0이 그날의 GTG다 — 하루 하나, 탭은 일반 회차 뒤에 온다.
-- 운동 시간·부위별 볼륨·회복·루틴·1RM 추이·직전 기록·운동한 날 수에 넣지 않는다(본 운동이 아니라서).
ALTER TABLE workout_sets DROP CONSTRAINT ck_workout_sets_round_no;
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_round_no CHECK (round_no BETWEEN 0 AND 20);
