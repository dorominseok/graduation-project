package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;

/** 루틴 항목 하나 — 어느 슬롯의 어떤 종목을 몇 세트. 순서는 목록의 순서다. */
public record PlannedItem(MuscleGroup muscle, Long exerciseId, int sets) {
}
