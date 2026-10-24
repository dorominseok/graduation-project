package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 루틴을 짜는 시점의 한 판정 부위.
 *
 * @param weeklySets  기준일(어제)까지 최근 4주 주당 평균 세트 (분석 8.2와 같은 값)
 * @param verdict     그 값의 판정
 * @param lastTrained 그 부위에 본세트가 있는 가장 최근 날짜. <b>오늘 포함</b>. 한 번도 안 했으면 null
 * @param lesserSide  밀기/당기기·상체/하체 불균형에서 적은 쪽에 속하는지(설계서 2.3 균형 보정)
 */
public record MuscleState(MuscleGroup muscle,
                          BigDecimal weeklySets,
                          VolumeVerdict verdict,
                          LocalDate lastTrained,
                          boolean lesserSide) {
}
