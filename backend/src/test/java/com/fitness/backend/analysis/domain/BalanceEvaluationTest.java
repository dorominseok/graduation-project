package com.fitness.backend.analysis.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 균형 판정 — 2배 임계와 0 나눗셈 경계. 명세 8.3 / 분석 4.2. */
class BalanceEvaluationTest {

    private static final BigDecimal TH = BalanceEvaluation.DEFAULT_RATIO_THRESHOLD;

    private static BalanceEvaluation eval(String left, String right) {
        return BalanceEvaluation.of(new BigDecimal(left), new BigDecimal(right), TH);
    }

    @ParameterizedTest(name = "밀기 {0} / 당기기 {1} → {2} (비율 {3})")
    @CsvSource({
            "20.0, 10.0, BALANCED,   2.00",   // 정확히 2배는 정상 (초과만 경고)
            "20.1, 10.0, IMBALANCED, 2.01",
            "20.0,  8.0, IMBALANCED, 2.50",
            "10.0, 20.0, BALANCED,   2.00",   // 큰 쪽이 오른쪽이어도 같은 비율
            "28.0, 18.0, BALANCED,   1.56",
            "10.0, 10.0, BALANCED,   1.00",
    })
    void evaluatesRatio(String left, String right, BalanceVerdict verdict, String ratio) {
        BalanceEvaluation r = eval(left, right);
        assertEquals(verdict, r.verdict());
        assertEquals(0, r.ratio().compareTo(new BigDecimal(ratio)));
        assertTrue(!r.smallerSideZero());
    }

    @Test
    @DisplayName("한쪽이 0이면 비율을 낼 수 없으나 불균형인 것은 분명하다")
    void smallerSideZero() {
        BalanceEvaluation r = eval("12.0", "0.0");
        assertEquals(BalanceVerdict.IMBALANCED, r.verdict());
        assertNull(r.ratio());
        assertTrue(r.smallerSideZero());
    }

    @Test
    @DisplayName("양쪽 다 0은 균형이 아니라 기록 없음이다")
    void bothZeroIsInsufficientData() {
        BalanceEvaluation r = eval("0.0", "0.0");
        assertEquals(BalanceVerdict.INSUFFICIENT_DATA, r.verdict());
        assertNull(r.ratio());
        assertTrue(!r.smallerSideZero());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> eval("-1.0", "5.0"));
        assertThrows(IllegalArgumentException.class,
                () -> BalanceEvaluation.of(BigDecimal.ONE, null, TH));
        assertThrows(IllegalArgumentException.class,
                () -> BalanceEvaluation.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO));
    }
}
