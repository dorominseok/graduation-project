-- 종목 이름 정렬을 가나다순으로 고정한다.
-- DB 기본 콜레이션(en_US.utf8)은 한글 가중치가 없어 ORDER BY name_ko가
-- 글자 수 순으로 나온다. 한글 음절은 유니코드 배치가 가나다순이라 "C"(코드포인트)면 맞는다.
ALTER TABLE exercises ALTER COLUMN name_ko TYPE VARCHAR(100) COLLATE "C";
