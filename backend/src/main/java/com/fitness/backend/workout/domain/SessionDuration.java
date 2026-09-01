package com.fitness.backend.workout.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 세트 저장 시각으로부터 운동 시간을 산출한다. 「운동기록_방식_설계서」 4.2 / LOG-03.
 *
 * <pre>
 *   duration = Σ(연속한 두 세트의 간격, 15분 초과 시 15분으로 캡) + 90초
 *   상한: 4시간
 * </pre>
 *
 * <p>시작·종료 버튼을 두지 않은 이유는 운동 중에 버튼 조작을 잊기 쉽고, 종료를
 * 누르지 않으면 데이터가 통째로 유실되거나 비정상적으로 길어지기 때문이다.
 * 이 값은 그룹 활동 공유의 참고 지표이므로 분 단위 정밀도면 충분하고,
 * 보정 규칙은 이상치만 막는 수준으로 둔다.
 */
public final class SessionDuration {

    private SessionDuration() {
    }

    /**
     * {@code LIVE} 세션의 운동 시간을 산출한다.
     *
     * <p>{@code BACKFILL} 세션에는 쓰지 않는다 — 저장 시각이 실제 운동 시각과
     * 무관하므로 {@code durationSec}을 {@code null}로 두고, 사용자가 직접 입력한
     * {@code durationOverrideSec}만 사용한다(기록 방식 4.3).
     *
     * @param recordedAts 세션에 속한 전 세트의 저장 시각. 순서는 무관하며 내부에서 정렬한다
     * @return 산출된 운동 시간. 세트가 없으면 {@link Duration#ZERO}
     */
    public static Duration of(List<OffsetDateTime> recordedAts, DurationPolicy policy) {
        if (recordedAts == null) {
            throw new IllegalArgumentException("세트 저장 시각 목록은 null일 수 없다");
        }
        if (policy == null) {
            throw new IllegalArgumentException("보정 규칙은 null일 수 없다");
        }
        if (recordedAts.isEmpty()) {
            // 세트 0개 세션은 종료 자체가 422 EMPTY_SESSION으로 막히지만(명세 6.3),
            // 계산 함수 단독으로도 예외 없이 정의되게 둔다.
            return Duration.ZERO;
        }

        List<OffsetDateTime> sorted = recordedAts.stream()
                .peek(t -> {
                    if (t == null) {
                        throw new IllegalArgumentException("세트 저장 시각에 null이 포함될 수 없다");
                    }
                })
                .sorted(Comparator.naturalOrder())
                .toList();

        Duration total = Duration.ZERO;
        for (int i = 1; i < sorted.size(); i++) {
            Duration gap = Duration.between(sorted.get(i - 1), sorted.get(i));
            if (gap.isNegative()) {
                gap = Duration.ZERO;
            }
            total = total.plus(gap.compareTo(policy.interSetCap()) > 0 ? policy.interSetCap() : gap);
        }
        total = total.plus(policy.lastSetBonus());

        return total.compareTo(policy.sessionCap()) > 0 ? policy.sessionCap() : total;
    }
}
