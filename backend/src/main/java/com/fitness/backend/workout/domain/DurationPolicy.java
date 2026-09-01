package com.fitness.backend.workout.domain;

import java.time.Duration;

/**
 * 운동 시간 산출의 보정 규칙. 「운동기록_방식_설계서」 4.2 / API 명세서 8.5.
 *
 * @param interSetCap    세트 간격 상한. 앱을 닫고 나갔다 몇 시간 뒤 마지막 세트를 입력해도 시간이 부풀지 않게 한다
 * @param lastSetBonus   마지막 세트 수행 시간 보정. 첫~마지막 세트 시각 차이만 쓰면 마지막 세트가 누락된다
 * @param sessionCap     세션 상한. 캡을 통과한 값들이 누적되는 극단적 경우의 최종 방어선
 */
public record DurationPolicy(Duration interSetCap, Duration lastSetBonus, Duration sessionCap) {

    public static final DurationPolicy DEFAULT = new DurationPolicy(
            Duration.ofMinutes(15), Duration.ofSeconds(90), Duration.ofHours(4));

    public DurationPolicy {
        if (interSetCap == null || lastSetBonus == null || sessionCap == null) {
            throw new IllegalArgumentException("보정 규칙은 null일 수 없다");
        }
        if (interSetCap.isNegative() || lastSetBonus.isNegative() || sessionCap.isNegative()) {
            throw new IllegalArgumentException("보정 규칙은 음수일 수 없다");
        }
    }
}
