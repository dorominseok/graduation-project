package com.fitness.backend.analysis.domain;

import java.math.BigDecimal;

/**
 * 부족 판정 4구간의 경계값. API 명세서 8.5가 "코드 수정 없이 조정 가능하게"
 * 하라고 정했으므로 상수로 박지 않고 값 객체로 주입받는다
 * ({@code app.analysis.*} 프로퍼티).
 *
 * <p>필드 이름에 포함 관계를 드러낸 것은 의도적이다. 명세 8.2의 초판 표기가
 * {@code 4 ~ 10} / {@code 10 ~ 20}이어서 정확히 {@code 10.0}이 어느 구간인지
 * 정해지지 않았고, 그 경계는 4주 40세트처럼 흔하게 발생한다(LOG-13).
 *
 * @param insufficientBelow 이 값 <b>미만</b>이면 부족 (기본 4)
 * @param optimalFrom       이 값 <b>이상</b>부터 최적 (기본 10)
 * @param optimalThrough    이 값 <b>이하</b>까지 최적, 초과하면 과다 (기본 20)
 */
public record VolumeThresholds(
        BigDecimal insufficientBelow,
        BigDecimal optimalFrom,
        BigDecimal optimalThrough) {

    /** ACSM 권고(최소 유효 4세트, 최적 10~20세트, 20세트 초과 시 수익 감소) 기본값. */
    public static final VolumeThresholds ACSM_DEFAULT = new VolumeThresholds(
            BigDecimal.valueOf(4), BigDecimal.valueOf(10), BigDecimal.valueOf(20));

    public VolumeThresholds {
        if (insufficientBelow == null || optimalFrom == null || optimalThrough == null) {
            throw new IllegalArgumentException("경계값은 null일 수 없다");
        }
        if (insufficientBelow.compareTo(optimalFrom) > 0 || optimalFrom.compareTo(optimalThrough) > 0) {
            throw new IllegalArgumentException(
                    "경계값은 오름차순이어야 한다: %s / %s / %s"
                            .formatted(insufficientBelow, optimalFrom, optimalThrough));
        }
    }
}
