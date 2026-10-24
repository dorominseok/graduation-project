package com.fitness.backend.routine.domain;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.analysis.domain.MuscleGroup.CHEST;
import static com.fitness.backend.analysis.domain.MuscleGroup.CORE;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_FRONT;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_REAR;
import static com.fitness.backend.analysis.domain.MuscleGroup.POSTERIOR;
import static com.fitness.backend.analysis.domain.MuscleGroup.QUADS;
import static com.fitness.backend.analysis.domain.MuscleGroup.TRICEPS;
import static com.fitness.backend.analysis.domain.VolumeVerdict.BELOW_RECOMMENDED;
import static com.fitness.backend.analysis.domain.VolumeVerdict.EXCESSIVE;
import static com.fitness.backend.analysis.domain.VolumeVerdict.INSUFFICIENT;
import static com.fitness.backend.analysis.domain.VolumeVerdict.OPTIMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 오늘 할 부위와 세트 — 대표 상황의 결과를 그대로 고정한다. */
class SlotPlannerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final RoutinePolicy POLICY = RoutinePolicy.DEFAULT;

    private static MuscleState state(MuscleGroup m, String weekly, VolumeVerdict v, LocalDate last) {
        return new MuscleState(m, new BigDecimal(weekly), v, last, false);
    }

    private static MuscleState lesser(MuscleGroup m, String weekly, VolumeVerdict v) {
        return new MuscleState(m, new BigDecimal(weekly), v, null, true);
    }

    /** 9종 전부 기본 상태로 두고 일부만 바꾼다 */
    private static List<MuscleState> all(VolumeVerdict verdict, String weekly, LocalDate last, MuscleState... overrides) {
        Map<MuscleGroup, MuscleState> map = SlotPlanner.SIZE_ORDER.stream()
                .collect(Collectors.toMap(m -> m, m -> state(m, weekly, verdict, last)));
        for (MuscleState o : overrides) {
            map.put(o.muscle(), o);
        }
        return new ArrayList<>(map.values());
    }

    private static Map<MuscleGroup, Integer> sets(List<RoutineSlot> slots) {
        return slots.stream().collect(Collectors.toMap(RoutineSlot::muscle, RoutineSlot::sets));
    }

    private static int total(List<RoutineSlot> slots) {
        return slots.stream().mapToInt(RoutineSlot::sets).sum();
    }

    @Test
    @DisplayName("기록이 없으면 큰 근육 순으로 4세트씩, 18을 넘기 전까지 — 첫날 16세트 (2.6)")
    void newUserFirstDay() {
        List<RoutineSlot> slots = SlotPlanner.plan(all(INSUFFICIENT, "0", null), 3, TODAY, POLICY);

        assertEquals(List.of(CHEST, BACK, QUADS, POSTERIOR), slots.stream().map(RoutineSlot::muscle).toList());
        assertTrue(slots.stream().allMatch(s -> s.sets() == 4));
        assertEquals(16, total(slots));
    }

    @Test
    @DisplayName("다음 날은 어제 한 큰 근육이 회복 중이라 작은 근육이 온다 — 분할이 저절로 생긴다 (2.6)")
    void newUserSecondDay() {
        LocalDate yesterday = TODAY.minusDays(1);
        List<MuscleState> states = all(INSUFFICIENT, "0", null,
                state(CHEST, "0", INSUFFICIENT, yesterday), state(BACK, "0", INSUFFICIENT, yesterday),
                state(QUADS, "0", INSUFFICIENT, yesterday), state(POSTERIOR, "0", INSUFFICIENT, yesterday));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 3, TODAY, POLICY);

        assertEquals(List.of(DELT_FRONT, DELT_REAR, TRICEPS, BICEPS), slots.stream().map(RoutineSlot::muscle).toList());
    }

    @Test
    @DisplayName("설계서 3.3 예시 — 주 3회, 어깨 후면 4 · 삼두 4 · 가슴 3 · 앞허벅지 3 = 14, 등(최적)은 빠진다")
    void designExample() {
        LocalDate longAgo = TODAY.minusDays(10);
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(DELT_REAR, "0", INSUFFICIENT, null),
                state(TRICEPS, "2", INSUFFICIENT, longAgo),
                state(CHEST, "4", BELOW_RECOMMENDED, longAgo),
                state(QUADS, "6", BELOW_RECOMMENDED, longAgo));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 3, TODAY, POLICY);

        assertEquals(Map.of(DELT_REAR, 4, TRICEPS, 4, CHEST, 3, QUADS, 3), sets(slots));
        assertEquals(14, total(slots));
        assertEquals(List.of(DELT_REAR, TRICEPS, CHEST, QUADS), slots.stream().map(RoutineSlot::muscle).toList());
    }

    @Test
    @DisplayName("오늘·어제 한 부위는 빠지고, 그저께 한 부위는 들어간다 (48시간)")
    void recoveryExcludesTodayAndYesterday() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(5),
                state(CHEST, "0", INSUFFICIENT, TODAY),
                state(BACK, "0", INSUFFICIENT, TODAY.minusDays(1)),
                state(QUADS, "0", INSUFFICIENT, TODAY.minusDays(2)));

        Map<MuscleGroup, Integer> sets = sets(SlotPlanner.plan(states, 3, TODAY, POLICY));

        assertFalse(sets.containsKey(CHEST));
        assertFalse(sets.containsKey(BACK));
        assertTrue(sets.containsKey(QUADS));
    }

    @Test
    @DisplayName("과다 부위는 하루를 못 채워도 넣지 않는다")
    void excessiveNeverIncluded() {
        List<MuscleState> states = all(EXCESSIVE, "25", null);

        assertTrue(SlotPlanner.plan(states, 3, TODAY, POLICY).isEmpty());
    }

    @Test
    @DisplayName("같은 판정이면 불균형의 적은 쪽이 먼저지만, 판정은 뛰어넘지 않는다 (2.3)")
    void lesserSideWithinVerdictOnly() {
        LocalDate recent = TODAY.minusDays(1);
        List<MuscleState> states = all(OPTIMAL, "12", recent,
                state(CHEST, "5", BELOW_RECOMMENDED, null),     // 모자란 양 5
                lesser(BACK, "8", BELOW_RECOMMENDED),           // 모자란 양 2지만 적은 쪽
                state(TRICEPS, "3", INSUFFICIENT, null));       // 부족이 권장 이하보다 먼저

        List<MuscleGroup> order = SlotPlanner.plan(states, 3, TODAY, POLICY).stream().map(RoutineSlot::muscle).toList();

        assertEquals(List.of(TRICEPS, BACK, CHEST), order);
    }

    @Test
    @DisplayName("같은 판정·같은 부족량이면 오래 쉰 부위가 먼저다")
    void olderLastTrainedFirst() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(1),
                state(CHEST, "0", INSUFFICIENT, TODAY.minusDays(3)),
                state(BICEPS, "0", INSUFFICIENT, TODAY.minusDays(9)));

        List<MuscleGroup> order = SlotPlanner.plan(states, 3, TODAY, POLICY).stream().map(RoutineSlot::muscle).toList();

        assertEquals(List.of(BICEPS, CHEST), order.subList(0, 2));
    }

    @Test
    @DisplayName("12세트에 못 미치면 최적 부위 중 가장 오래 쉰 부위로 3세트씩 채운다 (2.5)")
    void fillWithLongestRestedOptimal() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(CORE, "0", INSUFFICIENT, null),
                state(BACK, "12", OPTIMAL, TODAY.minusDays(8)),
                state(QUADS, "12", OPTIMAL, TODAY.minusDays(6)),
                state(CHEST, "12", OPTIMAL, TODAY.minusDays(1)));   // 회복 중이라 채우지 않는다

        List<RoutineSlot> slots = SlotPlanner.plan(states, 3, TODAY, POLICY);

        assertEquals(CORE, slots.get(0).muscle());
        assertEquals(BACK, slots.get(1).muscle());
        assertEquals(QUADS, slots.get(2).muscle());
        assertTrue(slots.stream().filter(RoutineSlot::fill).allMatch(s -> s.sets() == 3));
        assertTrue(total(slots) >= 12);
        assertFalse(sets(slots).containsKey(CHEST));
    }

    @Test
    @DisplayName("주당 일수로 세트가 바뀐다 — 주 2회 5세트, 주 1회는 6에서 자르고, 주 7회는 2에서 받친다")
    void setsFollowDaysPerWeek() {
        List<MuscleState> states = all(INSUFFICIENT, "0", null);

        assertEquals(5, SlotPlanner.plan(states, 2, TODAY, POLICY).get(0).sets());
        assertEquals(6, SlotPlanner.plan(states, 1, TODAY, POLICY).get(0).sets());
        assertEquals(2, SlotPlanner.plan(states, 7, TODAY, POLICY).get(0).sets());
    }
}
