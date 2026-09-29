package com.fitness.backend.analysis.domain;

import java.util.List;

/**
 * 화면에 보이는 상위 부위 6종. API 명세서 8.2 {@code tiers[]} / LOG-10.
 *
 * <p>선언 순서가 곧 응답 순서다(명세 8.2 — "항상 6개, 고정 순서"). 화면이 정렬을
 * 하지 않아도 되게 서버가 순서까지 정해 내려준다.
 *
 * <p>하위가 둘인 항목(어깨·팔·하체)에는 <b>판정을 붙이지 않는다.</b> 합쳐서 최적이어도
 * 한쪽이 0일 수 있어서다. 대신 {@link SummaryBadge}로 "일부 부족"을 알려 펼쳐 보게 한다.
 */
public enum TierGroup {

    CHEST("가슴", MuscleGroup.CHEST),
    BACK("등", MuscleGroup.BACK),
    SHOULDERS("어깨", MuscleGroup.DELT_FRONT, MuscleGroup.DELT_REAR),
    ARMS("팔", MuscleGroup.TRICEPS, MuscleGroup.BICEPS),
    LEGS("하체", MuscleGroup.QUADS, MuscleGroup.POSTERIOR),
    CORE("코어", MuscleGroup.CORE);

    private final String label;
    private final List<MuscleGroup> children;

    TierGroup(String label, MuscleGroup... children) {
        this.label = label;
        this.children = List.of(children);
    }

    public String label() {
        return label;
    }

    public List<MuscleGroup> children() {
        return children;
    }

    /** 하위가 둘이면 상위에 판정 대신 배지가 붙는다(명세 8.2). */
    public boolean hasChildren() {
        return children.size() > 1;
    }
}
