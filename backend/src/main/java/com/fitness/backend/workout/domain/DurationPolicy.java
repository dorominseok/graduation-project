package com.fitness.backend.workout.domain;

import java.time.Duration;

/**
 * 운동 시간 산출 규칙.
 *
 * @param boutGap      이보다 긴 공백에서 구간을 나눈다. 아침·저녁에 두 번 운동해도 사이 공백은 시간에 들어가지 않는다
 * @param lastSetBonus 구간마다 더하는 마지막 세트 수행 시간. 첫~마지막 세트 시각 차이만 쓰면 마지막 세트가 빠진다
 * @param dayCap       하루 상한. 구간이 비정상적으로 이어지는 경우의 최종 방어선
 */
public record DurationPolicy(Duration boutGap, Duration lastSetBonus, Duration dayCap) {

    public static final DurationPolicy DEFAULT = new DurationPolicy(
            Duration.ofMinutes(30), Duration.ofSeconds(90), Duration.ofHours(4));

    public DurationPolicy {
        if (boutGap == null || lastSetBonus == null || dayCap == null) {
            throw new IllegalArgumentException("보정 규칙은 null일 수 없다");
        }
        if (boutGap.isNegative() || lastSetBonus.isNegative() || dayCap.isNegative()) {
            throw new IllegalArgumentException("보정 규칙은 음수일 수 없다");
        }
    }
}
