-- =====================================================
-- V3: 인증 · 프로필 · 종목 메타
-- 근거: 「API 명세서」 2.4 (필요한 스키마 추가), 9.1
-- =====================================================

-- -----------------------------------------------------
-- (1) refresh_tokens — 신규 테이블
--     근거: API 명세서 2.3(리프레시 토큰 회전), 2.4-(1)
--     원문 대신 SHA-256 해시(64 hex)를 저장한다.
-- -----------------------------------------------------
CREATE TABLE refresh_tokens (
                                id         BIGSERIAL PRIMARY KEY,
                                user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash VARCHAR(64) NOT NULL UNIQUE,
                                expires_at TIMESTAMPTZ NOT NULL,
                                revoked_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 재사용 감지 시 해당 사용자의 토큰 전량 폐기 (2.3)
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- -----------------------------------------------------
-- (2) users 프로필 컬럼 — 기존 테이블 확장
--     근거: API 명세서 2.4-(2), 4.6
--     프로필은 훈련 목표 1종이다. 운동 경력(experience_level)에 이어
--     키(height_cm)·체중(weight_kg)도 제외했다 — 어떤 로직도 참조하지
--     않고, 프로필 컬럼은 나중에 추가해도 비용이 같기 때문이다.
--     회원가입 후 선택 입력이므로 NULL 허용.
-- -----------------------------------------------------
ALTER TABLE users
    ADD COLUMN goal VARCHAR(20);

-- 훈련 목표 축 (체중 목표 아님) — 부록 A `user.goal`
-- 10월 루틴 추천에서 반복 횟수·강도 범위를 정하는 축으로 쓴다.
ALTER TABLE users ADD CONSTRAINT ck_users_goal
    CHECK (goal IN ('STRENGTH','HYPERTROPHY','ENDURANCE','GENERAL_FITNESS'));

-- -----------------------------------------------------
-- (3) exercises.delt_region — 기존 테이블 확장
--     근거: API 명세서 2.4-(3), 8.1 / LOG-09
--     primary_muscle = 'shoulders'인 종목에만 값을 갖는다.
--     값이 비면 전량 DELT_FRONT로 집계되어 전 사용자에게
--     "어깨(뒤) 부족" 오판이 상시 발생하므로(2.4-(3)),
--     shoulders ↔ delt_region을 양방향으로 강제한다.
-- -----------------------------------------------------
ALTER TABLE exercises ADD COLUMN delt_region VARCHAR(10);

-- 이미 종목 행이 적재된 DB(개발용 로컬 DB)를 위한 백필.
-- 배분 규칙은 LOG-09 「어깨 전면·후면 분리 방식」 그대로다 —
-- 후면 델트 3종만 REAR, 나머지 shoulders(오버헤드 프레스·전면 레이즈
-- ·측면 레이즈·업라이트 로우)는 FRONT. 신규 DB에서는 대상 행이 없어
-- 0건 갱신이며, 이후 값 유지는 R__seed_exercises.sql이 맡는다.
UPDATE exercises
   SET delt_region = CASE
           WHEN name_ko IN ('바벨 리어델트 로우', '페이스풀', '덤벨 리어델트 플라이')
               THEN 'REAR'
           ELSE 'FRONT'
       END
 WHERE primary_muscle = 'shoulders';

ALTER TABLE exercises ADD CONSTRAINT ck_exercises_delt_region
    CHECK (delt_region IN ('FRONT','REAR'));

ALTER TABLE exercises ADD CONSTRAINT ck_exercises_delt_region_scope
    CHECK (
        CASE WHEN primary_muscle = 'shoulders'
             THEN delt_region IS NOT NULL
             ELSE delt_region IS NULL
        END
    );

-- 종목 시드(R__seed_exercises.sql)의 UPSERT 대상 키.
-- 한글 종목명은 정제 단계에서 이미 유일하며, 종목 식별의 자연키로 쓴다.
ALTER TABLE exercises ADD CONSTRAINT uq_exercises_name_ko UNIQUE (name_ko);

-- -----------------------------------------------------
-- (4) user_favorite_exercises — 신규 테이블
--     근거: API 명세서 2.4-(4), 5.4 / 기록 방식 3.4
-- -----------------------------------------------------
CREATE TABLE user_favorite_exercises (
                                         id          BIGSERIAL PRIMARY KEY,
                                         user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                         exercise_id BIGINT      NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
                                         created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

                                         CONSTRAINT uq_user_favorite_exercises UNIQUE (user_id, exercise_id)
);

-- 즐겨찾기 탭 조회: 별표 누른 순 정렬 (5.2 `favorite=true&sort=createdAt,desc`)
CREATE INDEX idx_user_favorite_exercises_user
    ON user_favorite_exercises (user_id, created_at DESC);
