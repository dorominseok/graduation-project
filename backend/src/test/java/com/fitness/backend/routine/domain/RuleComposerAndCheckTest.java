package com.fitness.backend.routine.domain;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.CHEST;
import static com.fitness.backend.analysis.domain.MuscleGroup.CORE;
import static com.fitness.backend.analysis.domain.MuscleGroup.TRICEPS;
import static com.fitness.backend.analysis.domain.VolumeVerdict.BELOW_RECOMMENDED;
import static com.fitness.backend.analysis.domain.VolumeVerdict.INSUFFICIENT;
import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 규칙 선택(7장)과 최종 검증 R1~R5(6.2). */
class RuleComposerAndCheckTest {

    private static RoutineSlot slot(MuscleGroup m, int sets, String weekly) {
        return new RoutineSlot(m, sets, new BigDecimal(weekly), INSUFFICIENT, false);
    }

    private static ExerciseOption ex(long id, String group) {
        return new ExerciseOption(id, group, id % 2 == 0 ? BARBELL : CABLE, false, 0, true, null);
    }

    @Test
    @DisplayName("규칙 선택은 슬롯마다 후보 1순위, 순서는 큰 근육 → 작은 근육 → 코어")
    void composesFirstCandidateInSizeOrder() {
        List<RoutineSlot> slots = List.of(slot(CORE, 3, "0"), slot(TRICEPS, 4, "0"), slot(CHEST, 4, "0"));
        Map<MuscleGroup, List<ExerciseOption>> candidates = Map.of(
                CORE, List.of(ex(30, "a")), TRICEPS, List.of(ex(20, "b")), CHEST, List.of(ex(10, "c"), ex(11, "d")));

        List<PlannedItem> items = RuleComposer.compose(slots, candidates);

        assertEquals(List.of(10L, 20L, 30L), items.stream().map(PlannedItem::exerciseId).toList());
    }

    @Test
    @DisplayName("5세트 이상이고 계열이 다른 후보가 있으면 두 종목으로 나눈다 — 앞 종목이 하나 더")
    void splitsLargeSlots() {
        List<RoutineSlot> slots = List.of(slot(CHEST, 5, "0"));
        Map<MuscleGroup, List<ExerciseOption>> candidates = Map.of(
                CHEST, List.of(ex(10, "벤치"), ex(11, "벤치"), ex(12, "플라이")));

        List<PlannedItem> items = RuleComposer.compose(slots, candidates);

        assertEquals(List.of(new PlannedItem(CHEST, 10L, 3), new PlannedItem(CHEST, 12L, 2)), items);
        assertArrayEquals(new int[] {2, 2}, RuleComposer.split(4, 2));
    }

    @Test
    @DisplayName("설명은 그날 담긴 부위로 고른다 — 코어가 없는 날에 코어를 말하지 않는다")
    void commentFollowsComposition() {
        PlannedItem chest = new PlannedItem(CHEST, 10L, 4);
        PlannedItem back = new PlannedItem(BACK, 20L, 4);
        PlannedItem triceps = new PlannedItem(TRICEPS, 30L, 4);
        PlannedItem core = new PlannedItem(CORE, 40L, 3);

        assertEquals("큰 근육부터 시작해서 작은 근육과 코어로 마무리하는 순서예요.",
                RuleComposer.comment(List.of(chest, triceps, core)));
        assertEquals("큰 근육부터 시작해서 작은 근육으로 마무리하는 순서예요.",
                RuleComposer.comment(List.of(chest, triceps)));
        assertEquals("큰 근육부터 시작해서 코어로 마무리하는 순서예요.", RuleComposer.comment(List.of(back, core)));
        assertEquals("작은 근육부터 하고 코어로 마무리하는 순서예요.", RuleComposer.comment(List.of(triceps, core)));
        assertEquals("큰 근육 위주로, 큰 부위부터 하는 순서예요.", RuleComposer.comment(List.of(chest, back)));
        assertEquals("작은 근육 위주로 짠 날이에요.", RuleComposer.comment(List.of(triceps)));
        assertEquals("코어를 채우는 날이에요.", RuleComposer.comment(List.of(core)));
        assertEquals(RuleComposer.REST_COMMENT, RuleComposer.comment(List.of()));
    }

    @Test
    @DisplayName("큰 근육 → 작은 근육 순서가 아니면(LLM 순서) 순서를 말하지 않는다")
    void commentNeutralWhenOrderDiffers() {
        List<PlannedItem> llmOrder = List.of(new PlannedItem(TRICEPS, 30L, 4), new PlannedItem(CHEST, 10L, 4));

        assertEquals(RuleComposer.NEUTRAL_COMMENT, RuleComposer.comment(llmOrder));
    }

    @Test
    @DisplayName("규칙 루틴은 R1~R5를 모두 통과한다")
    void ruleRoutinePasses() {
        List<RoutineSlot> slots = List.of(slot(CHEST, 4, "2"), slot(BACK, 4, "2"));
        List<PlannedItem> items = List.of(new PlannedItem(CHEST, 10L, 4), new PlannedItem(BACK, 20L, 4));

        List<String> failures = RoutineCheck.check(slots, items, List.of(),
                Map.of(CHEST, Set.of(10L), BACK, Set.of(20L)), RoutinePolicy.DEFAULT);

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    @DisplayName("과다가 되는 세트(R2)와 불균형을 키우는 배분(R3)을 잡는다")
    void catchesOverloadAndWorseningImbalance() {
        List<RoutineSlot> slots = List.of(
                new RoutineSlot(CHEST, 6, new BigDecimal("18"), BELOW_RECOMMENDED, false),
                slot(BACK, 2, "1"));
        List<PlannedItem> items = List.of(new PlannedItem(CHEST, 10L, 6), new PlannedItem(BACK, 20L, 2));
        // 밀기가 이미 많은데 오늘도 밀기를 더 많이 한다
        List<Imbalance> imbalances = List.of(new Imbalance(BalanceSide.PULL, BalanceSide.PUSH));

        List<String> failures = RoutineCheck.check(slots, items, imbalances,
                Map.of(CHEST, Set.of(10L), BACK, Set.of(20L)), RoutinePolicy.DEFAULT);

        assertEquals(List.of("R2", "R3"), failures);
    }

    @Test
    @DisplayName("후보 밖 종목이나 세트가 다르면 R4")
    void catchesOutOfCandidates() {
        List<RoutineSlot> slots = List.of(slot(CHEST, 4, "0"));
        List<PlannedItem> items = List.of(new PlannedItem(CHEST, 99L, 3));

        assertEquals(List.of("R4"), RoutineCheck.check(slots, items, List.of(),
                Map.of(CHEST, Set.of(10L)), RoutinePolicy.DEFAULT));
    }
}
