-- =====================================================
-- V1: 초기 스키마
-- 근거: 「운동기록 방식 설계서」 2장
-- =====================================================

-- 1. users (최소 형태, 인증 구현은 9월)
CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       nickname      VARCHAR(50)  NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. exercises (운동 종목)
CREATE TABLE exercises (
                           id             BIGSERIAL PRIMARY KEY,
                           name_ko        VARCHAR(100) NOT NULL,
                           name_en        VARCHAR(200),
                           body_part      VARCHAR(20)  NOT NULL,
                           primary_muscle VARCHAR(50),
                           push_pull      VARCHAR(10),
                           measure_type   VARCHAR(30)  NOT NULL,
                           equipment      VARCHAR(30),
                           created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                           CONSTRAINT ck_exercises_body_part
                               CHECK (body_part IN ('CHEST','BACK','LEGS','SHOULDERS','ARMS','CORE')),
                           CONSTRAINT ck_exercises_push_pull
                               CHECK (push_pull IN ('PUSH','PULL','NONE')),
                           CONSTRAINT ck_exercises_measure_type
                               CHECK (measure_type IN ('WEIGHT_REPS','BODYWEIGHT_REPS','WEIGHTED_BODYWEIGHT','TIME'))
);

-- 3. workout_sessions (운동 세션 = 하루 한 번의 운동)
CREATE TABLE workout_sessions (
                                  id                    BIGSERIAL PRIMARY KEY,
                                  user_id               BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                  performed_on          DATE        NOT NULL,
                                  routine_id            BIGINT,
                                  status                VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
                                  source                VARCHAR(10) NOT NULL DEFAULT 'LIVE',
                                  started_at            TIMESTAMPTZ,
                                  ended_at              TIMESTAMPTZ,
                                  duration_sec          INT,
                                  duration_override_sec INT,
                                  memo                  TEXT,
                                  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

                                  CONSTRAINT ck_sessions_status CHECK (status IN ('DRAFT','DONE')),
                                  CONSTRAINT ck_sessions_source CHECK (source IN ('LIVE','BACKFILL'))
);

-- 4. workout_sets (세트 기록 = 1세트가 1행)
CREATE TABLE workout_sets (
                              id          BIGSERIAL PRIMARY KEY,
                              session_id  BIGINT      NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
                              exercise_id BIGINT      NOT NULL REFERENCES exercises(id),
                              set_no      SMALLINT    NOT NULL,
                              weight_kg   NUMERIC(6,2),
                              reps        SMALLINT,
                              duration_sec INT,
                              is_warmup   BOOLEAN     NOT NULL DEFAULT false,
                              recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- 인덱스 (설계서 2.2)
-- =====================================================
CREATE INDEX idx_sessions_user_date ON workout_sessions (user_id, performed_on);
CREATE INDEX idx_sets_session       ON workout_sets (session_id);
CREATE INDEX idx_sets_exercise      ON workout_sets (exercise_id);