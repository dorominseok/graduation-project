-- 알림(「알림_설계서」) — 브라우저 푸시 구독과 사용자별 알림 설정.
-- 회원 탈퇴는 users 행을 지우므로 두 테이블 모두 연쇄 삭제한다.

-- 브라우저(기기)마다 하나. 한 사람이 휴대폰·노트북을 같이 쓸 수 있다.
-- 같은 브라우저로 다른 계정에 로그인하면 푸시 주소는 같고 주인만 바뀐다 — 그래서 주소가 유일하다
CREATE TABLE push_subscriptions (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    VARCHAR(1000) NOT NULL,
    p256dh      VARCHAR(100)  NOT NULL,
    auth        VARCHAR(50)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);

-- 사용자당 한 줄. 알림을 처음 켤 때(구독할 때) 기본값으로 만든다
CREATE TABLE notification_settings (
    user_id           BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    reminder_enabled  BOOLEAN     NOT NULL,
    reminder_days     SMALLINT    NOT NULL,   -- 월 1 · 화 2 · 수 4 · 목 8 · 금 16 · 토 32 · 일 64
    reminder_time     TIME        NOT NULL,
    summary_enabled   BOOLEAN     NOT NULL,
    summary_day       SMALLINT    NOT NULL,   -- ISO 요일: 1(월) ~ 7(일)
    summary_time      TIME        NOT NULL,
    cheer_enabled     BOOLEAN     NOT NULL,
    group_enabled     BOOLEAN     NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_settings_days CHECK (reminder_days BETWEEN 0 AND 127 AND summary_day BETWEEN 1 AND 7),
    -- 고를 수 있는 시각은 08:00~21:30, 30분 단위 — 조용한 시간(22:00~08:00)과 부딪치지 않게
    CONSTRAINT ck_notification_settings_times CHECK (
        reminder_time BETWEEN TIME '08:00' AND TIME '21:30'
        AND summary_time BETWEEN TIME '08:00' AND TIME '21:30'
        AND EXTRACT(MINUTE FROM reminder_time) IN (0, 30) AND EXTRACT(SECOND FROM reminder_time) = 0
        AND EXTRACT(MINUTE FROM summary_time) IN (0, 30) AND EXTRACT(SECOND FROM summary_time) = 0)
);
