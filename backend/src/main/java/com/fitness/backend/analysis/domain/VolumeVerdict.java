package com.fitness.backend.analysis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 부위별 볼륨 4단계 판정. API 명세서 8.2 / 「운동 분석 로직 설계서」 2.2.
 *
 * <p>DB·스프링에 의존하지 않는 순수 계산이다. 이 판정이 이 작품의 핵심 주장
 * ("기준값은 문헌에서, 계산 방법은 직접 설계한다")을 떠받치므로, 집계 계층과
 * 분리해 경계값을 단위 테스트로 고정한다.
 */
public enum VolumeVerdict {

    INSUFFICIENT("부족"),
    BELOW_RECOMMENDED("권장 이하"),
    OPTIMAL("최적"),
    EXCESSIVE("과다");

    private final String label;

    VolumeVerdict(String label) {
        this.label = label;
    }

    /** 화면 표기용 한국어 라벨({@code verdictLabel}). */
    public String label() {
        return label;
    }

    /**
     * 주당 평균 세트를 4구간으로 분류한다.
     *
     * <pre>
     *   x &lt; 4          → INSUFFICIENT
     *   4 ≤ x &lt; 10     → BELOW_RECOMMENDED
     *   10 ≤ x ≤ 20    → OPTIMAL
     *   x &gt; 20         → EXCESSIVE
     * </pre>
     */
    public static VolumeVerdict classify(BigDecimal weeklySets, VolumeThresholds thresholds) {
        if (weeklySets == null) {
            throw new IllegalArgumentException("주당 평균 세트는 null일 수 없다");
        }
        if (weeklySets.signum() < 0) {
            throw new IllegalArgumentException("주당 평균 세트는 음수일 수 없다: " + weeklySets);
        }
        if (weeklySets.compareTo(thresholds.insufficientBelow()) < 0) {
            return INSUFFICIENT;
        }
        if (weeklySets.compareTo(thresholds.optimalFrom()) < 0) {
            return BELOW_RECOMMENDED;
        }
        if (weeklySets.compareTo(thresholds.optimalThrough()) <= 0) {
            return OPTIMAL;
        }
        return EXCESSIVE;
    }

    /**
     * 기간 내 세트 합을 주당 평균으로 환산한다(명세 8.2). 소수 1자리.
     *
     * <p>{@code double}이 아니라 {@link BigDecimal}을 쓰는 이유는 경계 때문이다.
     * 판정이 {@code 10.0}·{@code 20.0}에서 갈리는데 부동소수점은 그 값을 정확히
     * 표현한다는 보장이 없어, 경계에 놓인 사용자의 판정이 흔들릴 수 있다.
     */
    public static BigDecimal weeklyAverage(int totalSets, int weeks) {
        if (weeks <= 0) {
            throw new IllegalArgumentException("집계 주 수는 1 이상이어야 한다: " + weeks);
        }
        if (totalSets < 0) {
            throw new IllegalArgumentException("세트 합은 음수일 수 없다: " + totalSets);
        }
        return BigDecimal.valueOf(totalSets)
                .divide(BigDecimal.valueOf(weeks), 1, RoundingMode.HALF_UP);
    }
}
