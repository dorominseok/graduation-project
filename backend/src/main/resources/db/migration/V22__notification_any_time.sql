-- 알림 시각을 하루 내내(00:00~23:30) 고르게 한다. #77(2026-11-28)에서 서버 검사와 화면만 풀고 이 제약을 빠뜨려,
-- 원래 안 되던 시각(예: 03:00)을 저장하면 DB가 막아 "일시적인 오류"가 났다. 30분 단위 규칙은 그대로 둔다.
ALTER TABLE notification_settings DROP CONSTRAINT ck_notification_settings_times;
ALTER TABLE notification_settings ADD CONSTRAINT ck_notification_settings_times CHECK (
    EXTRACT(MINUTE FROM reminder_time) IN (0, 30) AND EXTRACT(SECOND FROM reminder_time) = 0
    AND EXTRACT(MINUTE FROM summary_time) IN (0, 30) AND EXTRACT(SECOND FROM summary_time) = 0);
