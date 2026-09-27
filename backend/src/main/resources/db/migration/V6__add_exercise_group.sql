-- 종목 계열. 같은 동작의 변형들을 한 묶음으로 본다(LOG-24).
--
-- 종목 선택 화면이 「부위 → 계열 → 변형」 3단으로 바뀌면서 필요해졌다.
-- 109종을 한 목록에 늘어놓으면 검색 말고는 원하는 종목을 찾을 길이 없다.
--
-- primary_muscle과 다른 축이다 — 저쪽은 "어느 근육을 쓰는가"(판정용)이고
-- 이쪽은 "어떤 동작인가"(탐색용)다. 벤치프레스와 체스트프레스는 같은 근육을
-- 쓰지만 사용자가 고를 때는 다른 동작으로 구분한다.
ALTER TABLE exercises ADD COLUMN group_name VARCHAR(40);

CREATE INDEX idx_exercises_group_name ON exercises (group_name);
