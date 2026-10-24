package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;

/**
 * 오늘 할 부위 하나와 그 세트. 규칙이 정한다 — LLM은 이 안에서 종목만 고른다(설계서 0.1).
 *
 * @param weeklySets 근거. 루틴 항목에 그대로 남는다(설계서 10.2)
 * @param verdict    근거
 * @param fill       하루 총량을 채우려고 넣은 최적 부위인지(설계서 2.5)
 */
public record RoutineSlot(MuscleGroup muscle,
                          int sets,
                          BigDecimal weeklySets,
                          VolumeVerdict verdict,
                          boolean fill) {
}
