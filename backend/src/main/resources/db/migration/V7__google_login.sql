-- 구글 로그인 (LOG-37)
-- 구글로 가입한 사람은 비밀번호가 없다. 대신 구글 회원번호(sub)로 찾는다 —
-- 구글 계정의 이메일은 바뀔 수 있지만 이 번호는 바뀌지 않는다.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_id VARCHAR(255);

-- NULL은 여럿이어도 된다(이메일 가입자). 값이 있으면 한 계정에만 묶인다
ALTER TABLE users ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);

-- 로그인할 방법이 하나도 없는 계정은 만들 수 없다
ALTER TABLE users ADD CONSTRAINT ck_users_login_method
    CHECK (password_hash IS NOT NULL OR google_id IS NOT NULL);
