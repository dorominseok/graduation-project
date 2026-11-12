package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * 주당 운동 일수. 부위당 세트(주 목표 ÷ 묶음이 돌아오는 횟수)의 분모다.
 *
 * <p><b>꽉 찬 주 단위로 세고 반올림한다.</b> 전에는 기록을 시작한 뒤 지난 일수로 나눴는데, 월~금을 지키는
 * 사람도 2주차 금요일에는 9일 ÷ 11일 × 7 = 5.7일로 나왔다 — 구간이 운동한 날에서 시작해 주말 전에 끝나기
 * 때문이다. 5.3이든 5.7이든 부위당 세트가 6에서 5로 내려가고, 5세트는 두 종목으로 나누지 않아
 * 루틴이 15세트 세 종목으로 줄었다. 7일 단위 구간에는 평일과 주말이 늘 같은 수만큼 들어간다.
 */
public final class TrainingFrequency {

    private static final int DAYS_PER_WEEK = 7;

    private TrainingFrequency() {
    }

    /**
     * 주당 일수를 셀 구간(주). 분석의 분모({@code basisDays} — 기록을 시작한 뒤 지난 일수, 7~28)에서
     * 7일로 떨어지지 않는 나머지는 버린다.
     */
    public static int fullWeeks(int basisDays) {
        return Math.max(1, basisDays / DAYS_PER_WEEK);
    }

    /**
     * @param knownDays       분석 구간(4주)에서 운동한 날 수. 너무 적으면 빈도를 모른다고 본다
     * @param daysInFullWeeks 기준일까지 {@code fullWeeks}주 동안 운동한 날 수
     * @return 주당 운동 일수(정수). 모르면 기본값 — 첫 주의 하루 이틀만 보고 "주 1회"라고 단정하지 않는다
     */
    public static int daysPerWeek(int knownDays, int daysInFullWeeks, int fullWeeks, RoutinePolicy policy) {
        if (fullWeeks < 1) {
            throw new IllegalArgumentException("구간은 1주 이상이어야 한다: " + fullWeeks);
        }
        if (knownDays < policy.minKnownDays()) {
            return policy.defaultDaysPerWeek();
        }
        return Math.max(1, Math.round((float) daysInFullWeeks / fullWeeks));
    }

    /** 판정 부위 수 — 루틴이 일주일 동안 나눠 담는 부위(종아리·전완은 루틴이 고르지 않는다) */
    private static final int JUDGED_MUSCLES = (int) Arrays.stream(MuscleGroup.values()).filter(MuscleGroup::judged).count();

    /**
     * 루틴을 그대로 따를 때 부위당 주 평균 세트의 최대. 일주일 총량(주당 일수 × 하루 상한)을 판정 부위가 나눈다.
     *
     * <p>하루 상한이 막고 있어서, 나누는 방식(하루 한 묶음·전신·상하체)을 바꿔도 이 값은 그대로다. 주 3일이면
     * 54세트 ÷ 9부위 = 6세트라 주 목표(10)에 닿지 않는다 — 분석 탭이 "권장 이하"가 이어지는 이유로 이 값을 보여 준다.
     */
    public static BigDecimal maxWeeklySetsPerMuscle(int daysPerWeek, RoutinePolicy policy) {
        return BigDecimal.valueOf((long) daysPerWeek * policy.dailyMaxSets())
                .divide(BigDecimal.valueOf(JUDGED_MUSCLES), 1, RoundingMode.HALF_UP);
    }

    /** 모든 판정 부위가 주 목표에 닿으려면 필요한 주당 일수. 기본 설정이면 10 × 9 ÷ 18 = 5일 */
    public static int daysForTarget(RoutinePolicy policy) {
        return Math.ceilDiv(policy.targetWeeklySets() * JUDGED_MUSCLES, policy.dailyMaxSets());
    }
}
