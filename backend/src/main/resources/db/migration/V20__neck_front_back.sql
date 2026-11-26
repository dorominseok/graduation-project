-- 목을 회복 지도의 앞·뒤 그림에 따로 칠하려고 주동근 'neck'을 'front neck'·'back neck'으로 나눈다(2026-11-27).
-- 기본 종목 13종은 반복 시드(R__seed_exercises)가 CSV대로 바꾼다. 여기서는 사용자가 만든 목 종목만 옮긴다 —
-- 앞·뒤를 알 수 없어(동작도 '없음'으로 저장됐다) 목 종목이 더 많은 앞으로 둔다. 종목 고치기에서 뒤로 바꿀 수 있다.
UPDATE exercises SET primary_muscle = 'front neck' WHERE owner_id IS NOT NULL AND primary_muscle = 'neck';
