package com.fitness.backend.routine.domain;

import java.math.BigDecimal;

/**
 * 루틴 생성 규칙의 수치. 「루틴생성_로직_설계서」 13장 — 초기값과 근거는 설계서에 있고,
 * 실험(11.5)과 실사용으로 조정한다.
 *
 * @param targetWeeklySets   부위당 주 목표. 최적 하한(분석 2.2)
 * @param perMuscleMinSets   부위당 한 번에 최소
 * @param perMuscleMaxSets   부위당 한 번에 최대
 * @param fillMaxSets        총량을 채우려고 넣는 최적 부위의 최대 세트
 * @param dailyMinSets       하루 총량 하한. 못 미치면 최적 부위로 채운다
 * @param dailyMaxSets       하루 총량 상한
 * @param recoveryDays       이 일수 안에(오늘 포함) 한 부위는 뺀다. 48시간 = 2일(오늘·어제)
 * @param defaultDaysPerWeek 주당 운동 일수를 모를 때
 * @param minKnownDays       운동한 날이 이보다 적으면 주당 일수를 모른다고 본다
 * @param optimalThrough     과다 경계. 루틴을 해도 이 값을 넘지 않아야 한다(R2)
 */
public record RoutinePolicy(int targetWeeklySets,
                            int perMuscleMinSets,
                            int perMuscleMaxSets,
                            int fillMaxSets,
                            int dailyMinSets,
                            int dailyMaxSets,
                            int recoveryDays,
                            int defaultDaysPerWeek,
                            int minKnownDays,
                            BigDecimal optimalThrough) {

    public static final RoutinePolicy DEFAULT =
            new RoutinePolicy(10, 2, 6, 3, 12, 18, 2, 3, 3, BigDecimal.valueOf(20));

    public RoutinePolicy {
        if (perMuscleMinSets < 1 || perMuscleMaxSets < perMuscleMinSets) {
            throw new IllegalArgumentException("부위당 세트 범위가 올바르지 않다");
        }
        if (dailyMinSets < 1 || dailyMaxSets < dailyMinSets) {
            throw new IllegalArgumentException("하루 총량 범위가 올바르지 않다");
        }
        if (targetWeeklySets < 1 || recoveryDays < 0 || defaultDaysPerWeek < 1 || minKnownDays < 0) {
            throw new IllegalArgumentException("루틴 규칙 값이 올바르지 않다");
        }
    }
}
