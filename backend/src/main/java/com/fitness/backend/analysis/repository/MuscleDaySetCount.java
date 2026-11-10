package com.fitness.backend.analysis.repository;

import com.fitness.backend.exercise.domain.DeltRegion;
import java.time.LocalDate;

/**
 * 집계 쿼리 한 줄. {@code primary_muscle}·{@code delt_region}·날짜 조합별 본세트 수.
 * 판정 부위로 접는 일은 {@link MuscleSetCount}처럼 {@code MuscleGroup}이 한다.
 */
public record MuscleDaySetCount(String primaryMuscle, DeltRegion deltRegion, LocalDate performedOn, long sets) {
}
