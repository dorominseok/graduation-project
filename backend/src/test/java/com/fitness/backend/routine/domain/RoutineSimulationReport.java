package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.RoutineSimulation.Day;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 여섯 상황을 8주 시뮬레이션으로 찍는다. 규칙을 바꾸기 전·후에 같은 코드를 돌려 PR 본문의 표를 만들었다.
 * 값은 {@link RoutineSimulationTest}가 고정한다.
 */
final class RoutineSimulationReport {

    static final Set<DayOfWeek> THREE = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    static final Set<DayOfWeek> FOUR = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    static final Set<DayOfWeek> FIVE = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    static final int WEEKS = 8;

    private RoutineSimulationReport() {
    }

    /** 기록 없이 시작해 루틴만 따라 한 사람 */
    static List<Day> newUser(Set<DayOfWeek> weekdays) {
        return new RoutineSimulation(RoutinePolicy.DEFAULT).follow(RoutineSimulation.START, WEEKS, weekdays);
    }

    /** 4주 동안 혼자 운동해 온 사람(주 5일, 부위마다 한 번에 {@code setsPerSession}세트)이 그 뒤로 루틴을 따른다 */
    static List<Day> trainedUser(int setsPerSession) {
        RoutineSimulation simulation = new RoutineSimulation(RoutinePolicy.DEFAULT);
        simulation.seedOwnTraining(RoutineSimulation.START.minusWeeks(4), 4, FIVE, setsPerSession);
        return simulation.follow(RoutineSimulation.START, WEEKS, FIVE);
    }

    /** 기록 없이 시작한 사람의 8주 뒤 부위별 주당 평균 */
    static Collection<BigDecimal> weeklyAfter(Set<DayOfWeek> weekdays) {
        RoutineSimulation simulation = new RoutineSimulation(RoutinePolicy.DEFAULT);
        simulation.follow(RoutineSimulation.START, WEEKS, weekdays);
        return simulation.weeklySets(RoutineSimulation.START.plusWeeks(WEEKS).minusDays(1)).values();
    }

    static boolean lowerDay(Day day) {
        return day.has(MuscleGroup.QUADS) || day.has(MuscleGroup.POSTERIOR);
    }

    public static void main(String[] args) {
        RoutineSimulation names = new RoutineSimulation(RoutinePolicy.DEFAULT);
        List<Day> five = newUser(FIVE);

        System.out.println("## 1. 기록 없는 사람의 첫 세 번 (주 5일)");
        five.subList(0, 3).forEach(day -> System.out.printf("- %s — %d세트 · %d종목(새 동작 %d): %s%n", muscles(day),
                day.sets(), day.exercises(), day.newExercises(), day.items().stream()
                        .map(i -> names.name(i.exerciseId()) + " ×" + i.sets()).collect(Collectors.joining(", "))));

        System.out.println("\n## 2. 주 5일을 지키는 사람의 2~4주차 (15일)");
        List<Day> early = five.stream().filter(d -> d.week() >= 2 && d.week() <= 4).toList();
        System.out.println("- 날마다 세트/종목(주당 일수): " + early.stream()
                .map(d -> d.sets() + "/" + d.exercises() + "(" + d.daysPerWeek() + ")").collect(Collectors.joining(" ")));
        summary(early);

        System.out.println("\n## 3. 전 부위가 최적(주 약 12세트)인 사람 — 4주 혼자 운동한 뒤 8주");
        trained(trainedUser(7));

        System.out.println("\n## 4. 전 부위가 주 약 18세트인 사람 — 4주 혼자 운동한 뒤 8주");
        trained(trainedUser(11));

        System.out.println("\n## 5. 하체 날 (주 5일, 8주)");
        List<Day> lower = five.stream().filter(RoutineSimulationReport::lowerDay).toList();
        System.out.printf("- 하체 날 %d일 중 12세트 이하 %d일, 코어가 붙은 날 %d일%n", lower.size(),
                lower.stream().filter(d -> d.sets() <= 12).count(), lower.stream().filter(d -> d.has(MuscleGroup.CORE)).count());
        summary(lower);

        System.out.println("\n## 6. 주 3일 · 4일 (8주)");
        for (Set<DayOfWeek> weekdays : List.of(THREE, FOUR)) {
            List<Day> days = newUser(weekdays);
            System.out.printf("- 주 %d일: 8주 뒤 부위별 주당 평균 %s%n", weekdays.size(), range(weeklyAfter(weekdays)));
            summary(days);
        }

        System.out.println("\n## 왜 오늘 이 부위인지 — 주 5일 첫 2주, 전 부위가 주 약 18세트인 사람의 첫 주");
        Stream.concat(five.stream().limit(10), trainedUser(11).stream().limit(5)).forEach(day -> System.out.printf(
                "- %s → %s%n", muscles(day), RoutineReason.of(day.plan(), day.imbalances(), day.date(), RoutinePolicy.DEFAULT)));

        System.out.println("\n## 전체 — 주 5일 8주");
        summary(five);
        System.out.println("- 8주 뒤 부위별 주당 평균 " + range(weeklyAfter(FIVE)));
        System.out.println("- 종목 수 분포: " + five.stream()
                .collect(Collectors.groupingBy(Day::exercises, java.util.TreeMap::new, Collectors.counting())));
    }

    private static void trained(List<Day> days) {
        Day first = days.get(0);
        System.out.printf("- 첫 루틴: %s — %d세트 · %d종목%n", muscles(first), first.sets(), first.exercises());
        System.out.printf("- 빈 루틴 %d일 / %d일%n", days.stream().filter(d -> d.sets() == 0).count(), days.size());
        summary(days);
    }

    private static void summary(List<Day> days) {
        System.out.printf("- 하루 평균 %.1f세트 · %.1f종목, 18세트 미만 %d일, 3종목 이하 %d일 (전체 %d일)%n",
                days.stream().mapToInt(Day::sets).average().orElse(0),
                days.stream().mapToInt(Day::exercises).average().orElse(0),
                days.stream().filter(d -> d.sets() < 18).count(),
                days.stream().filter(d -> d.exercises() <= 3).count(), days.size());
    }

    private static String muscles(Day day) {
        return day.slots().isEmpty() ? "빈 루틴" : day.slots().stream()
                .map(s -> s.muscle().label() + " " + s.sets() + (s.fill() ? "(채움)" : "")).collect(Collectors.joining(" · "));
    }

    private static String range(Collection<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElseThrow() + "~"
                + values.stream().max(BigDecimal::compareTo).orElseThrow();
    }
}
