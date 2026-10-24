-- 입문 대표 종목(LOG-40). 기록·즐겨찾기가 없는 사람에게 루틴 후보를 줄 때 이 순서로 먼저 넣는다.
--
-- 없으면 새 종목이 시드 순서(영문 이름 순)로 골라져, 처음 운동하는 사람의 첫 루틴이
-- 디클라인 벤치프레스·친업으로 나왔다. 부위마다 1~2개만 둔다 — 1이 먼저다.
-- 값은 CSV(starter_rank)에서 반복 마이그레이션이 채운다.
ALTER TABLE exercises ADD COLUMN starter_rank SMALLINT;

ALTER TABLE exercises
    ADD CONSTRAINT ck_exercises_starter_rank CHECK (starter_rank IS NULL OR starter_rank > 0);
