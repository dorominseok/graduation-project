-- 친구 그룹 (LOG-38)
-- 테이블 이름을 groups가 아니라 friend_groups로 둔다. GROUP은 SQL·JPQL 예약어이고,
-- 종목의 "계열"(exercises.group_name, V6)과도 헷갈린다.

CREATE TABLE friend_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(20) NOT NULL,
    -- 헷갈리는 글자(0·O, 1·I·L)를 뺀 대문자·숫자 6자리. 방장이 다시 만들면 바뀐다
    invite_code VARCHAR(6)  NOT NULL UNIQUE,
    -- 방장. 처음엔 만든 사람이고, 방장이 나가면 가장 먼저 들어온 멤버에게 넘어간다.
    -- 회원 탈퇴 때도 서비스가 먼저 넘기거나 그룹을 지운다. 연쇄 삭제를 걸면
    -- 방장 한 명의 탈퇴로 남은 멤버들의 그룹이 사라지므로 걸지 않는다
    owner_id    BIGINT      NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_friend_groups_owner ON friend_groups (owner_id);

CREATE TABLE friend_group_members (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT      NOT NULL REFERENCES friend_groups(id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 방장을 넘길 순서다. 가장 먼저 들어온 사람이 받는다
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friend_group_members UNIQUE (group_id, user_id)
);

-- "내 그룹 목록"은 user_id로 찾는다. 위 유니크 인덱스는 group_id가 앞이라 쓰지 못한다
CREATE INDEX idx_friend_group_members_user ON friend_group_members (user_id);

-- 응원. 운동 한 번(세션)에 한 사람이 한 번. 그룹이 아니라 세션에 붙는다.
-- 같은 사람과 그룹 두 개를 함께해도 그 사람의 운동은 하나다
CREATE TABLE cheers (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cheers UNIQUE (session_id, user_id)
);

-- 회원 탈퇴 때 그 사람이 보낸 응원을 연쇄 삭제한다. 위 유니크 인덱스는 session_id가 앞이라 쓰지 못한다
CREATE INDEX idx_cheers_user ON cheers (user_id);
