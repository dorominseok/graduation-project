-- 하루 기록 (LOG-39)
-- 운동 한 번(세션)이던 workout_sessions를 "사용자의 하루"로 바꾼다. 하루에 하나, 시작·종료가 없다.
-- 테이블 이름은 그대로 둔다 — 세트(workout_sets)와 응원(cheers)이 session_id로 가리키고 있어,
-- 뜻만 바꾸면 외래키가 그대로 산다.

-- 1. 같은 날 기록을 가장 앞(id가 가장 작은) 기록 하나로 합친다.
--    from_id → keep_id 대응표. 하루에 기록이 하나뿐인 날은 들어가지 않는다
CREATE TEMPORARY TABLE day_merge ON COMMIT DROP AS
SELECT s.id AS from_id, k.keep_id
  FROM workout_sessions s
  JOIN (SELECT user_id, performed_on, MIN(id) AS keep_id
          FROM workout_sessions
         GROUP BY user_id, performed_on
        HAVING COUNT(*) > 1) k
    ON k.user_id = s.user_id AND k.performed_on = s.performed_on
 WHERE s.id <> k.keep_id;

-- 메모는 줄을 바꿔 잇고, 루틴 연결은 있는 쪽을 쓴다.
-- 시간 보정값은 하나라도 있으면 합친 기록들의 시간을 더한 값으로 둔다. 보정값이 없는 기록은
-- 그때까지 산출된 시간을 더한다 — 사용자가 고친 시간을 버리지 않으면서, 나머지 기록의 시간도 빠뜨리지 않는다
UPDATE workout_sessions w
   SET memo = m.memo,
       routine_id = COALESCE(w.routine_id, m.routine_id),
       duration_override_sec = CASE WHEN m.has_override
                                    THEN m.total_sec
                                    ELSE w.duration_override_sec END
  FROM (SELECT g.keep_id,
               string_agg(s.memo, E'\n' ORDER BY s.id) AS memo,
               MIN(s.routine_id) AS routine_id,
               bool_or(s.duration_override_sec IS NOT NULL) AS has_override,
               SUM(COALESCE(s.duration_override_sec, s.duration_sec, 0)) AS total_sec
          FROM workout_sessions s
          JOIN (SELECT keep_id, keep_id AS id FROM day_merge
                UNION
                SELECT keep_id, from_id FROM day_merge) g
            ON g.id = s.id
         GROUP BY g.keep_id) m
 WHERE w.id = m.keep_id;

-- 세트를 옮기고, 종목별 순번을 저장 시각 순으로 다시 매긴다. 옮기기만 하면 두 기록의 "1세트"가 둘이 된다
UPDATE workout_sets ws
   SET session_id = d.keep_id
  FROM day_merge d
 WHERE ws.session_id = d.from_id;

UPDATE workout_sets ws
   SET set_no = r.n
  FROM (SELECT id,
               ROW_NUMBER() OVER (PARTITION BY session_id, exercise_id ORDER BY recorded_at, id) AS n
          FROM workout_sets
         WHERE session_id IN (SELECT keep_id FROM day_merge)) r
 WHERE ws.id = r.id;

-- 응원을 옮긴다. 같은 사람이 같은 날 두 기록에 모두 눌렀으면 하나만 남는다
INSERT INTO cheers (session_id, user_id, created_at)
SELECT d.keep_id, c.user_id, MIN(c.created_at)
  FROM cheers c
  JOIN day_merge d ON d.from_id = c.session_id
 GROUP BY d.keep_id, c.user_id
ON CONFLICT (session_id, user_id) DO NOTHING;

-- 옮기고 남은 기록을 지운다. 옛 응원 행은 연쇄 삭제된다
DELETE FROM workout_sessions
 WHERE id IN (SELECT from_id FROM day_merge);

-- 2. 상태와 저장해 두던 시각·시간을 지운다.
--    진행 중/완료(status)가 없어지고, 사후 입력 표식(source)은 운동 시간의 구간 규칙이 대신한다.
--    시작·끝 시각과 운동 시간은 세트 저장 시각으로 읽을 때 계산한다 — 세트를 고칠 때마다
--    저장값을 맞출 필요가 없고, 규칙이 바뀌어도 다시 계산하는 마이그레이션이 필요 없다
ALTER TABLE workout_sessions
    DROP COLUMN status,
    DROP COLUMN source,
    DROP COLUMN started_at,
    DROP COLUMN ended_at,
    DROP COLUMN duration_sec;

-- 3. 하루에 하나. 이 유니크 인덱스가 (user_id, performed_on) 조회도 맡으므로 V1의 인덱스는 지운다
ALTER TABLE workout_sessions
    ADD CONSTRAINT uq_workout_sessions_user_day UNIQUE (user_id, performed_on);

DROP INDEX idx_sessions_user_date;
