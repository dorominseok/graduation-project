package com.fitness.backend.analysis.repository;

import com.fitness.backend.exercise.domain.DeltRegion;
import java.time.LocalDate;

/**
 * 날짜·근육별 본세트 수. 주간 집계의 재료다(LOG-30).
 *
 * <p>주 단위로 DB에서 묶지 않고 날짜로 꺼내 자바에서 나눈다. 주 시작 요일(월요일,
 * 명세 1.2)을 SQL의 {@code date_trunc('week', ...)}에 맡기면 그 규칙이 쿼리 문자열
 * 안에 숨고, 데이터베이스 설정에 따라 결과가 달라질 여지가 생긴다.
 */
public record DailyMuscleSetCount(LocalDate performedOn, String primaryMuscle, DeltRegion deltRegion, long sets) {
}
