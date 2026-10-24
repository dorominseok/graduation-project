package com.fitness.backend.analysis.domain;

import java.math.BigDecimal;

/**
 * 부족 판정 4구간의 경계값. 코드를 고치지 않고 조정할 수 있게 설정({@code app.analysis.*})으로 받는다.
 *
 * <p>필드 이름에 포함 관계(미만·이상·이하)를 드러냈다. {@code 10.0}처럼 경계에 놓인 값이 어느
 * 구간인지 이름만 보고 알 수 있어야 하고, 그 경계는 4주 40세트처럼 흔하게 나온다.
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
