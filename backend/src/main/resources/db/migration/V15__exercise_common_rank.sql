-- 헬스장에서 흔히 하는 정도. 1 = 아주 흔함, 2 = 흔함, NULL = 그 밖.
--
-- 루틴 후보를 채울 때 입문 대표 종목(starter_rank) 다음에 이 순서로 먼저 넣는다. 종목이 261종으로 늘자
-- 시드 순서대로 채우는 자리에 케이블 데드리프트·아웃타이 머신이 레그 프레스보다 먼저 들어갔다.
-- 종목 교체 목록도 같은 기구·같은 빈도끼리는 이 순서로 보인다.
-- 값은 CSV(common_rank)에서 반복 마이그레이션이 채운다.
ALTER TABLE exercises ADD COLUMN common_rank SMALLINT;

ALTER TABLE exercises
    ADD CONSTRAINT ck_exercises_common_rank CHECK (common_rank IS NULL OR common_rank IN (1, 2));
