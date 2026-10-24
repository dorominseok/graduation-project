package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.MuscleGroup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최종 루틴 검증 R1~R5. 「루틴생성_로직_설계서」 6.2 — 기능명세서 2.3의 검사 항목을 정량화한 것.
 *
 * <p>규칙이 세트를 정하므로 대부분 구성상 통과한다. 그래도 매번 검사해 통과율을 남긴다 —
 * 규칙에 구멍이 있으면 실사용 데이터에서 드러난다. 실패해도 루틴은 준다.
 */
public final class RoutineCheck {

    private RoutineCheck() {
    }

    /**
     * @param imbalances 불균형인 쌍마다 (적은 쪽, 많은 쪽). 없으면 빈 목록
     * @param candidates 슬롯마다 후보 id
     * @return 실패한 규칙 ID. 비어 있으면 통과
     */
    public static List<String> check(List<RoutineSlot> slots,
                                     List<PlannedItem> items,
                                     List<Imbalance> imbalances,
                                     Map<MuscleGroup, Set<Long>> candidates,
                                     RoutinePolicy policy) {
        List<String> failures = new ArrayList<>();

        boolean r1 = slots.stream().allMatch(s ->
                s.sets() >= policy.perMuscleMinSets() && s.sets() <= policy.perMuscleMaxSets());
        if (!r1) {
            failures.add("R1");
        }

        boolean r2 = slots.stream().allMatch(s ->
                s.weeklySets().add(BigDecimal.valueOf(s.sets())).compareTo(policy.optimalThrough()) <= 0);
        if (!r2) {
            failures.add("R2");
        }

        Map<MuscleGroup, Integer> setsByMuscle = new EnumMap<>(MuscleGroup.class);
        items.forEach(i -> setsByMuscle.merge(i.muscle(), i.sets(), Integer::sum));
        boolean r3 = imbalances.stream().allMatch(im ->
                sum(setsByMuscle, im.lesser()) >= sum(setsByMuscle, im.bigger()));
        if (!r3) {
            failures.add("R3");
        }

        Map<MuscleGroup, Integer> planned = slots.stream()
                .collect(Collectors.toMap(RoutineSlot::muscle, RoutineSlot::sets, Integer::sum,
                        () -> new EnumMap<>(MuscleGroup.class)));
        boolean inCandidates = items.stream().allMatch(i ->
                candidates.getOrDefault(i.muscle(), Set.of()).contains(i.exerciseId()));
        // 후보가 하나도 없는 슬롯은 항목을 만들 수 없어 비어 있다. 그 슬롯만 비교에서 뺀다
        boolean setsMatch = slots.stream().allMatch(s ->
                candidates.getOrDefault(s.muscle(), Set.of()).isEmpty()
                        || planned.get(s.muscle()).equals(setsByMuscle.getOrDefault(s.muscle(), 0)));
        if (!inCandidates || !setsMatch) {
            failures.add("R4");
        }

        int total = items.stream().mapToInt(PlannedItem::sets).sum();
        if (total > policy.dailyMaxSets()) {
            failures.add("R5");
        }
        return List.copyOf(failures);
    }

    private static int sum(Map<MuscleGroup, Integer> setsByMuscle, BalanceSide side) {
        return side.components().stream().mapToInt(m -> setsByMuscle.getOrDefault(m, 0)).sum();
    }

    /** 불균형인 쌍. 루틴이 적은 쪽 세트를 많은 쪽보다 적게 두면 불균형을 키운다(R3) */
    public record Imbalance(BalanceSide lesser, BalanceSide bigger) {
    }
}
