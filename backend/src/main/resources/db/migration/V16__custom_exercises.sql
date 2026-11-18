-- 내 종목: 사용자가 직접 만드는 종목(「내종목_설계서」 4장).
--
-- 기본 종목과 같은 테이블에 주인을 두고 넣는다. 세트·분석·1RM·기록 조회가 모두 종목 id로
-- 이어져 있어, 같은 테이블이면 그 경로를 손대지 않아도 된다.

-- 주인. 기본 종목은 NULL. 탈퇴하면 그 사람의 종목도 지운다(세트도 같은 사람의 기록이라 함께 지워진다)
ALTER TABLE exercises ADD COLUMN owner_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

-- 기록이 있는 내 종목을 지우면 숨긴다 — 지난 기록의 종목 이름이 사라지면 안 된다
ALTER TABLE exercises ADD COLUMN hidden_at TIMESTAMPTZ;

ALTER TABLE exercises ADD CONSTRAINT ck_exercises_hidden_custom_only
    CHECK (hidden_at IS NULL OR owner_id IS NOT NULL);

-- 이름은 기본 종목끼리, 그리고 한 사람의 종목끼리만 겹치면 안 된다.
-- 시드의 UPSERT는 이제 기본 종목 쪽 부분 인덱스를 고른다(ON CONFLICT (name_ko) WHERE owner_id IS NULL)
ALTER TABLE exercises DROP CONSTRAINT uq_exercises_name_ko;
CREATE UNIQUE INDEX uq_exercises_builtin_name ON exercises (name_ko) WHERE owner_id IS NULL;
CREATE UNIQUE INDEX uq_exercises_owner_name ON exercises (owner_id, name_ko) WHERE owner_id IS NOT NULL;
