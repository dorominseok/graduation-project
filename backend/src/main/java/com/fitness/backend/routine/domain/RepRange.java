package com.fitness.backend.routine.domain;

import com.fitness.backend.exercise.domain.MeasureType;

/**
 * 루틴에 표시하는 반복 범위.
 *
 * <p>훈련 목표별로 나누지 않고 측정 유형으로 고정한다. 강도(중량)는 처방하지 않는다 —
 * 해 본 종목은 기록 화면이 지난 기록으로 채우고, 처음인 종목은 빈 칸에서 시작한다.
 *
 * @param min 최소 (시간 종목은 초)
 * @param max 최대
 */
public record RepRange(int min, int max) {

    public static RepRange of(MeasureType measureType) {
        return switch (measureType) {
            case WEIGHT_REPS, WEIGHTED_BODYWEIGHT -> new RepRange(8, 12);
            case BODYWEIGHT_REPS -> new RepRange(10, 15);
            case TIME -> new RepRange(30, 60);
        };
    }
}
