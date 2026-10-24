package com.fitness.backend.routine.repository;

/** 종목별 수 — 쿼리에 따라 기록한 날 수이거나 세트 수다 */
public record ExerciseDaysRow(Long exerciseId, long count) {
}
