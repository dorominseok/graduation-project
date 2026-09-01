package com.fitness.backend.stats.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 추정 1RM — Epley 공식과 reps ≤ 12 제한. 명세 7.1 / LOG-07. */
class OneRepMaxTest {

    @ParameterizedTest(name = "{0}kg × {1}회 → {2}kg")
    @CsvSource({
            "100.0,  1, 100.0",   // 1회는 공식을 적용하지 않는다 (적용하면 103.3이 된다)
            "100.0,  5, 116.7",
            "80.0,   5,  93.3",
            "82.5,   5,  96.3",
            "70.0,  10,  93.3",
            "60.0,  12,  84.0",
            "62.5,   8,  79.2",
    })
    void epley(String weight, int reps, String expected) {
        assertEquals(expected, OneRepMax.epley(new BigDecimal(weight), reps).toPlainString());
    }

    @Test
    @DisplayName("1회 세트에 공식을 적용하면 실제로 든 무게보다 커진다 — 그래서 예외로 둔다")
    void singleRepReturnsWeightItself() {
        assertEquals("100.0", OneRepMax.epley(new BigDecimal("100.0"), 1).toPlainString());
    }

    @ParameterizedTest(name = "reps {0} → 대상 여부 {1}")
    @CsvSource({"1,true", "12,true", "13,false", "20,false", "0,false"})
    void eligibility(int reps, boolean expected) {
        assertEquals(expected, OneRepMax.isEligible(reps, OneRepMax.DEFAULT_MAX_REPS));
    }

    @Test
    @DisplayName("고반복 구간은 오차가 커서 애초에 계산에 넣지 않는다")
    void highRepsExcluded() {
        assertFalse(OneRepMax.isEligible(15, OneRepMax.DEFAULT_MAX_REPS));
        assertTrue(OneRepMax.isEligible(12, OneRepMax.DEFAULT_MAX_REPS));
    }

    @ParameterizedTest(name = "{0}kg / 1RM {1}kg → {2}%")
    @CsvSource({
            "70.0, 95.0, 74",
            "60.0, 95.0, 63",
            "95.0, 95.0, 100",
    })
    void intensityPercent(String weight, String oneRm, int expected) {
        assertEquals(expected, OneRepMax.intensityPercent(new BigDecimal(weight), new BigDecimal(oneRm)));
    }

    @Test
    @DisplayName("1RM을 낼 수 없는 종목은 강도도 null이다 (맨몸·시간 기반)")
    void intensityIsNullWithoutOneRm() {
        assertNull(OneRepMax.intensityPercent(new BigDecimal("70.0"), null));
        assertNull(OneRepMax.intensityPercent(null, new BigDecimal("95.0")));
        assertNull(OneRepMax.intensityPercent(new BigDecimal("70.0"), BigDecimal.ZERO));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> OneRepMax.epley(null, 5));
        assertThrows(IllegalArgumentException.class, () -> OneRepMax.epley(new BigDecimal("-1"), 5));
        assertThrows(IllegalArgumentException.class, () -> OneRepMax.epley(new BigDecimal("60"), 0));
    }
}
