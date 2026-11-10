package com.fitness.backend.analysis.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecoveryEvaluationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final int R = 2;       // 오늘·어제
    private static final int HEAVY = 6;

    private static RecoveryState state(Integer daysAgo, long recentSets) {
        LocalDate last = daysAgo == null ? null : TODAY.minusDays(daysAgo);
        return RecoveryEvaluation.evaluate(last, recentSets, TODAY, R, HEAVY);
    }

    @Test
    @DisplayName("오늘·어제 한 부위는 회복 중 — 세트가 기준 이상이면 HIGH, 미만이면 MEDIUM")
    void recoveringSplitsByHeavySets() {
        assertEquals(RecoveryState.HIGH, state(0, 6), "6세트는 기준에 포함된다");
        assertEquals(RecoveryState.MEDIUM, state(0, 5));
        assertEquals(RecoveryState.HIGH, state(1, 9));
        assertEquals(RecoveryState.MEDIUM, state(1, 1));
    }

    @Test
    @DisplayName("정확히 R일 전이면 거의 회복, 그보다 오래되면 회복됨 — 세트 수는 보지 않는다")
    void afterRecoveryWindow() {
        assertEquals(RecoveryState.LOW, state(2, 0));
        assertEquals(RecoveryState.RECOVERED, state(3, 0));
        assertEquals(RecoveryState.RECOVERED, state(20, 0));
    }

    @Test
    @DisplayName("기록이 없으면 회복됨")
    void noRecord() {
        assertEquals(RecoveryState.RECOVERED, state(null, 0));
    }

    @Test
    @DisplayName("회복 일수가 0이면 아무 부위도 회복 중이 아니다 — 루틴도 그때는 빼지 않는다")
    void zeroRecoveryDays() {
        assertEquals(RecoveryState.RECOVERED, RecoveryEvaluation.evaluate(TODAY, 10, TODAY, 0, HEAVY));
        assertFalse(RecoveryEvaluation.recovering(TODAY, TODAY, 0));
    }

    @Test
    @DisplayName("회복 중 판정은 오늘 포함 R일 — 루틴의 회복 제외와 같은 경계다")
    void recoveringBoundary() {
        assertTrue(RecoveryEvaluation.recovering(TODAY, TODAY, R));
        assertTrue(RecoveryEvaluation.recovering(TODAY.minusDays(1), TODAY, R));
        assertFalse(RecoveryEvaluation.recovering(TODAY.minusDays(2), TODAY, R));
        assertFalse(RecoveryEvaluation.recovering(null, TODAY, R));
        assertTrue(RecoveryEvaluation.recovering(TODAY.minusDays(2), TODAY, 3), "R이 3이면 그제도 회복 중");
    }

    @Test
    @DisplayName("며칠 전인지는 오늘이 0")
    void daysSince() {
        assertEquals(0, RecoveryEvaluation.daysSince(TODAY, TODAY));
        assertEquals(3, RecoveryEvaluation.daysSince(TODAY.minusDays(3), TODAY));
    }
}
