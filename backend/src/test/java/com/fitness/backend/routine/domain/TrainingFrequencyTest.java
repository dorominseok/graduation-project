package com.fitness.backend.routine.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주당 운동 일수 — 꽉 찬 주 단위로 세고 반올림한다. */
class TrainingFrequencyTest {

    private static final RoutinePolicy POLICY = RoutinePolicy.DEFAULT;

    @Test
    @DisplayName("구간은 기록을 시작한 뒤 지난 일수에서 7일로 떨어지는 만큼 — 11일이면 1주, 28일이면 4주")
    void fullWeeks() {
        assertEquals(1, TrainingFrequency.fullWeeks(7));
        assertEquals(1, TrainingFrequency.fullWeeks(11));
        assertEquals(2, TrainingFrequency.fullWeeks(14));
        assertEquals(3, TrainingFrequency.fullWeeks(27));
        assertEquals(4, TrainingFrequency.fullWeeks(28));
    }

    @Test
    @DisplayName("월~금을 지키는 사람의 2주차 금요일 — 11일 동안 9일이지만 최근 7일에는 5일이다")
    void weekdayTrainerIsFiveNotFivePointSeven() {
        // 전에는 9 × 7 ÷ 11 = 5.7일로 보고 부위당 5세트를 줬다
        assertEquals(5, TrainingFrequency.daysPerWeek(9, 5, TrainingFrequency.fullWeeks(11), POLICY));
    }

    @Test
    @DisplayName("여러 주의 평균은 반올림한다 — 4주에 21일이면 5일, 22일이면 6일")
    void rounds() {
        assertEquals(5, TrainingFrequency.daysPerWeek(21, 21, 4, POLICY));
        assertEquals(6, TrainingFrequency.daysPerWeek(22, 22, 4, POLICY));
        assertEquals(3, TrainingFrequency.daysPerWeek(12, 12, 4, POLICY));
    }

    @Test
    @DisplayName("운동한 날이 사흘이 안 되면 모른다고 보고 기본값 5일 — 오래 쉬다 온 사람은 1일 밑으로 내려가지 않는다")
    void unknownAndFloor() {
        assertEquals(5, TrainingFrequency.daysPerWeek(2, 2, 1, POLICY));
        assertEquals(3, TrainingFrequency.daysPerWeek(3, 3, 1, POLICY));
        assertEquals(1, TrainingFrequency.daysPerWeek(3, 1, 4, POLICY));
    }
}
