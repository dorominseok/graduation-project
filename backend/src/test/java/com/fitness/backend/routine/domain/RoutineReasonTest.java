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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import com.fitness.backend.routine.domain.RoutineSimulation.Day;
import com.fitness.backend.routine.domain.SlotPlanner.Plan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** "왜 오늘 이 부위인지" — 규칙이 묶음을 정한 단계마다 문장을 고정하고, 문장의 수치가 규칙이 본 값인지 본다. */
class RoutineReasonTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final RoutinePolicy POLICY = RoutinePolicy.DEFAULT;
    private static final List<Imbalance> NONE = List.of();

    private static MuscleState state(MuscleGroup m, String weekly, VolumeVerdict v, Integer daysAgo) {
        return new MuscleState(m, new BigDecimal(weekly), v, daysAgo == null ? null : TODAY.minusDays(daysAgo), false, false);
    }

    private static MuscleState lesser(MuscleGroup m, String weekly, VolumeVerdict v, Integer daysAgo) {
        return new MuscleState(m, new BigDecimal(weekly), v, daysAgo == null ? null : TODAY.minusDays(daysAgo), true, false);
    }

    private static MuscleState bigger(MuscleGroup m, String weekly, VolumeVerdict v, Integer daysAgo) {
        return new MuscleState(m, new BigDecimal(weekly), v, daysAgo == null ? null : TODAY.minusDays(daysAgo), false, true);
    }

    /** 9종 전부 같은 상태로 두고 일부만 바꾼다 */
    private static List<MuscleState> all(VolumeVerdict verdict, String weekly, Integer daysAgo, MuscleState... overrides) {
        Map<MuscleGroup, MuscleState> map = new EnumMap<>(MuscleGroup.class);
        SlotPlanner.SIZE_ORDER.forEach(m -> map.put(m, state(m, weekly, verdict, daysAgo)));
        for (MuscleState o : overrides) {
            map.put(o.muscle(), o);
        }
        return new ArrayList<>(map.values());
    }

    private static String reason(List<MuscleState> states, List<Imbalance> imbalances) {
        return RoutineReason.of(SlotPlanner.decide(states, 5, TODAY, POLICY), imbalances, TODAY, POLICY);
    }

    private static Optional<String> brief(List<MuscleState> states, List<Imbalance> imbalances) {
        return RoutineReason.brief(SlotPlanner.decide(states, 5, TODAY, POLICY), imbalances);
    }

    @Test
    @DisplayName("기록이 없으면 큰 근육 순서가 묶음을 정했다고 말한다 — 수치를 꾸미지 않는다")
    void newcomer() {
        List<MuscleState> states = all(INSUFFICIENT, "0", null);

        assertEquals("아직 기록이 없어서 큰 근육부터 시작해요. 오늘은 밀기 날이에요.", reason(states, NONE));
        assertEquals(Optional.of("기록이 없어 큰 근육부터"), brief(states, NONE));
    }

    @Test
    @DisplayName("모자란 부위가 묶음을 정한 날 — 그 부위의 주당 세트와 판정을 그대로 적는다")
    void lackingLeads() {
        List<MuscleState> states = all(OPTIMAL, "12", 3,
                state(DELT_REAR, "1.8", INSUFFICIENT, 5), state(POSTERIOR, "6", BELOW_RECOMMENDED, 5));

        assertEquals("어깨 후면이 주 1.8세트로 부족해서 오늘은 당기기 날이에요.", reason(states, NONE));
        assertEquals(Optional.of("어깨 후면이 부족"), brief(states, NONE));
    }

    @Test
    @DisplayName("어제 한 묶음이 쉬는 날은 그것부터 말한다 — 오늘·어제·며칠 전을 마지막 운동일로 적는다")
    void restingBundleComesFirst() {
        List<MuscleState> states = all(BELOW_RECOMMENDED, "6", 5,
                state(CHEST, "6", BELOW_RECOMMENDED, 1), state(BICEPS, "6", BELOW_RECOMMENDED, 0));

        assertEquals("어제 한 밀기와 오늘 한 당기기는 쉬고, 앞허벅지가 주 6세트로 권장 이하라서 오늘은 하체 날이에요.",
                reason(states, NONE));
    }

    @Test
    @DisplayName("불균형의 적은 쪽 묶음을 먼저 한 날 — 어느 쪽이 적은지와 그 부위의 수치를 함께 적는다")
    void lesserSideLeads() {
        List<Imbalance> imbalances = List.of(new Imbalance(BalanceSide.LOWER, BalanceSide.UPPER));
        List<MuscleState> states = all(INSUFFICIENT, "0", null,
                bigger(CHEST, "6", BELOW_RECOMMENDED, 1), bigger(DELT_FRONT, "6", BELOW_RECOMMENDED, 1),
                bigger(TRICEPS, "6", BELOW_RECOMMENDED, 1), bigger(BACK, "0", INSUFFICIENT, null),
                bigger(DELT_REAR, "0", INSUFFICIENT, null), bigger(BICEPS, "0", INSUFFICIENT, null),
                lesser(QUADS, "0", INSUFFICIENT, null), lesser(POSTERIOR, "0", INSUFFICIENT, null));

        assertEquals("어제 한 밀기는 쉬고, 상체에 비해 하체가 적고 앞허벅지가 주 0세트로 부족해서 오늘은 하체 날이에요.",
                reason(states, imbalances));
        assertEquals(Optional.of("상체에 비해 하체가 적고 앞허벅지가 부족"), brief(states, imbalances));
    }

    @Test
    @DisplayName("모자란 부위가 없는 날 — 묶음을 정한 최적 부위를 며칠 쉬었는지 적는다. '가장 오래'라고 하지 않는다")
    void optimalLeads() {
        List<MuscleState> states = all(OPTIMAL, "12", 3, state(BACK, "12", OPTIMAL, 6));

        assertEquals("모자란 부위가 없어서 오늘은 6일 쉰 등이 있는 당기기 날이에요.", reason(states, NONE));
        assertEquals(Optional.of("모자란 부위가 없고 등 차례"), brief(states, NONE));
    }

    @Test
    @DisplayName("모자란 부위가 코어뿐이면 '모자란 부위가 없다'고 하지 않는다 — 코어는 묶음을 못 정할 뿐 모자라다")
    void coreLackingIsNotNothingLacking() {
        List<MuscleState> states = all(OPTIMAL, "12", 3, state(BACK, "12", OPTIMAL, 6), state(CORE, "2", INSUFFICIENT, 6));

        assertEquals("오늘은 6일 쉰 등이 있는 당기기 날이에요.", reason(states, NONE));
    }

    @Test
    @DisplayName("쉬는 묶음이 있고 남은 부위가 모두 최적이면 '남은 부위 중에'라고 한정한다")
    void optimalAmongTheRest() {
        List<MuscleState> states = all(OPTIMAL, "12", 4, state(CHEST, "12", OPTIMAL, 1));

        assertEquals("어제 한 밀기는 쉬고, 남은 부위 중에 모자란 곳이 없어서 오늘은 4일 쉰 등이 있는 당기기 날이에요.",
                reason(states, NONE));
    }

    @Test
    @DisplayName("적은 쪽이 최적이어도 그 묶음을 한 날 — 어느 쪽이 적은지만 적는다")
    void optimalLesserLeads() {
        List<Imbalance> imbalances = List.of(new Imbalance(BalanceSide.PULL, BalanceSide.PUSH));
        List<MuscleState> states = all(EXCESSIVE, "25", 3,
                bigger(CHEST, "3", INSUFFICIENT, 3), lesser(BACK, "12", OPTIMAL, 3));

        assertEquals("밀기에 비해 당기기가 적어서 오늘은 당기기 날이에요.", reason(states, imbalances));
    }

    @Test
    @DisplayName("과다 경계 때문에 세트를 줄여 담은 부위가 있으면 그것도 적는다 — 루틴이 작은 이유다")
    void trimmedByExcessiveBoundary() {
        // 주 17세트면 20까지 3세트뿐이다. 세 부위 9세트로는 하한에 못 미쳐 코어도 3세트 담긴다
        List<MuscleState> states = all(OPTIMAL, "17", 3);

        assertEquals("모자란 부위가 없어서 오늘은 3일 쉰 가슴이 있는 밀기 날이에요. "
                + "가슴, 어깨 전·측면, 삼두와 코어는 주 20세트를 넘지 않게 줄여 담았어요.", reason(states, NONE));
        // 남은 자리를 나눠서 3세트가 된 부위는 줄였다고 하지 않는다
        List<MuscleState> shared = all(OPTIMAL, "12", 3,
                state(DELT_REAR, "1.8", INSUFFICIENT, 5), state(CORE, "1.4", INSUFFICIENT, 5));
        assertEquals("어깨 후면이 주 1.8세트로 부족해서 오늘은 당기기 날이에요.", reason(shared, NONE));
    }

    @Test
    @DisplayName("빈 루틴 — 전부 회복 중인 날과 과다 경계에 걸린 날을 다르게 말한다")
    void emptyRoutine() {
        List<MuscleState> recovering = all(INSUFFICIENT, "3", 0);
        List<MuscleState> nearExcessive = all(OPTIMAL, "18", 3, state(CORE, "17.5", OPTIMAL, 3));
        List<MuscleState> mixed = all(OPTIMAL, "19", 3, state(CHEST, "19", OPTIMAL, 1));

        assertEquals("하체, 밀기, 당기기와 코어가 모두 회복 중이라 오늘은 담을 부위가 없어요.", reason(recovering, NONE));
        assertEquals("모든 부위가 이미 주 17.5세트 이상이라, 과다(주 20세트 초과)가 되지 않게 오늘은 담지 않았어요.",
                reason(nearExcessive, NONE));
        assertEquals("어제 한 밀기는 쉬고, 남은 부위는 이미 주 19세트 이상이라, 과다(주 20세트 초과)가 되지 않게 오늘은 담지 않았어요.",
                reason(mixed, NONE));
        assertEquals(Optional.empty(), brief(nearExcessive, NONE));
    }

    @Test
    @DisplayName("코어만 담은 날 — 묶음이 모두 쉬는지, 남은 묶음이 과다인지를 구분한다")
    void coreOnly() {
        List<MuscleState> bundlesRest = all(INSUFFICIENT, "3", 1, state(CORE, "3", INSUFFICIENT, 4));
        List<MuscleState> bundlesExcessive = all(EXCESSIVE, "22", 3, state(CORE, "3", INSUFFICIENT, 4));

        assertEquals("하체, 밀기와 당기기가 모두 회복 중이라 오늘은 코어만 담았어요.", reason(bundlesRest, NONE));
        assertEquals("남은 묶음 부위는 이미 과다(주 20세트 초과)라 오늘은 코어만 담았어요.", reason(bundlesExcessive, NONE));
    }

    private static final Pattern WEEKLY = Pattern.compile("([가-힣·\\s]+?)[이가] 주 ([0-9.]+)세트로 (부족|권장 이하)(?:해서|라서)");
    private static final Pattern RESTED = Pattern.compile("(\\d+)일 쉰 ([가-힣·\\s]+?)[이가] 있는");
    private static final Pattern BUNDLE = Pattern.compile("오늘은 (?:\\d+일 쉰 [가-힣·\\s]+?[이가] 있는 )?(하체|밀기|당기기) 날이에요");

    @Test
    @DisplayName("8주 시뮬레이션의 모든 날 — 문장의 묶음·주당 세트·판정·쉰 날수가 그날 규칙이 본 값과 같다")
    void everySentenceMatchesWhatTheRuleSaw() {
        Stream.of(RoutineSimulationReport.newUser(RoutineSimulationReport.THREE),
                        RoutineSimulationReport.newUser(RoutineSimulationReport.FIVE),
                        RoutineSimulationReport.trainedUser(7), RoutineSimulationReport.trainedUser(11))
                .flatMap(List::stream)
                .forEach(RoutineReasonTest::assertTruthful);
    }

    private static void assertTruthful(Day day) {
        Plan plan = day.plan();
        String sentence = RoutineReason.of(plan, day.imbalances(), day.date(), POLICY);
        MuscleState lead = plan.basis().lead();

        Matcher bundle = BUNDLE.matcher(sentence);
        if (bundle.find()) {
            String label = bundle.group(1);
            assertEquals(plan.basis().bundle().orElseThrow().label(), label, sentence);
            assertTrue(day.slots().stream().anyMatch(s -> MuscleBundle.of(s.muscle())
                    .filter(b -> b.label().equals(label)).isPresent()), "그 묶음의 부위가 루틴에 있다: " + sentence);
        }
        Matcher weekly = WEEKLY.matcher(sentence);
        if (weekly.find()) {
            assertTrue(weekly.group(1).strip().endsWith(lead.muscle().label()), sentence);
            assertEquals(0, new BigDecimal(weekly.group(2)).compareTo(lead.weeklySets()), sentence);
            assertEquals(lead.verdict().label(), weekly.group(3), sentence);
        }
        Matcher rested = RESTED.matcher(sentence);
        if (rested.find()) {
            assertEquals(ChronoUnit.DAYS.between(lead.lastTrained(), day.date()), Long.parseLong(rested.group(1)), sentence);
            assertEquals(lead.muscle().label(), rested.group(2).strip(), sentence);
        }
        // 쉰다고 한 묶음은 루틴에 없다
        plan.basis().restingBundles().keySet().forEach(resting ->
                assertTrue(day.slots().stream().noneMatch(s -> resting.contains(s.muscle())), sentence));
        assertTrue(bundle.reset().find() || day.slots().isEmpty()
                || day.slots().stream().allMatch(s -> s.muscle() == CORE), "문장의 꼴을 못 읽었다: " + sentence);
    }
}
