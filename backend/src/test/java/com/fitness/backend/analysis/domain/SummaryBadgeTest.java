package com.fitness.backend.analysis.domain;

import static com.fitness.backend.analysis.domain.VolumeVerdict.BELOW_RECOMMENDED;
import static com.fitness.backend.analysis.domain.VolumeVerdict.EXCESSIVE;
import static com.fitness.backend.analysis.domain.VolumeVerdict.INSUFFICIENT;
import static com.fitness.backend.analysis.domain.VolumeVerdict.OPTIMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요약 배지 우선순위 — 상위 항목이 하위의 나쁜 상태를 가리지 않는지 확인한다. 명세 8.2 / LOG-09·LOG-10. */
class SummaryBadgeTest {

    @Test
    @DisplayName("하위가 전부 최적이면 모두 최적")
    void allOptimal() {
        assertEquals(SummaryBadge.ALL_OPTIMAL, SummaryBadge.resolve(List.of(OPTIMAL, OPTIMAL)));
    }

    @Test
    @DisplayName("팔 12세트가 '최적'으로 뭉뚱그려지지 않는다 — 삼두 0 / 이두 12는 일부 부족")
    void armsWithZeroTricepsIsNotOptimal() {
        assertEquals(SummaryBadge.PARTIAL_INSUFFICIENT, SummaryBadge.resolve(List.of(INSUFFICIENT, OPTIMAL)));
    }

    @Test
    @DisplayName("어깨(앞) 권장 이하 / 어깨(뒤) 부족 → 부족이 우선한다")
    void insufficientOutranksBelow() {
        assertEquals(SummaryBadge.PARTIAL_INSUFFICIENT, SummaryBadge.resolve(List.of(BELOW_RECOMMENDED, INSUFFICIENT)));
    }

    @Test
    @DisplayName("한쪽 부족 · 한쪽 과다는 서로 반대 방향이므로 확인 필요")
    void oppositeDirectionsAreMixed() {
        assertEquals(SummaryBadge.MIXED, SummaryBadge.resolve(List.of(INSUFFICIENT, EXCESSIVE)));
    }

    @Test
    @DisplayName("과다만 있으면 일부 과다")
    void excessiveOnly() {
        assertEquals(SummaryBadge.PARTIAL_EXCESSIVE, SummaryBadge.resolve(List.of(OPTIMAL, EXCESSIVE)));
    }

    @Test
    @DisplayName("권장 이하만 있으면 일부 권장 이하")
    void belowOnly() {
        assertEquals(SummaryBadge.PARTIAL_BELOW, SummaryBadge.resolve(List.of(BELOW_RECOMMENDED, OPTIMAL)));
    }

    @Test
    @DisplayName("과다 + 권장 이하 — 명세의 표와 우선순위 줄이 어긋나는 조합. 우선순위 줄을 따른다")
    void excessiveWithBelowFollowsPriorityLine() {
        // 예: 삼두 22세트(과다) · 이두 6세트(권장 이하).
        // 8.2의 표는 PARTIAL_EXCESSIVE의 조건에 "권장이하 없음"을 달았으나,
        // 바로 아래 우선순위 줄은 EXCESSIVE > BELOW_RECOMMENDED라고 적었다.
        // 더 나쁜 상태를 감추지 않는 쪽을 택한다.
        assertEquals(SummaryBadge.PARTIAL_EXCESSIVE, SummaryBadge.resolve(List.of(EXCESSIVE, BELOW_RECOMMENDED)));
    }

    @Test
    @DisplayName("하위가 하나뿐이어도(가슴·등·코어) 배지 계산 자체는 성립한다")
    void singleChild() {
        assertEquals(SummaryBadge.PARTIAL_INSUFFICIENT, SummaryBadge.resolve(List.of(INSUFFICIENT)));
    }

    @Test
    void rejectsEmptyOrNull() {
        assertThrows(IllegalArgumentException.class, () -> SummaryBadge.resolve(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SummaryBadge.resolve(null));
    }
}
