-- 오늘의 루틴 추천 (LOG-40, 「루틴생성_로직_설계서」 10장)
-- 루틴은 계획이고 workout_sets는 기록이다. 둘을 다른 테이블에 두어 계획이 분석에 섞이지 않게 한다(LOG-05).

CREATE TABLE routines (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    routine_date         DATE        NOT NULL,
    -- 분석 기준일. 어제다 — 오늘 체크한 세트가 오늘 루틴의 근거를 흔들지 않게(설계서 1.1)
    reference_date       DATE        NOT NULL,
    -- 종목을 누가 골랐나. LLM이 꺼져 있거나 실패하면 규칙이 고른다
    generated_by         VARCHAR(10) NOT NULL,
    llm_attempts         SMALLINT    NOT NULL DEFAULT 0,
    fallback_reason      VARCHAR(30),
    comment              TEXT,
    comment_by           VARCHAR(10) NOT NULL,
    -- 최종 루틴 검증(R1~R5). 실패해도 루틴은 주고, 사유를 남겨 통과율을 센다(설계서 6.2)
    final_check_passed   BOOLEAN     NOT NULL,
    final_check_failures VARCHAR(100),
    -- 만족도. 루틴과 1:1이고 한 번만 받으므로 테이블을 나누지 않는다(설계서 10.1)
    feedback_rating      VARCHAR(10),
    feedback_reason      VARCHAR(20),
    feedback_skipped     BOOLEAN     NOT NULL DEFAULT false,
    feedback_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 하루에 하나. 다시 만들기는 없고 후보정으로 고친다(LOG-40)
    CONSTRAINT uq_routines_user_day UNIQUE (user_id, routine_date),
    CONSTRAINT ck_routines_generated_by CHECK (generated_by IN ('LLM', 'RULE')),
    CONSTRAINT ck_routines_comment_by CHECK (comment_by IN ('LLM', 'RULE')),
    CONSTRAINT ck_routines_fallback_reason
        CHECK (fallback_reason IN ('LLM_DISABLED', 'LLM_ERROR', 'VALIDATION_FAILED')),
    CONSTRAINT ck_routines_llm_attempts CHECK (llm_attempts BETWEEN 0 AND 3),
    CONSTRAINT ck_routines_feedback_rating CHECK (feedback_rating IN ('GOOD', 'OKAY', 'BAD')),
    CONSTRAINT ck_routines_feedback_reason
        CHECK (feedback_reason IN ('EXERCISE', 'TOO_MUCH', 'TOO_LITTLE', 'OTHER'))
);

CREATE TABLE routine_items (
    id                   BIGSERIAL PRIMARY KEY,
    routine_id           BIGINT       NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    position             SMALLINT     NOT NULL,
    -- 판정 부위(슬롯). 수행률은 종목이 아니라 부위로 센다(설계서 11.2)
    muscle_group         VARCHAR(20)  NOT NULL,
    exercise_id          BIGINT       NOT NULL REFERENCES exercises(id),
    planned_sets         SMALLINT     NOT NULL,
    -- 반복 범위. 시간 종목은 초다(설계서 9.1)
    target_min           SMALLINT     NOT NULL,
    target_max           SMALLINT     NOT NULL,
    first_time           BOOLEAN      NOT NULL,
    -- 근거는 만든 날의 값으로 남긴다. 다시 계산하면 오늘 기록이 섞여 달라진다(설계서 10.2)
    basis_weekly_sets    NUMERIC(4,1) NOT NULL,
    basis_verdict        VARCHAR(20)  NOT NULL,
    -- 후보정 전 원래 값. 수행률의 분모가 원래 계획이고, 후보정 비율도 여기서 센다(설계서 11.2·11.3)
    original_exercise_id BIGINT       REFERENCES exercises(id),
    original_sets        SMALLINT,
    removed              BOOLEAN      NOT NULL DEFAULT false,

    CONSTRAINT ck_routine_items_planned_sets CHECK (planned_sets BETWEEN 1 AND 10),
    CONSTRAINT ck_routine_items_target CHECK (target_min BETWEEN 1 AND target_max)
);

CREATE INDEX idx_routine_items_routine ON routine_items (routine_id);

-- 하루 기록과 루틴을 잇는다. V1부터 컬럼만 있던 자리에 이제 FK를 건다(명세 6.2)
ALTER TABLE workout_sessions
    ADD CONSTRAINT fk_workout_sessions_routine
        FOREIGN KEY (routine_id) REFERENCES routines(id) ON DELETE SET NULL;
