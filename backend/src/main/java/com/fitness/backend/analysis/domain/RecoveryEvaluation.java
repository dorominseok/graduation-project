package com.fitness.backend.analysis.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 회복 상태 판정. DB·스프링에 의존하지 않는 순수 계산이다.
 *
 * <p>{@link #recovering}은 오늘의 루틴이 부위를 뺄 때도 쓴다({@code SlotPlanner}). 회복 지도와 루틴이
 * 같은 함수를 써야 "지도는 회복됐다는데 루틴은 그 부위를 뺀다" 같은 어긋남이 생기지 않는다.
 */
public final class RecoveryEvaluation {

    private RecoveryEvaluation() {
    }

    /**
     * 오늘 포함 {@code recoveryDays}일 안에 한 부위인지. 2일이면 오늘·어제다(48시간).
     *
     * @param lastTrained 마지막 운동일. 기록이 없으면 {@code null}
     */
    public static boolean recovering(LocalDate lastTrained, LocalDate today, int recoveryDays) {
        return lastTrained != null && recoveryDays > 0
                && !lastTrained.isBefore(today.minusDays(recoveryDays - 1L));
    }

    /**
     * 4단계 판정.
     *
     * <pre>
     *   회복 중(오늘 포함 R일 안)이고 최근 세트 ≥ heavySets → HIGH
     *   회복 중이고 최근 세트 &lt; heavySets                → MEDIUM
     *   마지막 운동이 정확히 R일 전                        → LOW
     *   그보다 오래됐거나 기록 없음, 또는 R = 0             → RECOVERED
     * </pre>
     *
     * @param lastTrained 마지막 운동일. 기록이 없으면 {@code null}
     * @param recentSets  오늘 포함 {@code recoveryDays}일 동안의 본세트 수
     */
    public static RecoveryState evaluate(LocalDate lastTrained, long recentSets, LocalDate today,
                                         int recoveryDays, int heavySets) {
        if (recovering(lastTrained, today, recoveryDays)) {
            return recentSets >= heavySets ? RecoveryState.HIGH : RecoveryState.MEDIUM;
        }
        if (lastTrained != null && recoveryDays > 0 && daysSince(lastTrained, today) == recoveryDays) {
            return RecoveryState.LOW;
        }
        return RecoveryState.RECOVERED;
    }

    /** 마지막 운동일부터 오늘까지의 일수. 오늘 했으면 0. */
    public static long daysSince(LocalDate lastTrained, LocalDate today) {
        return ChronoUnit.DAYS.between(lastTrained, today);
    }
}
