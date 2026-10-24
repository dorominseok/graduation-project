package com.fitness.backend.routine.service;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.BalanceVerdict;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.service.AnalysisService;
import com.fitness.backend.analysis.web.AnalysisDtos.BalanceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.ChildResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleVolumeResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.PairResponse;
import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.exercise.repository.UserFavoriteExerciseRepository;
import com.fitness.backend.routine.RoutineProperties;
import com.fitness.backend.routine.domain.CandidatePicker;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.MuscleState;
import com.fitness.backend.routine.domain.PlannedItem;
import com.fitness.backend.routine.domain.RepRange;
import com.fitness.backend.routine.domain.Routine;
import com.fitness.backend.routine.domain.RoutineCheck;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import com.fitness.backend.routine.domain.RoutineEnums.Author;
import com.fitness.backend.routine.domain.RoutineEnums.FallbackReason;
import com.fitness.backend.routine.domain.RoutineItem;
import com.fitness.backend.routine.domain.RoutinePolicy;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.domain.SlotPlanner;
import com.fitness.backend.routine.llm.LlmRoutineComposer;
import com.fitness.backend.routine.llm.LlmRoutineComposer.Attempt;
import com.fitness.backend.routine.llm.LlmRoutineComposer.Composition;
import com.fitness.backend.routine.repository.ExerciseDaysRow;
import com.fitness.backend.routine.repository.LastTrainedRow;
import com.fitness.backend.routine.repository.RoutineItemRepository;
import com.fitness.backend.routine.repository.RoutineRepository;
import com.fitness.backend.routine.repository.TrainingHistoryRepository;
import com.fitness.backend.routine.web.RoutineDtos.AlternativeResponse;
import com.fitness.backend.routine.web.RoutineDtos.CreateResult;
import com.fitness.backend.routine.web.RoutineDtos.FeedbackRequest;
import com.fitness.backend.routine.web.RoutineDtos.FeedbackState;
import com.fitness.backend.routine.web.RoutineDtos.RoutineItemResponse;
import com.fitness.backend.routine.web.RoutineDtos.RoutineResponse;
import com.fitness.backend.routine.web.RoutineDtos.UpdateItemRequest;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 오늘의 루틴.
 *
 * <p>규칙은 {@code routine.domain}의 순수 계산이 맡고, 여기서는 분석 결과와 기록을 읽어 넘기고
 * 결과를 저장한다. 부위·세트는 늘 규칙이 정하고, 종목·순서·설명은 LLM이 켜져 있으면 LLM이,
 * 꺼져 있거나 실패하면 규칙이 고른다.
 */
@Service
@Transactional(readOnly = true)
public class RoutineService {

    private static final Logger log = LoggerFactory.getLogger(RoutineService.class);

    /** 교체 후보는 이만큼만 보인다 */
    private static final int MAX_ALTERNATIVES = 10;

    private final RoutineRepository routineRepository;
    private final RoutineItemRepository itemRepository;
    private final TrainingHistoryRepository historyRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserFavoriteExerciseRepository favoriteRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final AnalysisService analysisService;
    private final AnalysisProperties analysisProperties;
    private final RoutineProperties properties;
    /** {@code app.llm.enabled=true}일 때만 있다 */
    private final Optional<LlmRoutineComposer> llm;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public RoutineService(RoutineRepository routineRepository,
                          RoutineItemRepository itemRepository,
                          TrainingHistoryRepository historyRepository,
                          ExerciseRepository exerciseRepository,
                          UserFavoriteExerciseRepository favoriteRepository,
                          WorkoutSessionRepository sessionRepository,
                          AnalysisService analysisService,
                          AnalysisProperties analysisProperties,
                          RoutineProperties properties,
                          Optional<LlmRoutineComposer> llm,
                          TransactionTemplate transactions,
                          Clock clock) {
        this.routineRepository = routineRepository;
        this.itemRepository = itemRepository;
        this.historyRepository = historyRepository;
        this.exerciseRepository = exerciseRepository;
        this.favoriteRepository = favoriteRepository;
        this.sessionRepository = sessionRepository;
        this.analysisService = analysisService;
        this.analysisProperties = analysisProperties;
        this.properties = properties;
        this.llm = llm;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ── 만들기

    /**
     * 오늘 루틴. 이미 있으면 그 루틴을 준다 — 하루에 하나이고 다시 만들기는 없다.
     *
     * <p>판정은 기준일 어제로 본다. 오늘 체크한 세트가 오늘 루틴의 근거를 흔들지 않게.
     * 회복 제외만 오늘 기록까지 본다 — 오늘 이미 한 부위는 빠져야 한다.
     *
     * <p>트랜잭션은 저장할 때만 연다. LLM 호출(재요청까지 하면 수 초)이 DB 연결을 붙잡지 않게 —
     * 읽기는 각자 짧은 읽기 트랜잭션이고, 저장은 한 트랜잭션이다. SUPPORTS라 이미 열린 트랜잭션이
     * 있으면(테스트) 거기에 붙는다.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public CreateResult createToday(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Optional<Routine> existing = routineRepository.findByUserIdAndRoutineDate(userId, today);
        if (existing.isPresent()) {
            return new CreateResult(toResponse(existing.get()), false);
        }

        LocalDate referenceDate = today.minusDays(1);
        RoutinePolicy policy = policy();
        MuscleVolumeResponse volume = analysisService.muscleVolume(userId, null, referenceDate);
        BalanceResponse balance = analysisService.balance(userId, null, referenceDate);

        List<Imbalance> imbalances = imbalances(balance);
        Set<MuscleGroup> lesser = imbalances.stream()
                .flatMap(im -> im.lesser().components().stream())
                .collect(Collectors.toSet());
        Map<MuscleGroup, LocalDate> lastTrained = lastTrained(userId, today);
        List<MuscleState> states = volume.tiers().stream()
                .flatMap(tier -> tier.children().stream())
                .map((ChildResponse c) -> new MuscleState(c.key(), c.weeklySets(), c.verdict(),
                        lastTrained.get(c.key()), lesser.contains(c.key())))
                .toList();

        List<RoutineSlot> slots = SlotPlanner.plan(states, daysPerWeek(volume, policy), today, policy);

        Map<Long, Exercise> exercises = allExercises();
        Map<MuscleGroup, List<ExerciseOption>> candidates = candidates(userId, today, slots, exercises);
        Composition composition = compose(slots, candidates, exercises);
        List<String> failures = RoutineCheck.check(slots, composition.items(), imbalances,
                candidateIds(candidates), policy);

        return transactions.execute(status ->
                save(userId, today, referenceDate, slots, exercises, composition, failures));
    }

    /**
     * 종목·순서·설명. LLM이 켜져 있으면 LLM이 고르고, 검증 실패·호출 실패면 안에서 규칙으로 간다.
     * 꺼져 있으면 부르지 않는다({@code LLM_DISABLED}).
     */
    private Composition compose(List<RoutineSlot> slots, Map<MuscleGroup, List<ExerciseOption>> candidates,
                                Map<Long, Exercise> exercises) {
        if (llm.isEmpty()) {
            List<PlannedItem> items = RuleComposer.compose(slots, candidates);
            return new Composition(items, RuleComposer.comment(items), Author.RULE, Author.RULE,
                    FallbackReason.LLM_DISABLED, List.of());
        }
        Map<Long, String> names = exercises.values().stream()
                .collect(Collectors.toMap(Exercise::getId, Exercise::getNameKo));
        Composition result = llm.get().compose(slots, candidates, names);
        if (result.fallbackReason() != null) {
            // 운영에서 폴백이 얼마나 나는지 본다. 사용자 기록은 남기지 않고 사유만
            log.warn("루틴 LLM 폴백 {} — 시도 {}회, {}", result.fallbackReason(), result.llmAttempts(),
                    result.attempts().stream().map(RoutineService::describe).toList());
        }
        return result;
    }

    private static String describe(Attempt attempt) {
        return attempt.error() != null ? attempt.error()
                : attempt.failures().stream().map(f -> f.code()).distinct().toList() + " " + attempt.latencyMs() + "ms";
    }

    private CreateResult save(Long userId, LocalDate today, LocalDate referenceDate, List<RoutineSlot> slots,
                              Map<Long, Exercise> exercises, Composition composition, List<String> failures) {
        int inserted = routineRepository.insertIfAbsent(userId, today, referenceDate,
                composition.generatedBy().name(), composition.llmAttempts(),
                composition.fallbackReason() == null ? null : composition.fallbackReason().name(),
                composition.comment(), composition.commentBy().name(),
                failures.isEmpty(), failures.isEmpty() ? null : String.join(",", failures));
        Routine routine = routineRepository.findByUserIdAndRoutineDate(userId, today)
                .orElseThrow(() -> new IllegalStateException("방금 만든 루틴이 없다"));
        if (inserted == 0) {
            // 동시에 들어온 다른 요청이 먼저 만들었다. 그 루틴을 준다
            return new CreateResult(toResponse(routine), false);
        }

        List<PlannedItem> items = composition.items();
        Set<Long> doneBefore = new HashSet<>(historyRepository.exercisesDoneBefore(userId, today));
        Map<MuscleGroup, RoutineSlot> slotByMuscle = slots.stream()
                .collect(Collectors.toMap(RoutineSlot::muscle, Function.identity()));
        List<RoutineItem> saved = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PlannedItem item = items.get(i);
            Exercise exercise = exercises.get(item.exerciseId());
            RoutineSlot slot = slotByMuscle.get(item.muscle());
            saved.add(RoutineItem.plan(routine.getId(), i + 1, item.muscle(), item.exerciseId(), item.sets(),
                    RepRange.of(exercise.getMeasureType()), !doneBefore.contains(item.exerciseId()),
                    slot.weeklySets(), slot.verdict()));
        }
        itemRepository.saveAll(saved);
        linkDay(userId, today, routine.getId());
        return new CreateResult(toResponse(routine), true);
    }

    /** 불균형인 쌍마다 (적은 쪽, 많은 쪽). 판정 불가(양쪽 0)는 넣지 않는다 */
    private static List<Imbalance> imbalances(BalanceResponse balance) {
        List<Imbalance> result = new ArrayList<>();
        for (PairResponse pair : balance.pairs()) {
            if (pair.verdict() != BalanceVerdict.IMBALANCED || pair.biggerSide() == null) {
                continue;
            }
            BalanceSide bigger = pair.biggerSide();
            BalanceSide smaller = bigger == pair.key().left() ? pair.key().right() : pair.key().left();
            result.add(new Imbalance(smaller, bigger));
        }
        return result;
    }

    /** 주동근별 마지막 운동일을 판정 부위로 접는다. 등은 넷이라 가장 늦은 날을 쓴다 */
    private Map<MuscleGroup, LocalDate> lastTrained(Long userId, LocalDate today) {
        Map<MuscleGroup, LocalDate> result = new EnumMap<>(MuscleGroup.class);
        for (LastTrainedRow row : historyRepository.lastTrained(userId, today)) {
            MuscleGroup.of(row.primaryMuscle(), row.deltRegion()).ifPresent(group ->
                    result.merge(group, row.lastDate(), (a, b) -> a.isAfter(b) ? a : b));
        }
        return result;
    }

    /**
     * 주당 운동 일수. 운동한 날이 너무 적으면 모른다고 보고 기본값(3일)을 쓴다 —
     * 하루 기록한 사람을 "주 1회"로 보면 부위마다 6세트가 나온다.
     */
    private static double daysPerWeek(MuscleVolumeResponse volume, RoutinePolicy policy) {
        int days = volume.confidence().workoutDayCount();
        if (days < policy.minKnownDays()) {
            return policy.defaultDaysPerWeek();
        }
        return days * 7.0 / volume.basisDays();
    }

    /** 슬롯마다 후보. 종목 109개를 한 번 읽어 판정 부위로 나눈다 */
    private Map<MuscleGroup, List<ExerciseOption>> candidates(Long userId, LocalDate today, List<RoutineSlot> slots,
                                                             Map<Long, Exercise> exercises) {
        Set<Long> favorites = new HashSet<>(favoriteRepository.findExerciseIdsByUserId(userId));
        Map<Long, Integer> recent = recentDays(userId, today);
        Set<Long> doneBefore = new HashSet<>(historyRepository.exercisesDoneBefore(userId, today));

        Map<MuscleGroup, List<ExerciseOption>> pools = new EnumMap<>(MuscleGroup.class);
        for (Exercise e : exercises.values()) {
            MuscleGroup.of(e.getPrimaryMuscle(), e.getDeltRegion()).ifPresent(group ->
                    pools.computeIfAbsent(group, g -> new ArrayList<>()).add(new ExerciseOption(
                            e.getId(), e.getGroupName(), e.getEquipment(), favorites.contains(e.getId()),
                            recent.getOrDefault(e.getId(), 0), !doneBefore.contains(e.getId()),
                            e.getStarterRank() == null ? null : e.getStarterRank().intValue())));
        }

        Map<MuscleGroup, List<ExerciseOption>> result = new EnumMap<>(MuscleGroup.class);
        for (RoutineSlot slot : slots) {
            result.put(slot.muscle(), CandidatePicker.pick(pools.getOrDefault(slot.muscle(), List.of()),
                    properties.candidatesPerMuscle(), properties.historyMaxCandidates()));
        }
        return result;
    }

    private Map<Long, Integer> recentDays(Long userId, LocalDate today) {
        LocalDate from = today.minusWeeks(properties.historyWeeks()).plusDays(1);
        return historyRepository.exerciseDays(userId, from, today).stream()
                .collect(Collectors.toMap(ExerciseDaysRow::exerciseId, r -> (int) r.count()));
    }

    private static Map<MuscleGroup, Set<Long>> candidateIds(Map<MuscleGroup, List<ExerciseOption>> candidates) {
        Map<MuscleGroup, Set<Long>> result = new EnumMap<>(MuscleGroup.class);
        candidates.forEach((muscle, options) ->
                result.put(muscle, options.stream().map(ExerciseOption::id).collect(Collectors.toSet())));
        return result;
    }

    /** 그날 기록을 열어 루틴을 잇는다. 하루 기록은 하루에 하나라 날짜로 찾는다 */
    private void linkDay(Long userId, LocalDate today, Long routineId) {
        sessionRepository.insertIfAbsent(userId, today);
        WorkoutSession day = sessionRepository.findByUserIdAndPerformedOn(userId, today)
                .orElseThrow(() -> new IllegalStateException("방금 연 기록이 없다"));
        day.linkRoutine(routineId);
    }

    // ── 읽기

    public Optional<RoutineResponse> today(Long userId) {
        return routineRepository.findByUserIdAndRoutineDate(userId, LocalDate.now(clock)).map(this::toResponse);
    }

    public RoutineResponse get(Long userId, Long routineId) {
        return toResponse(mustFind(userId, routineId));
    }

    /**
     * 만족도를 물을 지난 루틴. 오늘 루틴은 홈 카드가 따로 묻는다.
     *
     * <p>최근 며칠 안에서 <b>루틴 부위를 체크한 가장 최근 루틴 하나</b>만 본다. 그 루틴에 이미 답했거나
     * 건너뛰었으면 더 오래된 루틴은 묻지 않는다 — 여러 날을 밀린 숙제처럼 연달아 묻지 않게.
     */
    public Optional<RoutineResponse> feedbackPending(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Exercise> exercises = allExercises();
        for (Routine routine : routineRepository.findRecent(
                userId, today.minusDays(properties.feedbackLookbackDays()), today)) {
            List<RoutineItem> items = itemRepository.findByRoutineIdOrderByPositionAsc(routine.getId());
            if (!trainedRoutineMuscle(userId, routine.getRoutineDate(), items, exercises)) {
                continue;
            }
            return routine.feedbackClosed() ? Optional.empty() : Optional.of(toResponse(routine, items, exercises));
        }
        return Optional.empty();
    }

    /** 교체 후보 — 같은 부위에서 기구가 다른 종목 먼저, 그다음 자주 한 순, 입문 대표 종목 순 */
    public List<AlternativeResponse> alternatives(Long userId, Long routineId, Long itemId) {
        Routine routine = mustFind(userId, routineId);
        RoutineItem item = mustFindItem(routine, itemId);
        Map<Long, Exercise> exercises = allExercises();
        Exercise current = exercises.get(item.getExerciseId());
        Set<Long> inRoutine = itemRepository.findByRoutineIdOrderByPositionAsc(routine.getId()).stream()
                .filter(i -> !i.isRemoved())
                .map(RoutineItem::getExerciseId)
                .collect(Collectors.toSet());
        Map<Long, Integer> recent = recentDays(userId, routine.getRoutineDate());
        Set<Long> doneBefore = new HashSet<>(historyRepository.exercisesDoneBefore(userId, routine.getRoutineDate()));

        return exercises.values().stream()
                .filter(e -> MuscleGroup.of(e.getPrimaryMuscle(), e.getDeltRegion())
                        .map(g -> g == item.getMuscleGroup()).orElse(false))
                .filter(e -> !inRoutine.contains(e.getId()))
                .sorted(Comparator
                        .comparing((Exercise e) -> e.getEquipment() == current.getEquipment())
                        .thenComparing(e -> recent.getOrDefault(e.getId(), 0), Comparator.reverseOrder())
                        .thenComparing(Exercise::getStarterRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Exercise::getId))
                .limit(MAX_ALTERNATIVES)
                .map(e -> new AlternativeResponse(e.getId(), e.getNameKo(), e.getNameEn(), e.getEquipment(),
                        e.getMeasureType(), !doneBefore.contains(e.getId()),
                        e.getEquipment() == current.getEquipment()))
                .toList();
    }

    // ── 후보정

    /** 종목 교체·세트 조정. 원래 값은 항목에 남는다 — 수행률의 분모다 */
    @Transactional
    public RoutineResponse updateItem(Long userId, Long routineId, Long itemId, UpdateItemRequest request) {
        Routine routine = mustFindToday(userId, routineId);
        RoutineItem item = mustFindItem(routine, itemId);

        if (request.exerciseId() != null) {
            Exercise exercise = exerciseRepository.findById(request.exerciseId())
                    .orElseThrow(() -> ApiException.notFound("종목"));
            boolean sameMuscle = MuscleGroup.of(exercise.getPrimaryMuscle(), exercise.getDeltRegion())
                    .map(g -> g == item.getMuscleGroup()).orElse(false);
            if (!sameMuscle) {
                // 다른 부위로 바꾸면 루틴의 근거(이 부위가 부족하다)가 성립하지 않는다
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 부위의 종목으로만 바꿀 수 있습니다.");
            }
            boolean firstTime = !historyRepository.exercisesDoneBefore(userId, routine.getRoutineDate())
                    .contains(exercise.getId());
            item.replaceExercise(exercise.getId(), RepRange.of(exercise.getMeasureType()), firstTime);
        }
        if (request.plannedSets() != null) {
            item.adjustSets(request.plannedSets());
        }
        return toResponse(routine);
    }

    /** 종목 빼기. 지우지 않고 표시만 한다 — 원래 계획은 수행률의 분모로 남는다 */
    @Transactional
    public RoutineResponse removeItem(Long userId, Long routineId, Long itemId) {
        Routine routine = mustFindToday(userId, routineId);
        mustFindItem(routine, itemId).remove();
        return toResponse(routine);
    }

    // ── 만족도

    /** 루틴당 한 번. 답했거나 건너뛰었으면 다시 받지 않는다 */
    @Transactional
    public RoutineResponse feedback(Long userId, Long routineId, FeedbackRequest request) {
        Routine routine = mustFind(userId, routineId);
        if (routine.feedbackClosed()) {
            throw new ApiException(ErrorCode.ROUTINE_FEEDBACK_EXISTS);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (Boolean.TRUE.equals(request.skipped())) {
            routine.skipFeedback(now);
        } else if (request.rating() != null) {
            routine.recordFeedback(request.rating(), request.reason(), now);
        } else {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "만족도를 골라 주세요.");
        }
        return toResponse(routine);
    }

    // ── 도우미

    private RoutinePolicy policy() {
        return properties.policy(analysisProperties.volume().optimalThrough());
    }

    private Map<Long, Exercise> allExercises() {
        return exerciseRepository.findAll().stream().collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }

    private Routine mustFind(Long userId, Long routineId) {
        return routineRepository.findByIdAndUserId(routineId, userId).orElseThrow(() -> ApiException.notFound("루틴"));
    }

    /** 후보정은 오늘 루틴만 — 지난 루틴을 고치면 이미 쌓인 수행률·후보정 기록이 바뀐다 */
    private Routine mustFindToday(Long userId, Long routineId) {
        Routine routine = mustFind(userId, routineId);
        if (!routine.getRoutineDate().isEqual(LocalDate.now(clock))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "지난 루틴은 바꿀 수 없습니다.");
        }
        return routine;
    }

    private RoutineItem mustFindItem(Routine routine, Long itemId) {
        return itemRepository.findByIdAndRoutineId(itemId, routine.getId())
                .orElseThrow(() -> ApiException.notFound("루틴 항목"));
    }

    /** 그날 루틴 부위에 본세트를 하나라도 체크했나 — 만족도를 물을 조건 */
    private boolean trainedRoutineMuscle(Long userId, LocalDate date, List<RoutineItem> items,
                                         Map<Long, Exercise> exercises) {
        Set<MuscleGroup> muscles = items.stream()
                .filter(i -> !i.isRemoved())
                .map(RoutineItem::getMuscleGroup)
                .collect(Collectors.toSet());
        return historyRepository.setsOn(userId, date).stream()
                .map(r -> exercises.get(r.exerciseId()))
                .filter(e -> e != null)
                .map(e -> MuscleGroup.of(e.getPrimaryMuscle(), e.getDeltRegion()))
                .anyMatch(g -> g.isPresent() && muscles.contains(g.get()));
    }

    private RoutineResponse toResponse(Routine routine) {
        return toResponse(routine, itemRepository.findByRoutineIdOrderByPositionAsc(routine.getId()), allExercises());
    }

    private RoutineResponse toResponse(Routine routine, List<RoutineItem> items, Map<Long, Exercise> exercises) {
        Map<Long, Integer> setsOnDay = historyRepository.setsOn(routine.getUserId(), routine.getRoutineDate()).stream()
                .collect(Collectors.toMap(ExerciseDaysRow::exerciseId, r -> (int) r.count()));

        List<RoutineItemResponse> itemResponses = items.stream().map(item -> {
            Exercise e = exercises.get(item.getExerciseId());
            return new RoutineItemResponse(item.getId(), item.getPosition(), item.getMuscleGroup(),
                    item.getMuscleGroup().label(), item.getExerciseId(), e.getNameKo(), e.getNameEn(),
                    e.getMeasureType(), e.getEquipment(), item.getPlannedSets(), item.getTargetMin(),
                    item.getTargetMax(), item.isFirstTime(), item.getBasisWeeklySets(), item.getBasisVerdict(),
                    item.getBasisVerdict().label(), item.isRemoved(), item.adjusted(),
                    setsOnDay.getOrDefault(item.getExerciseId(), 0));
        }).toList();

        List<RoutineItemResponse> active = itemResponses.stream().filter(i -> !i.removed()).toList();
        int totalSets = active.stream().mapToInt(RoutineItemResponse::plannedSets).sum();
        int doneSets = active.stream().mapToInt(i -> Math.min(i.doneSets(), i.plannedSets())).sum();
        boolean isToday = routine.getRoutineDate().isEqual(LocalDate.now(clock));
        boolean askFeedback = isToday && !routine.feedbackClosed()
                && trainedRoutineMuscle(routine.getUserId(), routine.getRoutineDate(), items, exercises);

        return new RoutineResponse(routine.getId(), routine.getRoutineDate(), routine.getReferenceDate(),
                routine.getGeneratedBy(), routine.getComment(), lowConfidence(routine), totalSets, doneSets,
                itemResponses,
                new FeedbackState(routine.getFeedbackRating(), routine.getFeedbackReason(), routine.isFeedbackSkipped()),
                askFeedback);
    }

    /** 만든 날 기준 최근 4주 운동한 날이 신뢰도 임계에 못 미친다(분석 5.5와 같은 기준) */
    private boolean lowConfidence(Routine routine) {
        LocalDate to = routine.getReferenceDate();
        int days = sessionRepository.countRecordedDays(
                routine.getUserId(), to.minusDays(analysisProperties.periodDaysBack()), to);
        return days < analysisProperties.confidenceSessionThreshold();
    }
}
