-- 하루 기록 안의 회차(결정 기록 D-22, 2026-11-28 사용자 요청 — 오전·오후처럼 하루에 두 번 운동).
--
-- 기록은 날짜마다 하나 그대로 두고(LOG-39), 그 안을 회차로 나눈다. 세트에 회차 번호를 단다 — 기존 세트는 모두 1회차.
-- 같은 종목을 1회차와 2회차에 하면 카드가 둘이고 세트도 따로 쌓인다. 분석·회복·루틴은 회차와 상관없이 그날 세트를 센다.
ALTER TABLE workout_sets ADD COLUMN round_no SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_round_no CHECK (round_no BETWEEN 1 AND 20);

-- 카드 순서(V18의 종목 순서)는 이제 (종목, 회차) 단위다. "종목id:회차" 글자 배열로 둔다 — 기존 순서는 모두 1회차.
ALTER TABLE workout_sessions ADD COLUMN card_order TEXT[];
UPDATE workout_sessions s
   SET card_order = (SELECT array_agg(t.id::text || ':1' ORDER BY t.ord)
                       FROM unnest(s.exercise_order) WITH ORDINALITY AS t(id, ord))
 WHERE s.exercise_order IS NOT NULL;
ALTER TABLE workout_sessions DROP COLUMN exercise_order;
