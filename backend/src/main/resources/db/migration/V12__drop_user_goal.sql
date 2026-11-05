-- 훈련 목표(goal)를 지운다.
--
-- 프로필 화면은 닉네임만 바꾸고, 루틴의 반복 범위도 목표가 아니라 측정 유형으로 정해(2026-09-20)
-- 이 컬럼을 읽는 곳이 없다. 쓰지 않는 칸이 API와 스키마에 남아 있으면 기능이 있는 것처럼 보인다.
ALTER TABLE users DROP CONSTRAINT ck_users_goal;

ALTER TABLE users DROP COLUMN goal;
