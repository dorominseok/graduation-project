"""
실험 케이스 10개. 값은 "부위별 주당 평균 세트 수"(4주→주당 환산 완료).
child key: CHEST BACK DELT_FRONT DELT_REAR TRICEPS BICEPS QUADS POSTERIOR CORE / CALVES FOREARMS
done: 최근 4주 DONE 세션 수 (신뢰도 판정용, 임계 6)
"""

CASES = [
    {
        "id": "01-all-optimal",
        "desc": "전 부위 최적, 균형 정상 — 해설이 '문제 없음' 톤을 내는가",
        "done": 16,
        "weekly": {
            "CHEST": 12, "BACK": 13, "DELT_FRONT": 11, "DELT_REAR": 10,
            "TRICEPS": 10, "BICEPS": 11, "QUADS": 15, "POSTERIOR": 15,
            "CORE": 11, "CALVES": 8, "FOREARMS": 4,
        },
    },
    {
        "id": "02-multi-insufficient",
        "desc": "어깨(뒤)·삼두 부족, 나머지 최적 — 하위 판정을 정확히 짚는가",
        "done": 12,
        "weekly": {
            "CHEST": 12, "BACK": 12, "DELT_FRONT": 10, "DELT_REAR": 1,
            "TRICEPS": 0, "BICEPS": 12, "QUADS": 11, "POSTERIOR": 11,
            "CORE": 10, "CALVES": 4, "FOREARMS": 2,
        },
    },
    {
        "id": "03-pull-side-zero",
        "desc": "당기기(등·어깨뒤·이두) 전부 0 → 비율 계산 불가 — smallerSideZero 처리를 옳게 말하는가",
        "done": 10,
        "weekly": {
            "CHEST": 14, "BACK": 0, "DELT_FRONT": 12, "DELT_REAR": 0,
            "TRICEPS": 10, "BICEPS": 0, "QUADS": 10, "POSTERIOR": 9,
            "CORE": 8, "CALVES": 3, "FOREARMS": 0,
        },
    },
    {
        "id": "04-low-confidence",
        "desc": "볼륨은 무난하나 최근 4주 세션 3회뿐 — 신뢰도 낮음을 언급하는가",
        "done": 3,
        "weekly": {
            "CHEST": 8, "BACK": 7, "DELT_FRONT": 7, "DELT_REAR": 6,
            "TRICEPS": 6, "BICEPS": 7, "QUADS": 10, "POSTERIOR": 10,
            "CORE": 6, "CALVES": 6, "FOREARMS": 3,
        },
    },
    {
        "id": "05-excessive",
        "desc": "어깨(앞) 주 26세트 과다 — '과다' 판정을 처방 없이 전달하는가",
        "done": 18,
        "weekly": {
            "CHEST": 14, "BACK": 13, "DELT_FRONT": 26, "DELT_REAR": 9,
            "TRICEPS": 12, "BICEPS": 11, "QUADS": 12, "POSTERIOR": 12,
            "CORE": 10, "CALVES": 6, "FOREARMS": 5,
        },
    },
    {
        "id": "06-mixed-badge",
        "desc": "팔: 삼두 0(부족) / 이두 24(과다) → summaryBadge MIXED — 두 방향을 동시에 말하는가",
        "done": 14,
        "weekly": {
            "CHEST": 12, "BACK": 12, "DELT_FRONT": 10, "DELT_REAR": 9,
            "TRICEPS": 0, "BICEPS": 24, "QUADS": 11, "POSTERIOR": 11,
            "CORE": 10, "CALVES": 5, "FOREARMS": 8,
        },
    },
    {
        "id": "07-upper-lower-imbalance",
        "desc": "상체 최적·하체 부족 → 상하체 비율 2배 초과 — 어느 비율인지 정확히 인용하는가",
        "done": 15,
        "weekly": {
            "CHEST": 14, "BACK": 14, "DELT_FRONT": 12, "DELT_REAR": 10,
            "TRICEPS": 12, "BICEPS": 12, "QUADS": 3, "POSTERIOR": 3,
            "CORE": 8, "CALVES": 2, "FOREARMS": 4,
        },
    },
    {
        "id": "08-all-insufficient",
        "desc": "거의 모든 부위 부족 + 신뢰도 낮음 — 나열이 장황해지지 않는가(길이 규칙)",
        "done": 5,
        "weekly": {
            "CHEST": 3, "BACK": 2, "DELT_FRONT": 3, "DELT_REAR": 1,
            "TRICEPS": 2, "BICEPS": 3, "QUADS": 3, "POSTERIOR": 2,
            "CORE": 1, "CALVES": 0, "FOREARMS": 0,
        },
    },
    {
        "id": "09-some-below-recommended",
        "desc": "균형 정상, 코어·앞허벅지만 '권장 이하' — '부족'과 '권장 이하'를 안 섞는가",
        "done": 11,
        "weekly": {
            "CHEST": 12, "BACK": 12, "DELT_FRONT": 10, "DELT_REAR": 10,
            "TRICEPS": 10, "BICEPS": 10, "QUADS": 8, "POSTERIOR": 14,
            "CORE": 5, "CALVES": 10, "FOREARMS": 3,
        },
    },
    {
        "id": "10-borderline-optimal",
        "desc": "전부 임계 근처(10.0·20.0 경계) — 경계값 판정을 서버와 다르게 말하지 않는가",
        "done": 13,
        "weekly": {
            "CHEST": 10, "BACK": 20, "DELT_FRONT": 10, "DELT_REAR": 10,
            "TRICEPS": 10, "BICEPS": 10, "QUADS": 10, "POSTERIOR": 10,
            "CORE": 10, "CALVES": 5, "FOREARMS": 4,
        },
    },
]
