package com.fitness.backend.stats.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 추정 1RM 산출. API 명세서 7.1 / 「운동 분석 로직 설계서」 1.3 / LOG-07.
 *
 * <p>사용자에게 1RM을 직접 묻지 않는다. 실제 측정은 부상 위험이 있고 대부분의
 * 사용자가 자신의 1RM을 모르기 때문이다. 대신 [중량 × 횟수] 기록에서 추정한다.
 */
public final class OneRepMax {

    /** 산출 대상 반복 횟수 상한. 추정 공식은 고반복 구간에서 오차가 커지므로 애초에 계산에 넣지 않는다(LOG-07). */
    public static final int DEFAULT_MAX_REPS = 12;

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private OneRepMax() {
    }

    /** 해당 세트가 1RM 추정 대상인지. 종목의 {@code measureType}이 {@code WEIGHT_REPS}인지는 호출부가 판단한다. */
    public static boolean isEligible(int reps, int maxReps) {
        return reps >= 1 && reps <= maxReps;
    }

    /**
     * Epley 공식으로 추정한다. {@code 1RM = w × (1 + reps / 30)}, 소수 1자리.
     *
     * <p>{@code reps = 1}이면 그 중량이 곧 1RM이므로 공식을 적용하지 않는다.
     * 적용하면 실제로 든 무게보다 3.3% 큰 값이 나온다.
     */
    public static BigDecimal epley(BigDecimal weightKg, int reps) {
        if (weightKg == null) {
            throw new IllegalArgumentException("중량은 null일 수 없다");
        }
        if (weightKg.signum() < 0) {
            throw new IllegalArgumentException("중량은 음수일 수 없다: " + weightKg);
        }
        if (reps < 1) {
            throw new IllegalArgumentException("반복 횟수는 1 이상이어야 한다: " + reps);
        }
        if (reps == 1) {
            return weightKg.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(THIRTY, 10, RoundingMode.HALF_UP));
        return weightKg.multiply(factor).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 세트별 중량이 그날 추정 1RM의 몇 %인지(명세 7.2). 반올림한 정수.
     *
     * <p>워밍업 세트도 표시 대상이다 — 볼륨 집계에서는 빼지만(8.2) 그날의 강도
     * 스냅샷에는 포함한다.
     */
    public static Integer intensityPercent(BigDecimal weightKg, BigDecimal estimatedOneRm) {
        if (weightKg == null || estimatedOneRm == null || estimatedOneRm.signum() <= 0) {
            return null;
        }
        return weightKg.multiply(BigDecimal.valueOf(100))
                .divide(estimatedOneRm, 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
