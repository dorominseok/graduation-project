"""
분석 응답(8.2 muscle-volume / 8.3 balance) 참조 구현.

API_명세서.md §8.1·§8.2·§8.3·§8.5의 판정 로직을 그대로 옮긴 것.
LLM 실험의 입력 JSON을 만드는 용도이며, 서버 구현의 초안이기도 하다.
입력은 "부위별 주당 평균 세트 수"(이미 4주→주당 환산된 값)로 단순화한다.
"""

PERIOD_WEEKS = 4
BALANCE_THRESHOLD = 2.0
CONFIDENCE_THRESHOLD = 6  # 최근 4주 DONE 세션 수 임계 (기록 방식 5.5)

# 판정 부위 하위 9종 (label)
CHILD_LABEL = {
    "CHEST": "가슴",
    "BACK": "등",
    "DELT_FRONT": "어깨(앞)",
    "DELT_REAR": "어깨(뒤)",
    "TRICEPS": "삼두",
    "BICEPS": "이두",
    "QUADS": "앞허벅지",
    "POSTERIOR": "뒤허벅지·둔근",
    "CORE": "코어",
}

# 상위 6종 (body_part) ↔ 하위
PARENTS = [
    ("CHEST", "가슴", ["CHEST"]),
    ("BACK", "등", ["BACK"]),
    ("SHOULDERS", "어깨", ["DELT_FRONT", "DELT_REAR"]),
    ("ARMS", "팔", ["TRICEPS", "BICEPS"]),
    ("LEGS", "하체", ["QUADS", "POSTERIOR"]),
    ("CORE", "코어", ["CORE"]),
]

DISPLAY_ONLY = [("CALVES", "종아리"), ("FOREARMS", "전완")]

def _round1(x):
    return round(x + 1e-9, 1)


def verdict_of(weekly):
    """API_명세서 §8.2: <4 부족 / 4~10 권장 이하 / 10~20 최적 / >20 과다 (상한 포함)."""
    if weekly < 4:
        return "INSUFFICIENT", "부족"
    if weekly < 10:
        return "BELOW_RECOMMENDED", "권장 이하"
    if weekly <= 20:
        return "OPTIMAL", "최적"
    return "EXCESSIVE", "과다"


def _badge(child_verdicts):
    """2개 하위의 verdict key 리스트 -> (badge, badgeLabel). 우선순위: INSUFF > EXCESS(+INSUFF=MIXED) > BELOW > ALL_OPTIMAL"""
    s = set(child_verdicts)
    if "INSUFFICIENT" in s and "EXCESSIVE" in s:
        return "MIXED", "확인 필요"
    if "INSUFFICIENT" in s:
        return "PARTIAL_INSUFFICIENT", "일부 부족"
    if "EXCESSIVE" in s:
        return "PARTIAL_EXCESSIVE", "일부 과다"
    if "BELOW_RECOMMENDED" in s:
        return "PARTIAL_BELOW", "일부 권장 이하"
    return "ALL_OPTIMAL", "모두 최적"


def build_muscle_volume(weekly, done_sessions, reference_date="2026-09-01"):
    """weekly: {child_key: 주당평균세트}. CALVES/FOREARMS 포함 가능."""
    tiers = []
    for pkey, plabel, ckeys in PARENTS:
        children = []
        for ck in ckeys:
            w = _round1(weekly.get(ck, 0.0))
            vk, vl = verdict_of(w)
            children.append({
                "key": ck, "label": CHILD_LABEL[ck],
                "weeklySets": w, "totalSets": round(w * PERIOD_WEEKS),
                "verdict": vk, "verdictLabel": vl,
            })
        has_children = len(children) > 1
        total_w = _round1(sum(c["weeklySets"] for c in children))
        tier = {
            "key": pkey, "label": plabel,
            "weeklySets": total_w,
            "totalSets": sum(c["totalSets"] for c in children),
            "hasChildren": has_children,
            "verdict": None, "verdictLabel": None,
            "summaryBadge": None, "summaryBadgeLabel": None,
            "children": children,
        }
        if has_children:
            b, bl = _badge([c["verdict"] for c in children])
            tier["summaryBadge"], tier["summaryBadgeLabel"] = b, bl
        else:
            tier["verdict"] = children[0]["verdict"]
            tier["verdictLabel"] = children[0]["verdictLabel"]
        tiers.append(tier)

    display_only = []
    for dk, dl in DISPLAY_ONLY:
        w = _round1(weekly.get(dk, 0.0))
        display_only.append({"key": dk, "label": dl,
                             "weeklySets": w, "totalSets": round(w * PERIOD_WEEKS)})

    level = "LOW" if done_sessions < CONFIDENCE_THRESHOLD else "NORMAL"
    msg = (f"최근 4주 완료된 운동이 {done_sessions}회로 적어 판정 신뢰도가 낮습니다."
           if level == "LOW" else
           f"최근 4주 완료된 운동이 {done_sessions}회로 판정 신뢰도가 충분합니다.")

    return {
        "referenceDate": reference_date,
        "periodWeeks": PERIOD_WEEKS,
        "shoulderSplitResolved": True,
        "confidence": {
            "level": level,
            "doneSessionCount": done_sessions,
            "threshold": CONFIDENCE_THRESHOLD,
            "message": msg,
        },
        "tiers": tiers,
        "displayOnly": display_only,
    }


def _side(weekly, keys):
    return _round1(sum(weekly.get(k, 0.0) for k in keys))


def _pair(label, lkey, llabel, lkeys, rkey, rlabel, rkeys, weekly):
    lw = _side(weekly, lkeys)
    rw = _side(weekly, rkeys)
    big, small = (lkey, rw) if lw >= rw else (rkey, lw)
    bigger_w, smaller_w = max(lw, rw), min(lw, rw)
    smaller_zero = smaller_w == 0
    if bigger_w == 0:
        ratio, verdict, vlabel = None, "INSUFFICIENT_DATA", "데이터 부족"
    elif smaller_zero:
        ratio, verdict, vlabel = None, "IMBALANCED", "불균형"
    else:
        ratio = round(bigger_w / smaller_w, 2)
        if ratio <= BALANCE_THRESHOLD:
            verdict, vlabel = "BALANCED", "정상"
        else:
            verdict, vlabel = "IMBALANCED", "불균형"
    return {
        "key": label, "label": f"{llabel} / {rlabel}",
        "left": {"key": lkey, "label": llabel, "weeklySets": lw, "components": lkeys},
        "right": {"key": rkey, "label": rlabel, "weeklySets": rw, "components": rkeys},
        "biggerSide": big, "ratio": ratio, "smallerSideZero": smaller_zero,
        "verdict": verdict, "verdictLabel": vlabel,
    }


def build_balance(weekly, reference_date="2026-09-01"):
    push = ["CHEST", "DELT_FRONT", "TRICEPS"]
    pull = ["BACK", "DELT_REAR", "BICEPS"]
    upper = push + pull
    lower = ["QUADS", "POSTERIOR", "CALVES"]
    return {
        "referenceDate": reference_date,
        "periodWeeks": PERIOD_WEEKS,
        "ratioThreshold": BALANCE_THRESHOLD,
        "shoulderSplitResolved": True,
        "pairs": [
            _pair("PUSH_PULL", "PUSH", "밀기", push, "PULL", "당기기", pull, weekly),
            _pair("UPPER_LOWER", "UPPER", "상체", upper, "LOWER", "하체", lower, weekly),
        ],
    }
