-- 운동 부위 "목"(2026-11-20 사용자 요청). 넥 컬·넥 익스텐션·넥 브릿지 등 13종(시드 CSV).
-- 표시 부위(body_part)에 NECK을 더한다 — 목은 가슴·등·하체·어깨·팔·코어 어디에도 맞지 않는다.
-- 분석에서는 전완·종아리처럼 세트 수만 보이고 약점 판정·루틴·균형 판정에는 들어가지 않는다(primary_muscle = 'neck').
ALTER TABLE exercises DROP CONSTRAINT ck_exercises_body_part;
ALTER TABLE exercises ADD CONSTRAINT ck_exercises_body_part
    CHECK (body_part IN ('CHEST','BACK','LEGS','SHOULDERS','ARMS','CORE','NECK'));
