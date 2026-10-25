package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 오늘 할 부위와 세트를 정한다.
 *
 * <p>DB·스프링에 의존하지 않는 순수 계산이다. 분석 판정처럼 경계값을 단위 테스트로
 * 고정한다 — 루틴이 무엇을 근거로 나왔는지를 이 클래스 하나로 설명할 수 있어야 한다.
 *
 * <pre>
 *   회복   = 오늘·어제 한 부위가 있는 묶음은 통째로 뺀다. 코어는 부위만
 *   후보   = 부족 · 권장 이하 (과다·최적 제외)
 *   정렬   = 판정 → 불균형 적은 쪽 → 모자란 양(큰 순) → 마지막 운동일(오래된 순) → 크기 순
 *   묶음   = 맨 앞 부위의 묶음(하체·밀기·당기기). 불균형이면 적은 쪽이 먼저, 많은 쪽은 마지막
 *   세트   = 주 목표 ÷ 묶음이 돌아오는 주당 횟수(주당 일수 ÷ 묶음 수). 부족은 올림, 권장 이하는 내림, 3~6으로 자름
 *   담기   = 묶음의 부족 부위 → 코어 → 묶음의 최적 부위(3세트씩). 하루 상한(18)을 넘으면 남은 만큼만
 *   채우기 = 그래도 하한(12)에 못 미치면 최적인 코어, 그다음 담은 부위의 세트를 하나씩 올린다
 * </pre>
 *
 * <p>다른 묶음의 부족 부위는 다음 날로 넘긴다. 매일 맨 앞 부위부터 다시 고르므로 묶음이 돌아간다.
 */
public final class SlotPlanner {

    /**
     * 모든 조건이 같을 때의 순서 — 큰 근육부터, 코어가 마지막.
     * 기록이 없는 사람에게는 이 순서가 첫 루틴을 정한다.
     */
    public static final List<MuscleGroup> SIZE_ORDER = List.of(
            MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.POSTERIOR,
            MuscleGroup.DELT_FRONT, MuscleGroup.DELT_REAR, MuscleGroup.TRICEPS, MuscleGroup.BICEPS,
            MuscleGroup.CORE);

    private SlotPlanner() {
    }

    /**
     * @param states      판정 부위 9종의 상태. 판정하지 않는 부위(종아리·전완)는 무시한다
     * @param daysPerWeek 주당 운동 일수. 모르면 부르는 쪽이 기본값을 넘긴다
     * @param today       루틴 날짜. 회복 제외의 기준
     */
    public static List<RoutineSlot> plan(List<MuscleState> states, double daysPerWeek, LocalDate today,
                                         RoutinePolicy policy) {
        if (daysPerWeek <= 0) {
            throw new IllegalArgumentException("주당 운동 일수는 0보다 커야 한다: " + daysPerWeek);
        }
        double base = policy.targetWeeklySets() * MuscleBundle.values().length / daysPerWeek;
        BigDecimal target = BigDecimal.valueOf(policy.targetWeeklySets());

        // 어제 가슴만 했어도 오늘 삼두를 하면 미는 동작이 이틀 이어진다
        Set<MuscleBundle> resting = states.stream()
                .filter(s -> s.muscle().judged() && recovering(s, today, policy))
                .flatMap(s -> MuscleBundle.of(s.muscle()).stream())
                .collect(Collectors.toSet());
        List<MuscleState> judged = states.stream()
                .filter(s -> s.muscle().judged())
                .filter(s -> !recovering(s, today, policy))
                .filter(s -> MuscleBundle.of(s.muscle()).filter(resting::contains).isEmpty())
                .toList();

        List<MuscleState> lacking = judged.stream()
                .filter(s -> s.verdict() == VolumeVerdict.INSUFFICIENT
                        || s.verdict() == VolumeVerdict.BELOW_RECOMMENDED)
                .sorted(Comparator
                        .comparing((MuscleState s) -> s.verdict().ordinal())
                        .thenComparing(s -> !s.lesserSide())
                        .thenComparing(s -> target.subtract(s.weeklySets()), Comparator.reverseOrder())
                        .thenComparing(SlotPlanner::lastTrainedOrder)
                        .thenComparing(s -> SIZE_ORDER.indexOf(s.muscle())))
                .toList();
        List<MuscleState> optimal = judged.stream()
                .filter(s -> s.verdict() == VolumeVerdict.OPTIMAL)
                .sorted(Comparator
                        .comparing(SlotPlanner::lastTrainedOrder)
                        .thenComparing(s -> SIZE_ORDER.indexOf(s.muscle())))
                .toList();

        Optional<MuscleBundle> bundle = todayBundle(lacking, optimal);
        Predicate<MuscleState> inBundle = s -> bundle.filter(b -> b.contains(s.muscle())).isPresent();
        Predicate<MuscleState> core = s -> s.muscle() == MuscleGroup.CORE;
        int fillSets = clamp(Math.min((int) Math.floor(base + 1e-9), policy.fillMaxSets()), policy);

        Day day = new Day(policy);
        Stream.concat(lacking.stream().filter(inBundle), lacking.stream().filter(core)).forEach(state -> {
            int sets = state.verdict() == VolumeVerdict.INSUFFICIENT
                    ? clamp((int) Math.ceil(base - 1e-9), policy)
                    : clamp((int) Math.floor(base + 1e-9), policy);
            day.add(state, sets, false);
        });
        optimal.stream().filter(inBundle).forEach(state -> day.fill(state, fillSets));
        optimal.stream().filter(core).forEach(state -> {
            if (day.belowMin()) {
                day.fill(state, fillSets);
            }
        });
        day.topUp();
        return day.slots();
    }

    /**
     * 그날 묶음. 불균형의 적은 쪽 부위가 판정보다 먼저고, 많은 쪽에만 속한 부위는 가장 나중이다 — 많은 쪽
     * 묶음을 하면 불균형이 커진다(R3). 부족한 부위가 코어뿐이거나 없으면 가장 오래 쉰 최적 부위의 묶음이다.
     */
    private static Optional<MuscleBundle> todayBundle(List<MuscleState> lacking, List<MuscleState> optimal) {
        Predicate<MuscleState> neutral = s -> !s.lesserSide() && !s.biggerSide();
        return Stream.of(
                        lacking.stream().filter(MuscleState::lesserSide),
                        lacking.stream().filter(neutral),
                        optimal.stream().filter(MuscleState::lesserSide),
                        optimal.stream().filter(neutral),
                        lacking.stream().filter(MuscleState::biggerSide),
                        optimal.stream().filter(MuscleState::biggerSide))
                .flatMap(s -> s)
                .map(s -> MuscleBundle.of(s.muscle()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** 하루에 담은 부위. 하루 상한·하한과 과다 경계를 지키며 담는다 */
    private static final class Day {

        private final RoutinePolicy policy;
        private final List<RoutineSlot> slots = new ArrayList<>();
        private int total;

        Day(RoutinePolicy policy) {
            this.policy = policy;
        }

        /** 하루 상한을 넘으면 남은 만큼만 담는다. 그게 부위당 최소에 못 미치면 담지 않는다 */
        void add(MuscleState state, int sets, boolean fill) {
            int fitting = Math.min(sets, policy.dailyMaxSets() - total);
            if (fitting >= policy.perMuscleMinSets()) {
                slots.add(new RoutineSlot(state.muscle(), fitting, state.weeklySets(), state.verdict(), fill));
                total += fitting;
            }
        }

        /** 이미 최적인 부위는 과다 경계를 넘기지 않을 때만 넣는다(R2) */
        void fill(MuscleState state, int sets) {
            if (withinOptimal(state.weeklySets(), sets)) {
                add(state, sets, true);
            }
        }

        boolean belowMin() {
            return total < policy.dailyMinSets();
        }

        /**
         * 하한에 못 미치면 다른 묶음을 끌어오지 않고 담은 부위의 세트를 하나씩 올린다. 부위당 최대와
         * 과다 경계 안에서만 — 그래도 못 채우면 짧은 루틴이다.
         */
        void topUp() {
            boolean raised = true;
            while (belowMin() && raised) {
                raised = false;
                for (int i = 0; i < slots.size() && belowMin(); i++) {
                    RoutineSlot slot = slots.get(i);
                    if (slot.sets() < policy.perMuscleMaxSets() && withinOptimal(slot.weeklySets(), slot.sets() + 1)) {
                        slots.set(i, new RoutineSlot(slot.muscle(), slot.sets() + 1, slot.weeklySets(), slot.verdict(),
                                slot.fill()));
                        total++;
                        raised = true;
                    }
                }
            }
        }

        private boolean withinOptimal(BigDecimal weeklySets, int sets) {
            return weeklySets.add(BigDecimal.valueOf(sets)).compareTo(policy.optimalThrough()) <= 0;
        }

        List<RoutineSlot> slots() {
            return List.copyOf(slots);
        }
    }

    /** 오늘 포함 {@code recoveryDays}일 안에 한 부위. 48시간이면 오늘·어제 */
    static boolean recovering(MuscleState state, LocalDate today, RoutinePolicy policy) {
        LocalDate last = state.lastTrained();
        return last != null && policy.recoveryDays() > 0
                && !last.isBefore(today.minusDays(policy.recoveryDays() - 1L));
    }

    /** 오래된 순. 한 번도 안 한 부위가 가장 오래된 것이다 */
    private static LocalDate lastTrainedOrder(MuscleState state) {
        return state.lastTrained() == null ? LocalDate.MIN : state.lastTrained();
    }

    private static int clamp(int sets, RoutinePolicy policy) {
        return Math.max(policy.perMuscleMinSets(), Math.min(policy.perMuscleMaxSets(), sets));
    }
}
