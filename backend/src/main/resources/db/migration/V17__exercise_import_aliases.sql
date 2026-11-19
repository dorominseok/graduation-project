-- 기록 가져오기: 맞춘 이름 기억(「기록가져오기_설계서」 결정 9·6장).
--
-- 미리보기에서 "바꾸기"로 고르거나 미등록 운동을 만들어 넣으면, 원래 이름과 고른 종목의 짝을 사람마다 남긴다.
-- 다음에 같은 이름을 붙여넣으면 이 짝이 먼저 맞는다.
-- 탈퇴하면 함께 지운다. 짝의 종목(내 종목)을 지워도 함께 지운다.
CREATE TABLE exercise_import_aliases (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_name VARCHAR(200) NOT NULL,   -- 바디캘린더의 원래 이름 전체(띄어쓰기 정리) — 풀업: 중량
    exercise_id BIGINT       NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_import_alias UNIQUE (user_id, source_name)
);

-- 내 종목을 지울 때 연쇄 삭제가 이 표를 훑지 않게
CREATE INDEX ix_import_alias_exercise ON exercise_import_aliases (exercise_id);
