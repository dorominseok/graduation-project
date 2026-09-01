package com.fitness.backend.analysis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 밀기/당기기·상하체 균형 판정 결과. API 명세서 8.3 / 「운동 분석 로직 설계서」 4장.
 *
 * @param verdict          판정
 * @param ratio            큰 쪽 ÷ 작은 쪽, 소수 2자리. 작은 쪽이 0이면 {@code null}
 * @param smallerSideZero  작은 쪽이 0이면 {@code true} — 화면은 "비율 계산 불가"로 표기한다
 */
public record BalanceEvaluation(BalanceVerdict verdict, BigDecimal ratio, boolean smallerSideZero) {

    /** 균형 판정 임계. 기본 2.0배 — 실측상 일반인의 밀기:당기기가 약 1.5:1이라 1:1을 기준 삼으면 정상인 다수를 오판한다(분석 4.2). */
    public static final BigDecimal DEFAULT_RATIO_THRESHOLD = BigDecimal.valueOf(2.0);

    /**
     * 두 쪽의 주당 평균 세트로 균형을 판정한다.
     *
     * <pre>
     *   양쪽 다 0        → INSUFFICIENT_DATA (ratio null)
     *   한쪽만 0         → IMBALANCED, ratio null, smallerSideZero true
     *   비율 ≤ 임계      → BALANCED
     *   비율 &gt; 임계      → IMBALANCED
     * </pre>
     */
    public static BalanceEvaluation of(BigDecimal left, BigDecimal right, BigDecimal ratioThreshold) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("양쪽 세트 수는 null일 수 없다");
        }
        if (left.signum() < 0 || right.signum() < 0) {
            throw new IllegalArgumentException("세트 수는 음수일 수 없다: %s / %s".formatted(left, right));
        }
        if (ratioThreshold == null || ratioThreshold.signum() <= 0) {
            throw new IllegalArgumentException("비율 임계는 양수여야 한다: " + ratioThreshold);
        }

        BigDecimal bigger = left.max(right);
        BigDecimal smaller = left.min(right);

        if (bigger.signum() == 0) {
            // 양쪽 다 0 — 기록이 없는 것이지 균형이 잡힌 것이 아니다.
            return new BalanceEvaluation(BalanceVerdict.INSUFFICIENT_DATA, null, false);
        }
        if (smaller.signum() == 0) {
            // 한쪽이 0이면 비율이 무한대라 수치를 낼 수 없다. 다만 불균형인 것은 분명하다.
            return new BalanceEvaluation(BalanceVerdict.IMBALANCED, null, true);
        }

        BigDecimal ratio = bigger.divide(smaller, 2, RoundingMode.HALF_UP);
        BalanceVerdict verdict = ratio.compareTo(ratioThreshold) > 0
                ? BalanceVerdict.IMBALANCED
                : BalanceVerdict.BALANCED;
        return new BalanceEvaluation(verdict, ratio, false);
    }
}
