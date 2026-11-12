package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 오늘 할 부위와 세트를 정한다.
 *
 * <p>DB·스프링에 의존하지 않는 순수 계산이다. 분석 판정처럼 경계값을 단위 테스트로
 * 고정한다 — 루틴이 무엇을 근거로 나왔는지를 이 클래스 하나로 설명할 수 있어야 한다.
 *
 * <pre>
 *   회복   = 오늘·어제 한 부위가 있는 묶음은 통째로 뺀다. 코어는 부위만 ({@link RoutineExclusion})
 *   후보   = 부족 · 권장 이하 (과다·최적 제외)
 *   정렬   = 판정 → 불균형 적은 쪽 → 모자란 양(큰 순) → 마지막 운동일(오래된 순) → 크기 순
 *   묶음   = 맨 앞 부위의 묶음(하체·밀기·당기기). 불균형이면 적은 쪽이 먼저, 많은 쪽은 마지막
 *   세트   = 주 목표 ÷ 묶음이 돌아오는 주당 횟수(주당 일수 ÷ 묶음 수). 부족은 올림, 권장 이하·최적은 내림, 3~6으로 자름
 *   담기   = 묶음의 부족 부위 → 코어 → 묶음의 최적 부위. 하루 상한(18)을 넘으면 남은 만큼만
 *   최적   = 같은 세트를 담되 과다 경계(주당 평균 + 오늘 세트 ≤ 20)를 넘지 않는 만큼만. 3세트도 못 담으면 뺀다
 *   코어   = 부족하면 어느 날이든, 최적이면 하체 날에(하체 묶음은 부위가 둘이라 자리가 난다)
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
     * 그날 묶음을 정한 단계. 앞 단계에 묶음이 있는 부위가 있으면 뒤 단계는 보지 않는다 —
     * 불균형의 적은 쪽이 판정보다 먼저고, 많은 쪽에만 속한 부위는 마지막이다.
     */
    public enum Lead {
        /** 불균형의 적은 쪽에만 속한 모자란 부위 */
        LACKING_LESSER,
        /** 불균형과 상관없는 모자란 부위 */
        LACKING,
        /** 적은 쪽에만 속한 최적 부위 — 오래 쉰 순 */
        OPTIMAL_LESSER,
        /** 불균형과 상관없는 최적 부위 — 오래 쉰 순 */
        OPTIMAL,
        /** 많은 쪽에만 속한 모자란 부위 */
        LACKING_BIGGER,
        /** 많은 쪽에만 속한 최적 부위 */
        OPTIMAL_BIGGER
    }

    /**
     * 루틴이 그렇게 나온 근거. 화면의 "왜 오늘 이 부위인지"({@link RoutineReason})가 이것만 보고 쓴다 —
     * 규칙이 실제로 본 값이라 문장이 규칙과 어긋날 수 없다.
     *
     * @param bundle         그날 묶음. 묶음 부위가 모두 쉬거나 과다면 비어 있다
     * @param lead           묶음을 정한 부위. 묶음이 없으면 null
     * @param stage          그 부위를 고른 단계. 묶음이 없으면 null
     * @param restingBundles 회복 때문에 통째로 쉬는 묶음 → 그 묶음을 마지막으로 운동한 날
     * @param available      회복으로 빠지지 않은 판정 부위(오늘 담을 수 있는 후보 전부)
     * @param trimmed        과다 경계(주당 평균 + 오늘 세트) 때문에 세트를 줄여 담은 최적 부위
     */
    public record Basis(Optional<MuscleBundle> bundle, MuscleState lead, Lead stage,
                        Map<MuscleBundle, LocalDate> restingBundles, List<MuscleState> available,
                        List<MuscleGroup> trimmed) {
    }

    public record Plan(List<RoutineSlot> slots, Basis basis) {
    }

    /**
     * @param states      판정 부위 9종의 상태. 판정하지 않는 부위(종아리·전완)는 무시한다
     * @param daysPerWeek 주당 운동 일수. 모르면 부르는 쪽이 기본값을 넘긴다
     * @param today       루틴 날짜. 회복 제외의 기준
     */
    public static List<RoutineSlot> plan(List<MuscleState> states, double daysPerWeek, LocalDate today,
                                         RoutinePolicy policy) {
        return decide(states, daysPerWeek, today, policy).slots();
    }

    /** {@link #plan}과 같은 계산. 부위·세트와 함께 그렇게 정한 근거를 돌려준다 */
    public static Plan decide(List<MuscleState> states, double daysPerWeek, LocalDate today, RoutinePolicy policy) {
        if (daysPerWeek <= 0) {
            throw new IllegalArgumentException("주당 운동 일수는 0보다 커야 한다: " + daysPerWeek);
        }
        double base = policy.targetWeeklySets() * MuscleBundle.values().length / daysPerWeek;
        BigDecimal target = BigDecimal.valueOf(policy.targetWeeklySets());

        // 회복 중인 부위와 그 묶음은 뺀다. 분석 탭의 회복 지도가 같은 함수로 "오늘 루틴에서 빠진다"고 적는다
        Map<MuscleGroup, LocalDate> lastTrained = new EnumMap<>(MuscleGroup.class);
        states.forEach(s -> lastTrained.put(s.muscle(), s.lastTrained()));
        Map<MuscleGroup, RoutineExclusion.Excluded> excluded =
                RoutineExclusion.of(lastTrained, today, policy.recoveryDays());
        Set<MuscleGroup> resting = excluded.keySet();
        List<MuscleState> judged = states.stream()
                .filter(s -> s.muscle().judged())
                .filter(s -> !resting.contains(s.muscle()))
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

        Optional<Leader> leader = leader(lacking, optimal);
        Optional<MuscleBundle> bundle = leader.flatMap(l -> MuscleBundle.of(l.state().muscle()));
        Predicate<MuscleState> inBundle = s -> bundle.filter(b -> b.contains(s.muscle())).isPresent();
        Predicate<MuscleState> core = s -> s.muscle() == MuscleGroup.CORE;
        // 최적 부위도 주 목표를 유지할 만큼 담는다. 3세트로 줄이면 따라 하는 사람의 주당 평균이 다시 내려간다
        int fillSets = clamp((int) Math.floor(base + 1e-9), policy);
        boolean lowerDay = bundle.filter(b -> b == MuscleBundle.LOWER).isPresent();

        Day day = new Day(policy);
        Stream.concat(lacking.stream().filter(inBundle), lacking.stream().filter(core)).forEach(state -> {
            int sets = state.verdict() == VolumeVerdict.INSUFFICIENT
                    ? clamp((int) Math.ceil(base - 1e-9), policy)
                    : clamp((int) Math.floor(base + 1e-9), policy);
            day.add(state, sets, false);
        });
        // 남은 자리는 묶음의 최적 부위끼리 고르게 나눈다. 앞 부위가 다 가져가면 등 6 · 이두 0이 된다
        List<MuscleState> fillers = optimal.stream().filter(inBundle).filter(day::fillable).toList();
        for (int i = 0; i < fillers.size(); i++) {
            day.fill(fillers.get(i), Math.min(fillSets, day.share(fillers.size() - i)));
        }
        optimal.stream().filter(core).forEach(state -> {
            if (lowerDay || day.belowMin()) {
                day.fill(state, fillSets);
            }
        });
        day.topUp();
        return new Plan(day.slots(), new Basis(bundle, leader.map(Leader::state).orElse(null),
                leader.map(Leader::stage).orElse(null), restingBundles(excluded, lastTrained), judged,
                day.trimmed()));
    }

    /** 회복 중인 부위가 있어 통째로 쉬는 묶음과, 그 묶음을 마지막으로 운동한 날 */
    private static Map<MuscleBundle, LocalDate> restingBundles(Map<MuscleGroup, RoutineExclusion.Excluded> excluded,
                                                              Map<MuscleGroup, LocalDate> lastTrained) {
        Map<MuscleBundle, LocalDate> result = new EnumMap<>(MuscleBundle.class);
        excluded.forEach((muscle, why) -> {
            if (why.reason() == RoutineExclusion.Reason.SELF_RECOVERING) {
                MuscleBundle.of(muscle).ifPresent(b ->
                        result.merge(b, lastTrained.get(muscle), (x, y) -> x.isAfter(y) ? x : y));
            }
        });
        return result;
    }

    private record Leader(MuscleState state, Lead stage) {
    }

    /**
     * 그날 묶음을 정하는 부위. 불균형의 적은 쪽 부위가 판정보다 먼저고, 많은 쪽에만 속한 부위는 가장 나중이다 —
     * 많은 쪽 묶음을 하면 불균형이 커진다(R3). 부족한 부위가 코어뿐이거나 없으면 가장 오래 쉰 최적 부위다.
     * 코어는 묶음이 없어 건너뛴다.
     */
    private static Optional<Leader> leader(List<MuscleState> lacking, List<MuscleState> optimal) {
        Predicate<MuscleState> neutral = s -> !s.lesserSide() && !s.biggerSide();
        Map<Lead, Stream<MuscleState>> stages = new EnumMap<>(Lead.class);
        stages.put(Lead.LACKING_LESSER, lacking.stream().filter(MuscleState::lesserSide));
        stages.put(Lead.LACKING, lacking.stream().filter(neutral));
        stages.put(Lead.OPTIMAL_LESSER, optimal.stream().filter(MuscleState::lesserSide));
        stages.put(Lead.OPTIMAL, optimal.stream().filter(neutral));
        stages.put(Lead.LACKING_BIGGER, lacking.stream().filter(MuscleState::biggerSide));
        stages.put(Lead.OPTIMAL_BIGGER, optimal.stream().filter(MuscleState::biggerSide));
        for (Map.Entry<Lead, Stream<MuscleState>> stage : stages.entrySet()) {
            Optional<MuscleState> first = stage.getValue()
                    .filter(s -> MuscleBundle.of(s.muscle()).isPresent())
                    .findFirst();
            if (first.isPresent()) {
                return Optional.of(new Leader(first.get(), stage.getKey()));
            }
        }
        return Optional.empty();
    }

    /** 하루에 담은 부위. 하루 상한·하한과 과다 경계를 지키며 담는다 */
    private static final class Day {

        private final RoutinePolicy policy;
        private final List<RoutineSlot> slots = new ArrayList<>();
        private final List<MuscleGroup> trimmed = new ArrayList<>();
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

        /**
         * 이미 최적인 부위는 과다 경계까지만 담는다(R2). 주당 평균 + 오늘 세트가 경계를 넘으면 넘지 않는 만큼으로
         * 줄이고, 그게 부위당 최소에 못 미치면 {@link #add}가 담지 않는다.
         */
        void fill(MuscleState state, int sets) {
            int room = roomBeforeExcessive(state);
            int before = slots.size();
            add(state, Math.min(sets, room), true);
            if (room < sets && slots.size() > before) {
                trimmed.add(state.muscle());
            }
        }

        List<MuscleGroup> trimmed() {
            return List.copyOf(trimmed);
        }

        /** 과다 경계 안에서 부위당 최소라도 담을 수 있는 최적 부위인지 */
        boolean fillable(MuscleState state) {
            return roomBeforeExcessive(state) >= policy.perMuscleMinSets();
        }

        /** 하루 상한까지 남은 자리를 {@code parts}부위가 나눌 때 한 부위의 몫. 부위당 최소보다 작게 주지는 않는다 */
        int share(int parts) {
            return Math.max(policy.perMuscleMinSets(), (policy.dailyMaxSets() - total) / parts);
        }

        private int roomBeforeExcessive(MuscleState state) {
            return policy.optimalThrough().subtract(state.weeklySets()).setScale(0, RoundingMode.FLOOR).intValue();
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

    /** 오래된 순. 한 번도 안 한 부위가 가장 오래된 것이다 */
    private static LocalDate lastTrainedOrder(MuscleState state) {
        return state.lastTrained() == null ? LocalDate.MIN : state.lastTrained();
    }

    private static int clamp(int sets, RoutinePolicy policy) {
        return Math.max(policy.perMuscleMinSets(), Math.min(policy.perMuscleMaxSets(), sets));
    }
}
