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
        return new MuscleState(m, new BigDecimal(weekly), v, last, false, false);
    }

    private static MuscleState lesser(MuscleGroup m, String weekly, VolumeVerdict v) {
        return new MuscleState(m, new BigDecimal(weekly), v, null, true, false);
    }

    private static MuscleState bigger(MuscleGroup m, String weekly, VolumeVerdict v) {
        return new MuscleState(m, new BigDecimal(weekly), v, null, false, true);
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

    private static List<MuscleGroup> muscles(List<RoutineSlot> slots) {
        return slots.stream().map(RoutineSlot::muscle).toList();
    }

    @Test
    @DisplayName("기록이 없으면 주 5회로 보고 큰 근육의 묶음부터 — 첫날은 밀기 3부위 6세트씩 18세트")
    void newUserFirstDay() {
        List<RoutineSlot> slots = SlotPlanner.plan(all(INSUFFICIENT, "0", null), 5, TODAY, POLICY);

        assertEquals(List.of(CHEST, DELT_FRONT, TRICEPS), muscles(slots));
        assertTrue(slots.stream().allMatch(s -> s.sets() == 6));
        assertEquals(18, total(slots));
    }

    @Test
    @DisplayName("다음 날은 어제 한 밀기가 회복 중이라 당기기, 그다음 날은 하체 + 코어 — 묶음이 저절로 돈다")
    void bundlesRotate() {
        LocalDate yesterday = TODAY.minusDays(1);
        LocalDate twoDaysAgo = TODAY.minusDays(2);
        List<MuscleState> secondDay = all(INSUFFICIENT, "0", null,
                state(CHEST, "6", BELOW_RECOMMENDED, yesterday), state(DELT_FRONT, "6", BELOW_RECOMMENDED, yesterday),
                state(TRICEPS, "6", BELOW_RECOMMENDED, yesterday));
        List<MuscleState> thirdDay = all(INSUFFICIENT, "0", null,
                state(CHEST, "6", BELOW_RECOMMENDED, twoDaysAgo), state(DELT_FRONT, "6", BELOW_RECOMMENDED, twoDaysAgo),
                state(TRICEPS, "6", BELOW_RECOMMENDED, twoDaysAgo),
                state(BACK, "6", BELOW_RECOMMENDED, yesterday), state(DELT_REAR, "6", BELOW_RECOMMENDED, yesterday),
                state(BICEPS, "6", BELOW_RECOMMENDED, yesterday));

        assertEquals(List.of(BACK, DELT_REAR, BICEPS), muscles(SlotPlanner.plan(secondDay, 5, TODAY, POLICY)));
        assertEquals(List.of(QUADS, POSTERIOR, CORE), muscles(SlotPlanner.plan(thirdDay, 5, TODAY, POLICY)));
    }

    @Test
    @DisplayName("부족한 부위가 여러 묶음에 흩어져 있으면 맨 앞 부위의 묶음만 하고, 같은 묶음의 최적 부위로 채운다")
    void oneBundlePerDay() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(POSTERIOR, "2.1", INSUFFICIENT, TODAY.minusDays(5)),
                state(DELT_REAR, "1.8", INSUFFICIENT, TODAY.minusDays(5)),
                state(CORE, "1.4", INSUFFICIENT, TODAY.minusDays(5)));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 5, TODAY, POLICY);

        // 코어는 묶음이 없어 건너뛰고, 모자란 양이 더 큰 어깨 후면의 당기기 묶음
        assertEquals(Map.of(DELT_REAR, 6, CORE, 6, BACK, 3, BICEPS, 3), sets(slots));
        assertEquals(List.of(BACK, BICEPS), muscles(slots.stream().filter(RoutineSlot::fill).toList()));
    }

    @Test
    @DisplayName("뒤허벅지를 하는 날은 앞허벅지를 같이 채우고, 그래도 12에 못 미치면 최적인 코어로 채운다")
    void fillWithinBundleThenCore() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(POSTERIOR, "2", INSUFFICIENT, TODAY.minusDays(5)));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 5, TODAY, POLICY);

        assertEquals(Map.of(POSTERIOR, 6, QUADS, 3, CORE, 3), sets(slots));
        assertEquals(12, total(slots));
    }

    @Test
    @DisplayName("채우는 3세트로 과다 경계(20)를 넘는 최적 부위는 채우지 않는다 (R2)")
    void fillNeverOverloads() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(POSTERIOR, "2", INSUFFICIENT, TODAY.minusDays(5)),
                state(QUADS, "19", OPTIMAL, TODAY.minusDays(3)));

        assertFalse(sets(SlotPlanner.plan(states, 5, TODAY, POLICY)).containsKey(QUADS));
    }

    @Test
    @DisplayName("오늘·어제 한 부위는 빠지고, 그저께 한 부위는 들어간다 (48시간)")
    void recoveryExcludesTodayAndYesterday() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(5),
                state(CHEST, "0", INSUFFICIENT, TODAY),
                state(BACK, "0", INSUFFICIENT, TODAY.minusDays(1)),
                state(QUADS, "0", INSUFFICIENT, TODAY.minusDays(2)));

        Map<MuscleGroup, Integer> sets = sets(SlotPlanner.plan(states, 5, TODAY, POLICY));

        assertFalse(sets.containsKey(CHEST));
        assertFalse(sets.containsKey(BACK));
        assertTrue(sets.containsKey(QUADS));
    }

    @Test
    @DisplayName("과다 부위는 하루를 못 채워도 넣지 않는다")
    void excessiveNeverIncluded() {
        List<MuscleState> states = all(EXCESSIVE, "25", null);

        assertTrue(SlotPlanner.plan(states, 5, TODAY, POLICY).isEmpty());
    }

    @Test
    @DisplayName("불균형의 적은 쪽이 모자라면 판정이 더 나쁜 반대쪽보다 그 묶음을 먼저 한다 (R3)")
    void lesserSideBundleFirst() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                bigger(CHEST, "3", INSUFFICIENT),
                lesser(BACK, "8", BELOW_RECOMMENDED));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 5, TODAY, POLICY);

        assertEquals(BACK, slots.get(0).muscle());
        assertFalse(sets(slots).containsKey(CHEST));
    }

    @Test
    @DisplayName("많은 쪽에만 속한 부위는 모자라도 묶음을 정하지 못한다 — 적은 쪽이 최적이어도 그쪽을 채운다")
    void biggerSideNeverLeads() {
        List<MuscleState> states = all(EXCESSIVE, "25", null,
                bigger(CHEST, "3", INSUFFICIENT),
                new MuscleState(BACK, new BigDecimal("12"), OPTIMAL, null, true, false));

        assertEquals(List.of(BACK), muscles(SlotPlanner.plan(states, 5, TODAY, POLICY)));
    }

    @Test
    @DisplayName("같은 판정·같은 부족량이면 오래 쉰 부위의 묶음이다")
    void olderLastTrainedFirst() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(4),
                state(CHEST, "0", INSUFFICIENT, TODAY.minusDays(3)),
                state(BICEPS, "0", INSUFFICIENT, TODAY.minusDays(9)));

        List<RoutineSlot> slots = SlotPlanner.plan(states, 5, TODAY, POLICY);

        assertEquals(BICEPS, slots.get(0).muscle());
        assertFalse(sets(slots).containsKey(CHEST));
    }

    @Test
    @DisplayName("어제 묶음의 한 부위만 했어도 그 묶음은 통째로 쉰다 — 미는 동작이 이틀 이어지지 않게")
    void wholeBundleRests() {
        List<MuscleState> states = all(INSUFFICIENT, "0", null,
                state(CHEST, "3", INSUFFICIENT, TODAY.minusDays(1)));

        List<MuscleGroup> today = muscles(SlotPlanner.plan(states, 5, TODAY, POLICY));

        assertEquals(List.of(BACK, DELT_REAR, BICEPS), today);
    }

    @Test
    @DisplayName("하한에 못 미치면 다른 묶음을 끌어오지 않고 담은 부위의 세트를 올린다")
    void topUpWithinBundle() {
        List<MuscleState> states = all(OPTIMAL, "12", TODAY.minusDays(3),
                state(QUADS, "5", BELOW_RECOMMENDED, TODAY.minusDays(3)),
                state(POSTERIOR, "5", BELOW_RECOMMENDED, TODAY.minusDays(3)),
                state(CORE, "19", OPTIMAL, TODAY.minusDays(3)));

        // 주 7회면 권장 이하는 4세트씩 = 8. 코어는 채우면 과다라 못 넣고, 두 부위를 6세트까지 올린다
        List<RoutineSlot> slots = SlotPlanner.plan(states, 7, TODAY, POLICY);

        assertEquals(Map.of(QUADS, 6, POSTERIOR, 6), sets(slots));
    }

    @Test
    @DisplayName("세트 = 주 목표 ÷ (주당 일수 ÷ 묶음 셋) — 주 5회 6, 주 7회 부족은 5·권장 이하는 4, 3에서 받친다")
    void setsFollowDaysPerWeek() {
        List<MuscleState> lacking = all(INSUFFICIENT, "0", null);
        List<MuscleState> below = all(BELOW_RECOMMENDED, "5", null);

        assertEquals(6, SlotPlanner.plan(lacking, 5, TODAY, POLICY).get(0).sets());
        assertEquals(6, SlotPlanner.plan(lacking, 2, TODAY, POLICY).get(0).sets());
        assertEquals(5, SlotPlanner.plan(lacking, 7, TODAY, POLICY).get(0).sets());
        assertEquals(4, SlotPlanner.plan(below, 7, TODAY, POLICY).get(0).sets());
        assertEquals(3, SlotPlanner.plan(lacking, 20, TODAY, POLICY).get(0).sets());
    }

    @Test
    @DisplayName("하루 18세트를 넘으면 마지막 부위는 남은 만큼만 담는다")
    void lastSlotTakesWhatIsLeft() {
        List<RoutineSlot> slots = SlotPlanner.plan(all(INSUFFICIENT, "0", null), 6, TODAY, POLICY);

        assertEquals(Map.of(CHEST, 5, DELT_FRONT, 5, TRICEPS, 5, CORE, 3), sets(slots));
        assertEquals(18, total(slots));
    }
}
