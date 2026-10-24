package com.fitness.backend.routine.repository;

import com.fitness.backend.exercise.domain.DeltRegion;
import java.time.LocalDate;

/** 주동근(어깨는 앞·뒤까지)별 마지막 운동일. 판정 부위로 접는 일은 서비스가 한다 */
public record LastTrainedRow(String primaryMuscle, DeltRegion deltRegion, LocalDate lastDate) {
}
