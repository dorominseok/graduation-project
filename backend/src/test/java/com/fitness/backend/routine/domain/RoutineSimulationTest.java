package com.fitness.backend.routine.domain;

import static com.fitness.backend.routine.domain.RoutineSimulationReport.FIVE;
import static com.fitness.backend.routine.domain.RoutineSimulationReport.FOUR;
import static com.fitness.backend.routine.domain.RoutineSimulationReport.THREE;
import static com.fitness.backend.routine.domain.RoutineSimulationReport.newUser;
import static com.fitness.backend.routine.domain.RoutineSimulationReport.trainedUser;
import static com.fitness.backend.routine.domain.RoutineSimulationReport.weeklyAfter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.RoutineSimulation.Day;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 루틴을 8주 동안 그대로 따라 한 사람에게 날마다 어떤 루틴이 나오는지.
 *
 * <p>실사용 설문의 "운동이 적다"를 따라가 보니 루틴이 작게 나오는 상황이 여섯 가지였다(설계서 3.5). 한 번의
 * {@code plan} 호출로는 안 보이고 몇 주를 돌려야 드러나는 것들이라, 그 상황을 시뮬레이션으로 고정한다.
 */
class RoutineSimulationTest {

    private static final BigDecimal EXCESSIVE_FROM = RoutinePolicy.DEFAULT.optimalThrough();

    @Test
    @DisplayName("① 기록 없는 사람의 첫 세 번 — 큰 근육을 입문 대표 종목 둘로 나눠 4·5·4종목, 총량은 그대로 18세트")
    void firstThreeRoutines() {
        RoutineSimulation names = new RoutineSimulation(RoutinePolicy.DEFAULT);
        List<Day> days = newUser(FIVE).subList(0, 3);

        assertEquals(List.of(18, 18, 18), days.stream().map(Day::sets).toList());
        assertEquals(List.of(4, 5, 4), days.stream().map(Day::exercises).toList());
        assertEquals(List.of(4, 5, 4), days.stream().map(Day::newExercises).toList(), "전부 처음 하는 종목이다");
        assertEquals(List.of("바벨 벤치프레스 3", "푸시업 3", "덤벨 숄더프레스 6", "케이블 푸시다운 6"), items(names, days.get(0)));
        assertEquals(List.of("바벨 스쿼트 3", "덤벨 런지 3", "루마니안 데드리프트 3", "라잉 레그컬 3", "플랭크 6"),
                items(names, days.get(1)));
        assertEquals(List.of("랫풀다운: 와이드 그립 3", "시티드 케이블 로우 3", "페이스풀 6", "덤벨 컬 6"),
                items(names, days.get(2)));
    }

    @Test
    @DisplayName("② 주 5일을 지키면 2~4주차에도 주당 일수가 5로 잡혀 날마다 18세트 — 15세트 세 종목으로 줄지 않는다")
    void steadyFiveDaysInEarlyWeeks() {
        List<Day> early = newUser(FIVE).stream().filter(d -> d.week() >= 2 && d.week() <= 4).toList();

        assertEquals(15, early.size());
        assertTrue(early.stream().allMatch(d -> d.daysPerWeek() == 5), "기록 구간이 주말 전에 끝나도 5일이다");
        assertTrue(early.stream().allMatch(d -> d.sets() == 18));
        assertTrue(early.stream().allMatch(d -> d.exercises() >= 5));
    }

    @Test
    @DisplayName("③ 전 부위가 이미 최적인 사람도 날마다 18세트 — 최적이라고 3세트로 줄이지 않는다")
    void optimalEverywhereKeepsFullRoutine() {
        List<Day> days = trainedUser(7);

        assertEquals(40, days.size());
        assertTrue(days.get(0).slots().stream().allMatch(RoutineSlot::fill), "첫날은 전부 최적 부위를 채운 루틴이다");
        assertTrue(days.stream().allMatch(d -> d.sets() == 18));
    }

    @Test
    @DisplayName("④ 과다 경계에 가까운 사람은 경계까지만 담는다 — 주당 평균 + 오늘 세트가 20을 넘는 날이 하루도 없다")
    void nearExcessiveStaysWithinBoundary() {
        List<Day> days = trainedUser(11);

        // 주 18세트 안팎이면 부위마다 3세트(20까지 남은 만큼)라 첫 루틴은 9세트다. 빈 루틴은 아니다
        assertEquals(9, days.get(0).sets());
        assertTrue(days.get(0).slots().stream().allMatch(s -> s.sets() == 3));
        assertTrue(days.stream().noneMatch(d -> d.sets() == 0));
    }

    @Test
    @DisplayName("⑤ 하체 날에는 코어가 늘 붙어 18세트 — 코어가 최적이어도 12세트로 끝나지 않는다")
    void lowerDayAlwaysHasCore() {
        List<Day> lower = newUser(FIVE).stream().filter(RoutineSimulationReport::lowerDay).toList();

        assertEquals(13, lower.size());
        assertTrue(lower.stream().allMatch(d -> d.has(MuscleGroup.CORE)));
        assertTrue(lower.stream().allMatch(d -> d.sets() == 18));
    }

    @Test
    @DisplayName("⑥ 주 3~4일은 그대로다 — 날마다 18세트를 다 해도 부위당 주 10세트에 못 미친다 (하루 한 묶음의 한계)")
    void lowFrequencyStaysBelowTarget() {
        assertTrue(newUser(THREE).stream().allMatch(d -> d.sets() == 18));
        assertTrue(newUser(FOUR).stream().allMatch(d -> d.sets() == 18));

        assertTrue(weeklyAfter(THREE).stream().allMatch(w -> w.compareTo(new BigDecimal("6.0")) == 0), "주 3일은 부위당 주 6세트");
        assertTrue(weeklyAfter(FOUR).stream().allMatch(w -> w.compareTo(BigDecimal.TEN) < 0), "주 4일은 7.5~9세트");
    }

    @Test
    @DisplayName("어느 상황의 어느 날에도 부위당 3~6세트 · 하루 18세트 이하 · 주당 평균 + 오늘 세트 ≤ 20 (R1·R2·R5)")
    void everyRoutineKeepsTheLimits() {
        Stream.of(newUser(THREE), newUser(FOUR), newUser(FIVE), trainedUser(7), trainedUser(11))
                .flatMap(List::stream)
                .forEach(day -> {
                    assertTrue(day.sets() <= 18, day.toString());
                    for (RoutineSlot slot : day.slots()) {
                        assertTrue(slot.sets() >= 3 && slot.sets() <= 6, day.toString());
                        assertTrue(slot.weeklySets().add(BigDecimal.valueOf(slot.sets())).compareTo(EXCESSIVE_FROM) <= 0,
                                day.toString());
                    }
                });
    }

    private static List<String> items(RoutineSimulation names, Day day) {
        return day.items().stream().map(i -> names.name(i.exerciseId()) + " " + i.sets()).toList();
    }
}
