package com.fitness.backend.analysis.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 부족 판정 4구간 — 경계값을 고정한다. API 명세서 8.2 / LOG-13. */
class VolumeVerdictTest {

    private static final VolumeThresholds T = VolumeThresholds.ACSM_DEFAULT;

    @Nested
    @DisplayName("4구간 경계")
    class Boundaries {

        @ParameterizedTest(name = "주 {0}세트 → {1}")
        @CsvSource({
                "0.0,  INSUFFICIENT",
                "3.9,  INSUFFICIENT",
                "4.0,  BELOW_RECOMMENDED",   // 4는 부족이 아니다 (< 4 만 부족)
                "9.9,  BELOW_RECOMMENDED",
                "10.0, OPTIMAL",             // LOG-13이 확정한 경계 — 이전엔 두 구간에 걸쳐 있었다
                "15.0, OPTIMAL",
                "20.0, OPTIMAL",             // 20은 최적에 포함 (대조 평가 C-29: 목업은 과다로 처리)
                "20.1, EXCESSIVE",
                "40.0, EXCESSIVE",
        })
        void classifies(String weeklySets, VolumeVerdict expected) {
            assertEquals(expected, VolumeVerdict.classify(new BigDecimal(weeklySets), T));
        }

        @Test
        @DisplayName("4주 40세트는 정확히 10.0이 되어 최적으로 판정된다")
        void fortySetsOverFourWeeksIsOptimal() {
            BigDecimal weekly = VolumeVerdict.weeklyAverage(40, 4);
            assertEquals(0, weekly.compareTo(new BigDecimal("10.0")));
            assertEquals(VolumeVerdict.OPTIMAL, VolumeVerdict.classify(weekly, T));
        }
    }

    @Nested
    @DisplayName("주당 평균 환산")
    class WeeklyAverage {

        @ParameterizedTest(name = "{0}세트 / {1}주 → {2}")
        @CsvSource({
                "0,  4, 0.0",
                "15, 4, 3.8",   // 3.75 → 반올림 3.8. 부족(<4)을 유지한다
                "16, 4, 4.0",
                "48, 4, 12.0",
                "81, 4, 20.3",
        })
        void convertsToWeekly(int totalSets, int weeks, String expected) {
            assertEquals(0, VolumeVerdict.weeklyAverage(totalSets, weeks).compareTo(new BigDecimal(expected)));
        }

        @Test
        @DisplayName("소수 1자리로 고정한다 — 화면 표기와 판정이 같은 값을 쓴다")
        void keepsOneDecimal() {
            assertEquals("3.3", VolumeVerdict.weeklyAverage(13, 4).toPlainString());
        }

        @Test
        void rejectsNonPositiveWeeks() {
            assertThrows(IllegalArgumentException.class, () -> VolumeVerdict.weeklyAverage(10, 0));
        }
    }

    @Test
    @DisplayName("음수·null은 계산 대상이 아니다")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> VolumeVerdict.classify(null, T));
        assertThrows(IllegalArgumentException.class, () -> VolumeVerdict.classify(new BigDecimal("-0.1"), T));
    }

    @Test
    @DisplayName("경계값이 뒤바뀐 설정은 거부한다")
    void rejectsUnorderedThresholds() {
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeThresholds(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TWO));
    }
}
