package com.fitness.backend.routine.domain;

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
}
