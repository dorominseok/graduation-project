package com.fitness.backend.analysis.domain;

import java.util.Collection;
import java.util.EnumSet;

/**
 * 상위 부위(어깨·팔·하체)의 요약 배지. API 명세서 8.2 / LOG-10.
 *
 * <p>상위 항목에는 판정 라벨을 붙이지 않는다. "팔 12세트 · 최적"처럼 삼두 0세트를
 * 가리는 잘못된 안심을 만들지 않기 위한 규칙이며, LOG-09를 작성한 이유 자체였다.
 * 대신 하위 중 가장 나쁜 상태를 배지로 요약해 펼쳐 보게 유도한다.
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
     * <p>우선순위(명세 8.2): {@code INSUFFICIENT} &gt; {@code EXCESSIVE}
     * (부족 동반 시 {@code MIXED}) &gt; {@code BELOW_RECOMMENDED} &gt; 전부 최적.
     *
     * <p><b>명세의 두 서술이 어긋나는 지점이 있다.</b> 표는 {@code PARTIAL_EXCESSIVE}의
     * 조건을 "하위 중 최악이 EXCESSIVE, <i>부족·권장이하 없음</i>"이라 적었으나,
     * 바로 아래 우선순위 줄은 {@code EXCESSIVE}가 {@code BELOW_RECOMMENDED}보다
     * 앞선다고 적었다. 과다와 권장 이하가 함께 있을 때(예: 삼두 22세트 · 이두 6세트)
     * 두 서술이 다른 답을 낸다. 여기서는 <b>우선순위 줄</b>을 따른다 — 표의 조건절보다
     * 계산 규칙으로서 명확하고, 더 나쁜 상태를 감추지 않는 쪽이기 때문이다.
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
