package com.fitness.backend.analysis.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 주당 평균의 분모 (LOG-31).
 *
 * <p>기록이 4주 미만인 사람을 28일로 나누면 아직 오지 않은 날이 "안 한 날"로 섞여
 * 판정이 실제보다 낮게 나온다. 분모를 첫 기록일부터 센 일수로 줄이되 7일 아래로는
 * 내리지 않는다.
 */
class AveragingBasisTest {

    private static final LocalDate REF = LocalDate.of(2026, 10, 3);

    @ParameterizedTest(name = "첫 기록 {0}일 전 → {1}일로 나눈다")
    @CsvSource({
            "0,  7",    // 오늘 처음 — 하루로 나누면 주 35세트가 된다. 최소 7일
            "3,  7",    // 4일째도 최소 7일
            "6,  7",    // 딱 7일째
            "7,  8",    // 8일째부터 실제 일수
            "16, 17",
            "27, 28",   // 28일째 — 구간과 같아진다
            "40, 28",   // 구간보다 오래됐으면 지금까지와 같은 28일(= ÷ 4)
    })
    void basisFollowsElapsedDays(int daysAgo, int expected) {
        assertEquals(expected, AveragingBasis.days(REF.minusDays(daysAgo), REF, 28, 7));
    }

    @Test
    @DisplayName("기록이 없으면 구간 그대로 — 세트가 0이라 무엇으로 나눠도 0이다")
    void noRecordUsesWindow() {
        assertEquals(28, AveragingBasis.days(null, REF, 28, 7));
    }

    @Test
    @DisplayName("구간이 최솟값보다 짧으면 구간을 넘지 않는다")
    void neverExceedsWindow() {
        assertEquals(7, AveragingBasis.days(REF, REF, 7, 7));
        assertEquals(5, AveragingBasis.days(REF, REF, 5, 7));
    }

    @Test
    @DisplayName("첫 기록일이 기준일 뒤면 호출 쪽 잘못이다")
    void rejectsFirstRecordAfterReference() {
        assertThrows(IllegalArgumentException.class, () -> AveragingBasis.days(REF.plusDays(1), REF, 28, 7));
        assertThrows(IllegalArgumentException.class, () -> AveragingBasis.days(REF, REF, 0, 7));
    }
}
