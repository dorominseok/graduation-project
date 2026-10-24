package com.fitness.backend.analysis.domain;

import java.util.Collection;
import java.util.EnumSet;

/**
 * 상위 부위(어깨·팔·하체)의 요약 배지.
 *
 * <p>상위 항목에는 판정 라벨을 붙이지 않는다. "팔 12세트 · 최적"처럼 삼두 0세트를
 * 가리는 잘못된 안심을 만들지 않기 위해서다. 대신 하위 중 가장 나쁜 상태를 배지로
 * 요약해 펼쳐 보게 한다.
 */
public enum SummaryBadge {

    ALL_OPTIMAL("모두 최적"),
    PARTIAL_BELOW("일부 권장 이하"),
    PARTIAL_INSUFFICIENT("일부 부족"),
    PARTIAL_EXCESSIVE("일부 과다"),
    MIXED("확인 필요");

    private final String label;

    SummaryBadge(String label) {
        this.label = label;
    }

    /** 화면 표기용 한국어 라벨({@code summaryBadgeLabel}). 문안은 조정될 수 있고 enum 키는 고정이다. */
    public String label() {
        return label;
    }

    /**
     * 하위 판정들로부터 배지를 결정한다.
     *
     * <p>우선순위: {@code INSUFFICIENT} &gt; {@code EXCESSIVE}
     * (부족 동반 시 {@code MIXED}) &gt; {@code BELOW_RECOMMENDED} &gt; 전부 최적.
     * 과다와 권장 이하가 함께 있으면(예: 삼두 22세트 · 이두 6세트) 과다를 앞세운다 — 더 나쁜
     * 상태를 감추지 않게.
     */
    public static SummaryBadge resolve(Collection<VolumeVerdict> childVerdicts) {
        if (childVerdicts == null || childVerdicts.isEmpty()) {
            throw new IllegalArgumentException("하위 판정이 비어 있으면 배지를 정할 수 없다");
        }
        EnumSet<VolumeVerdict> present = EnumSet.noneOf(VolumeVerdict.class);
        for (VolumeVerdict v : childVerdicts) {
            if (v == null) {
                throw new IllegalArgumentException("하위 판정에 null이 포함될 수 없다");
            }
            present.add(v);
        }
        boolean insufficient = present.contains(VolumeVerdict.INSUFFICIENT);
        boolean excessive = present.contains(VolumeVerdict.EXCESSIVE);

        if (insufficient && excessive) {
            return MIXED;
        }
        if (insufficient) {
            return PARTIAL_INSUFFICIENT;
        }
        if (excessive) {
            return PARTIAL_EXCESSIVE;
        }
        if (present.contains(VolumeVerdict.BELOW_RECOMMENDED)) {
            return PARTIAL_BELOW;
        }
        return ALL_OPTIMAL;
    }
}
