package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.AveragingBasis;
import com.fitness.backend.analysis.domain.BalanceEvaluation;
import com.fitness.backend.analysis.domain.BalancePair;
import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.BalanceVerdict;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeThresholds;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 루틴을 그대로 따라 하는 사람을 몇 주 돌려 본다 — 규칙이 날마다 어떤 크기의 루틴을 내는지 보는 도구다.
 *
 * <p>부위·세트는 {@link SlotPlanner}, 후보는 {@link CandidatePicker}, 종목은 {@link RuleComposer}, 주당 일수는
 * {@link TrainingFrequency} — 운영과 같은 부품이다. 판정(주당 평균·4단계·불균형)은 분석 도메인의 순수 함수로
 * 운영({@code AnalysisService})과 같은 식을 쓴다. DB·스프링 없이 돈다.
 *
 * <p>{@code main}은 PR에 붙인 표를 찍는다:
 * {@code java -cp "build/classes/java/main;build/classes/java/test;build/resources/main" com.fitness.backend.routine.domain.RoutineSimulation}
 */
final class RoutineSimulation {

    private static final VolumeThresholds THRESHOLDS =
            new VolumeThresholds(BigDecimal.valueOf(4), BigDecimal.valueOf(10), BigDecimal.valueOf(20));
    private static final int WINDOW_DAYS = 28;
    private static final int MIN_BASIS_DAYS = 7;
    private static final int HISTORY_WEEKS = 8;
    private static final int CANDIDATES = 5;
    private static final int HISTORY_MAX = 3;
    /** 월요일. 요일 패턴이 주 단위로 맞아떨어지게 */
    static final LocalDate START = LocalDate.of(2026, 9, 7);

    /** 하루 루틴. {@code newExercises}는 그날 처음 하는 종목 수 */
    record Day(LocalDate date, int week, List<RoutineSlot> slots, List<PlannedItem> items, int newExercises,
               int daysPerWeek) {

        int sets() {
            return slots.stream().mapToInt(RoutineSlot::sets).sum();
        }

        int exercises() {
            return items.size();
        }

        boolean has(MuscleGroup muscle) {
            return slots.stream().anyMatch(s -> s.muscle() == muscle);
        }

        int setsOf(MuscleGroup muscle) {
            return slots.stream().filter(s -> s.muscle() == muscle).mapToInt(RoutineSlot::sets).sum();
        }
    }

    private record Seed(long id, String name, MuscleGroup muscle, Equipment equipment, String group, Integer starter) {
    }

    private final RoutinePolicy policy;
    private final Map<MuscleGroup, List<Seed>> pool = new EnumMap<>(MuscleGroup.class);
    private final Map<Long, String> names = new HashMap<>();
    /** 날짜 → 종목 → 본세트 */
    private final TreeMap<LocalDate, Map<Long, Integer>> log = new TreeMap<>();
    private final Map<Long, MuscleGroup> muscleOf = new HashMap<>();

    RoutineSimulation(RoutinePolicy policy) {
        this.policy = policy;
        loadSeed();
    }

    String name(long exerciseId) {
        return names.get(exerciseId);
    }

    // ── 돌리기

    /**
     * 루틴을 받기 전에 혼자 하던 기록을 넣는다. 정한 요일마다 밀기 → 당기기 → 하체+코어를 돌아가며,
     * 부위마다 한 번에 {@code setsPerSession}세트를 그 부위 후보 앞의 두 종목에 나눠서.
     */
    void seedOwnTraining(LocalDate from, int weeks, Set<DayOfWeek> weekdays, int setsPerSession) {
        List<List<MuscleGroup>> rotation = List.of(
                List.of(MuscleGroup.CHEST, MuscleGroup.DELT_FRONT, MuscleGroup.TRICEPS),
                List.of(MuscleGroup.BACK, MuscleGroup.DELT_REAR, MuscleGroup.BICEPS),
                List.of(MuscleGroup.QUADS, MuscleGroup.POSTERIOR, MuscleGroup.CORE));
        int turn = 0;
        for (int d = 0; d < weeks * 7; d++) {
            LocalDate date = from.plusDays(d);
            if (!weekdays.contains(date.getDayOfWeek())) {
                continue;
            }
            Map<Long, Integer> day = new HashMap<>();
            for (MuscleGroup muscle : rotation.get(turn++ % 3)) {
                List<ExerciseOption> options = candidates(muscle, date);
                int[] split = RuleComposer.split(setsPerSession, Math.min(2, options.size()));
                for (int i = 0; i < split.length; i++) {
                    day.put(options.get(i).id(), split[i]);
                }
            }
            log.put(date, day);
        }
    }

    /** {@code from}부터 {@code weeks}주, 정한 요일마다 루틴을 받아 그대로 한다 */
    List<Day> follow(LocalDate from, int weeks, Set<DayOfWeek> weekdays) {
        List<Day> days = new ArrayList<>();
        for (int d = 0; d < weeks * 7; d++) {
            LocalDate today = from.plusDays(d);
            if (!weekdays.contains(today.getDayOfWeek())) {
                continue;
            }
            Day day = plan(today, d / 7 + 1);
            days.add(day);
            if (!day.items().isEmpty()) {
                Map<Long, Integer> done = new HashMap<>();
                day.items().forEach(item -> done.merge(item.exerciseId(), item.sets(), Integer::sum));
                log.put(today, done);
            }
        }
        return days;
    }

    /** 그날의 루틴. 기록에는 넣지 않는다 */
    Day plan(LocalDate today, int week) {
        LocalDate reference = today.minusDays(1);
        Map<MuscleGroup, BigDecimal> weekly = weeklySets(reference);
        List<Imbalance> imbalances = imbalances(reference);
        Set<MuscleGroup> lesser = RoutineCheck.lesserOnly(imbalances);
        Set<MuscleGroup> bigger = RoutineCheck.biggerOnly(imbalances);
        Map<MuscleGroup, LocalDate> last = lastTrained(today);

        List<MuscleState> states = new ArrayList<>();
        for (MuscleGroup muscle : SlotPlanner.SIZE_ORDER) {
            BigDecimal sets = weekly.get(muscle);
            states.add(new MuscleState(muscle, sets, VolumeVerdict.classify(sets, THRESHOLDS), last.get(muscle),
                    lesser.contains(muscle), bigger.contains(muscle)));
        }
        int daysPerWeek = daysPerWeek(reference);
        List<RoutineSlot> slots = SlotPlanner.plan(states, daysPerWeek, today, policy);

        Map<MuscleGroup, List<ExerciseOption>> candidates = new EnumMap<>(MuscleGroup.class);
        slots.forEach(slot -> candidates.put(slot.muscle(), candidates(slot.muscle(), today)));
        List<PlannedItem> items = RuleComposer.compose(slots, candidates);
        Set<Long> doneBefore = doneBefore(today);
        int fresh = (int) items.stream().filter(i -> !doneBefore.contains(i.exerciseId())).count();
        return new Day(today, week, slots, items, fresh, daysPerWeek);
    }

    /** 기준일 시점의 부위별 주당 평균 — 분석 탭이 보여 주는 값 */
    Map<MuscleGroup, BigDecimal> weeklySets(LocalDate reference) {
        int basisDays = basisDays(reference);
        Map<MuscleGroup, Integer> totals = totals(reference);
        Map<MuscleGroup, BigDecimal> weekly = new EnumMap<>(MuscleGroup.class);
        for (MuscleGroup muscle : SlotPlanner.SIZE_ORDER) {
            weekly.put(muscle, VolumeVerdict.weeklyAverageOverDays(totals.getOrDefault(muscle, 0), basisDays));
        }
        return weekly;
    }

    // ── 운영과 같은 식

    private int daysPerWeek(LocalDate reference) {
        int known = recordedDays(reference.minusDays(WINDOW_DAYS - 1L), reference);
        int fullWeeks = TrainingFrequency.fullWeeks(basisDays(reference));
        return TrainingFrequency.daysPerWeek(known, recordedDays(reference.minusDays(fullWeeks * 7L - 1), reference),
                fullWeeks, policy);
    }

    private int basisDays(LocalDate reference) {
        LocalDate first = log.isEmpty() || log.firstKey().isAfter(reference) ? null : log.firstKey();
        return AveragingBasis.days(first, reference, WINDOW_DAYS, MIN_BASIS_DAYS);
    }

    private int recordedDays(LocalDate from, LocalDate to) {
        return from.isAfter(to) ? 0 : log.subMap(from, true, to, true).size();
    }

    private Map<MuscleGroup, Integer> totals(LocalDate reference) {
        Map<MuscleGroup, Integer> totals = new EnumMap<>(MuscleGroup.class);
        log.subMap(reference.minusDays(WINDOW_DAYS - 1L), true, reference, true).values().forEach(day ->
                day.forEach((exercise, sets) -> totals.merge(muscleOf.get(exercise), sets, Integer::sum)));
        return totals;
    }

    /** AnalysisService.balance와 같은 식 — 부위당 평균을 합에 상대쪽 부위 수를 곱해 맞댄다 */
    private List<Imbalance> imbalances(LocalDate reference) {
        int basisDays = basisDays(reference);
        Map<MuscleGroup, Integer> totals = totals(reference);
        List<Imbalance> result = new ArrayList<>();
        for (BalancePair pair : BalancePair.values()) {
            BigDecimal left = scaled(pair.left(), pair.right(), totals, basisDays);
            BigDecimal right = scaled(pair.right(), pair.left(), totals, basisDays);
            if (BalanceEvaluation.of(left, right, BalanceEvaluation.DEFAULT_RATIO_THRESHOLD).verdict()
                    != BalanceVerdict.IMBALANCED) {
                continue;
            }
            boolean leftBigger = left.compareTo(right) >= 0;
            result.add(new Imbalance(leftBigger ? pair.right() : pair.left(), leftBigger ? pair.left() : pair.right()));
        }
        return result;
    }

    private static BigDecimal scaled(BalanceSide side, BalanceSide other, Map<MuscleGroup, Integer> totals, int basisDays) {
        int total = side.components().stream().mapToInt(m -> totals.getOrDefault(m, 0)).sum();
        return VolumeVerdict.weeklyAverageOverDays(total, basisDays).multiply(BigDecimal.valueOf(other.components().size()));
    }

    private Map<MuscleGroup, LocalDate> lastTrained(LocalDate today) {
        Map<MuscleGroup, LocalDate> last = new EnumMap<>(MuscleGroup.class);
        log.headMap(today, true).forEach((date, day) -> day.keySet().forEach(e -> last.put(muscleOf.get(e), date)));
        return last;
    }

    private Set<Long> doneBefore(LocalDate today) {
        Set<Long> done = new HashSet<>();
        log.headMap(today, false).values().forEach(day -> done.addAll(day.keySet()));
        return done;
    }

    private List<ExerciseOption> candidates(MuscleGroup muscle, LocalDate today) {
        Set<Long> doneBefore = doneBefore(today);
        Map<Long, Integer> recent = new HashMap<>();
        log.subMap(today.minusWeeks(HISTORY_WEEKS).plusDays(1), true, today, true).values().forEach(day ->
                day.keySet().forEach(e -> recent.merge(e, 1, Integer::sum)));
        List<ExerciseOption> options = pool.getOrDefault(muscle, List.of()).stream()
                .map(s -> new ExerciseOption(s.id(), s.group(), s.equipment(), false, recent.getOrDefault(s.id(), 0),
                        !doneBefore.contains(s.id()), s.starter()))
                .toList();
        return CandidatePicker.pick(options, CANDIDATES, HISTORY_MAX);
    }

    /** 종목 시드. CSV 순서가 새 DB에 들어가는 id 순서다 */
    private void loadSeed() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                RoutineSimulation.class.getResourceAsStream("/data/exercises.csv"), StandardCharsets.UTF_8))) {
            List<String> header = List.of(reader.readLine().split(",", -1));
            String line;
            long id = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                id++;
                String[] f = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int k = 0; k < header.size(); k++) {
                    row.put(header.get(k), f[k]);
                }
                long exerciseId = id;
                names.put(exerciseId, row.get("name_ko"));
                DeltRegion delt = row.get("delt_region").isEmpty() ? null : DeltRegion.valueOf(row.get("delt_region"));
                MuscleGroup.of(row.get("primary_muscle"), delt).filter(MuscleGroup::judged).ifPresent(muscle -> {
                    muscleOf.put(exerciseId, muscle);
                    pool.computeIfAbsent(muscle, m -> new ArrayList<>()).add(new Seed(exerciseId, row.get("name_ko"),
                            muscle, Equipment.valueOf(row.get("equipment")),
                            row.get("group_name").isEmpty() ? null : row.get("group_name"),
                            row.get("starter_rank").isEmpty() ? null : Integer.valueOf(row.get("starter_rank"))));
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
