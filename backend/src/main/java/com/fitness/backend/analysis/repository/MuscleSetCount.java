package com.fitness.backend.analysis.repository;

import com.fitness.backend.exercise.domain.DeltRegion;

/**
 * 집계 쿼리 한 줄. {@code primary_muscle}·{@code delt_region} 조합별 본세트 수.
 *
 * <p>판정 부위로 접는 일은 {@link com.fitness.backend.analysis.domain.MuscleGroup}이
 * 한다. DB에 {@code case when}으로 매핑을 박으면 규칙이 SQL 문자열 안으로 숨어
 * 단위 테스트로 고정할 수 없다.
 */
public record MuscleSetCount(String primaryMuscle, DeltRegion deltRegion, long sets) {
}
