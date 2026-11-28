package com.fitness.backend.workout.service;

import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.workout.domain.ExerciseOrder;
import com.fitness.backend.workout.domain.ImportedSetTimes;
import com.fitness.backend.workout.domain.SessionDuration;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.domain.WorkoutSet;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import com.fitness.backend.workout.repository.WorkoutSetRepository;
import com.fitness.backend.workout.web.WorkoutDtos;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarDay;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarResponse;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.DayTimes;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseGroup;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseHistoryDay;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseRef;
import com.fitness.backend.workout.web.WorkoutDtos.ImportDayResult;
import com.fitness.backend.workout.web.WorkoutDtos.ImportedSet;
import com.fitness.backend.workout.web.WorkoutDtos.LastPerformanceResponse;
import com.fitness.backend.workout.web.WorkoutDtos.LastPerformanceSet;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayRequest;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayResult;
import com.fitness.backend.workout.web.WorkoutDtos.RoundTimes;
import com.fitness.backend.workout.web.WorkoutDtos.SessionResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SessionSummary;
import com.fitness.backend.workout.web.WorkoutDtos.SetInGroup;
import com.fitness.backend.workout.web.WorkoutDtos.SetResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SetSaveResult;
import com.fitness.backend.workout.web.WorkoutDtos.UpdateSessionRequest;
import com.fitness.backend.workout.web.WorkoutDtos.UpdateSetRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 기록.
 *
 * <p>기록 단위는 <b>사용자의 하루</b>다. 기본 흐름은 <b>그날 기록 열기 → 세트 저장(반복)</b>이고
 * 종료가 없다. 프론트는 기록 화면에 들어올 때 그날 기록을 열고, 완료 체크한 세트만 보낸다 —
 * 계획 행은 서버로 오지 않는다. 세트 없이 나가면 빈 기록이 남지만 운동한 날로 세지 않는다.
 *
 * <p>시작·끝 시각과 운동 시간은 저장하지 않고 세트 저장 시각으로 읽을 때 계산한다({@link DayTimes}).
 */
@Service
@Transactional(readOnly = true)
public class WorkoutService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;
    private final AnalysisProperties analysisProperties;
    private final Clock clock;

    public WorkoutService(WorkoutSessionRepository sessionRepository,
                          WorkoutSetRepository setRepository,
                          ExerciseRepository exerciseRepository,
                          AnalysisProperties analysisProperties,
                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.setRepository = setRepository;
        this.exerciseRepository = exerciseRepository;
        this.analysisProperties = analysisProperties;
        this.clock = clock;
    }

    /**
     * 그날 기록 열기. 없으면 만들고, 있으면 그 기록을 준다.
     *
     * <p>날짜를 생략하면 오늘이다. 단 어제 기록의 마지막 세트가 구간 간격(30분) 안이면 어젯밤
     * 운동이 자정을 넘겨 이어지는 중이므로 어제 기록을 연다.
     *
     * <p>두 번 열어도 기록은 하나다 — 응답이 유실돼 다시 보내도 같은 기록이 온다.
     */
    @Transactional
    public OpenDayResult openDay(Long userId, OpenDayRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate performedOn = request.performedOn() != null ? request.performedOn() : dayToContinue(userId, today);
        if (performedOn.isAfter(today)) {
            // 미리 짜두는 운동은 "계획"이고 이 테이블은 "기록"만 담는다.
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "미래 날짜로는 기록할 수 없습니다.");
        }

        boolean created = sessionRepository.insertIfAbsent(userId, performedOn) == 1;
        WorkoutSession session = sessionRepository.findByUserIdAndPerformedOn(userId, performedOn)
                .orElseThrow(() -> new IllegalStateException("방금 연 기록이 없다"));
        return new OpenDayResult(
                toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId())), created);
    }

    /** 자정 넘김. 어제 마지막 세트가 구간 간격 안이면 어제, 아니면 오늘 */
    private LocalDate dayToContinue(Long userId, LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Duration gap = analysisProperties.durationPolicy().boutGap();
        boolean continuing = sessionRepository.findByUserIdAndPerformedOn(userId, yesterday)
                .map(session -> setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId()))
                .filter(sets -> !sets.isEmpty())
                .map(sets -> Duration.between(sets.get(sets.size() - 1).getRecordedAt(), now).compareTo(gap) <= 0)
                .orElse(false);
        return continuing ? yesterday : today;
    }

    /**
     * 세트 저장.
     *
     * <p>같은 {@code clientSetId}가 다시 오면 저장하지 않고 기존 세트를 그대로 돌려준다.
     * 본문이 달라도 기존 값을 유지한다 — 이 키는 "같은 완료 체크 한 번"을 뜻하므로
     * 값 수정은 재전송이 아니라 {@code PATCH}가 할 일이다.
     */
    @Transactional
    public SetSaveResult addSet(Long userId, Long sessionId, CreateSetRequest request) {
        mustFindSession(userId, sessionId);

        Optional<WorkoutSet> duplicate = setRepository.findBySessionIdAndClientSetId(sessionId, request.clientSetId());
        if (duplicate.isPresent()) {
            return new SetSaveResult(toSetResponse(duplicate.get()), false);
        }

        // 남의 내 종목·숨긴 내 종목은 없는 종목이다(「내종목_설계서」 5.2)
        Exercise exercise = exerciseRepository.findVisible(request.exerciseId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다."));
        MeasureType measureType = exercise.getMeasureType();
        validateMeasureInput(measureType, request.weightKg(), request.reps(), request.durationSec());
        validateFailTarget(measureType, request.warmupOrDefault(), request.reps(), request.targetReps());

        // 순번은 서버가 매긴다. 클라이언트가 정하게 두면 같은 번호를 두 번 보내
        // 1세트가 두 줄이 된다 — 화면이 세트를 셀 근거가 없어진다. 카드(종목, 회차)마다 센다
        short round = request.roundOrDefault();
        short setNo = (short) (setRepository
                .countBySessionIdAndExerciseIdAndRoundNo(sessionId, request.exerciseId(), round) + 1);

        // 측정 유형이 쓰지 않는 값은 담지 않는다. 맨몸 운동에 중량이 들어가면
        // 볼륨 집계가 부풀려져 부족한 부위를 충분한 것으로 오판한다.
        //
        // 같은 키가 거의 동시에 둘 들어오면 위의 조회는 둘 다 "없음"을 본다. UNIQUE 제약이 최종 방어선이고,
        // 충돌한 쪽은 넣지 않고 먼저 저장된 세트를 돌려준다.
        boolean created = setRepository.insertIfAbsent(sessionId, request.exerciseId(), round, setNo,
                measureType.requiresWeight() ? request.weightKg() : null,
                measureType.requiresReps() ? toShort(request.reps()) : null,
                measureType.requiresDuration() ? request.durationSec() : null,
                request.warmupOrDefault(), toShort(request.targetReps()), OffsetDateTime.now(clock),
                request.clientSetId()) == 1;
        WorkoutSet saved = setRepository.findBySessionIdAndClientSetId(sessionId, request.clientSetId())
                .orElseThrow(() -> new IllegalStateException("방금 저장한 세트가 없다"));
        return new SetSaveResult(SetResponse.of(saved, exercise.getNameKo()), created);
    }

    /**
     * 다른 앱에서 가져온 하루를 넣는다(「기록가져오기_설계서」 7장). 기록 열기와 세트 저장을 한 번에 한다.
     *
     * <p>세트 하나하나는 {@link #addSet}과 같은 규칙이다 — 보이는 종목만(아니면 {@code 404}), 기록 방식이 꼭 받는
     * 값이 있어야 하고({@code 400}), 쓰지 않는 값은 버리며, 같은 {@code clientSetId}는 다시 넣지 않는다.
     * 저장 시각은 {@link ImportedSetTimes}가 정한다.
     *
     * <p>그날 세트가 이미 있으면 다음 회차로 넣는다(V23, 결정 기록 D-22) — 앞서 한 운동과 카드가 섞이지 않게.
     *
     * <p>그날 세트가 하나도 없었으면 {@code minutes}를 그날 운동 시간으로 둔다(결정 5) — 가져온 세트는 몇 초 사이에
     * 저장돼 산출 시간이 1분 남짓이 된다. 이미 기록이 있던 날은 그 시간을 덮지 않는다.
     */
    @Transactional
    public ImportDayResult importDay(Long userId, LocalDate date, Integer minutes, List<ImportedSet> sets) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "미래 날짜로는 기록할 수 없습니다.");
        }
        sessionRepository.insertIfAbsent(userId, date);
        WorkoutSession session = sessionRepository.findByUserIdAndPerformedOn(userId, date)
                .orElseThrow(() -> new IllegalStateException("방금 연 기록이 없다"));
        List<WorkoutSet> existing = setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId());
        OffsetDateTime start = ImportedSetTimes.start(date, clock.getZone(),
                existing.isEmpty() ? null : existing.get(existing.size() - 1).getRecordedAt(), sets.size(), now);

        short round = (short) (existing.isEmpty() ? 1 : Math.min(WorkoutDtos.MAX_ROUND,
                existing.stream().mapToInt(WorkoutSet::getRoundNo).max().orElse(0) + 1));

        Map<Long, Exercise> exercises = new HashMap<>();
        int added = 0;
        for (int i = 0; i < sets.size(); i++) {
            ImportedSet set = sets.get(i);
            Exercise exercise = exercises.computeIfAbsent(set.exerciseId(), id -> exerciseRepository
                    .findVisible(id, userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다.")));
            MeasureType measureType = exercise.getMeasureType();
            validateMeasureInput(measureType, set.weightKg(), set.reps(), set.durationSec());

            short setNo = (short) (setRepository.countBySessionIdAndExerciseIdAndRoundNo(
                    session.getId(), exercise.getId(), round) + 1);
            // 실패 세트의 목표는 맞을 때만 둔다 — 바꾸기로 시간 종목을 골랐으면 목표 없이 본세트로
            boolean failFits = set.targetReps() != null && measureType.requiresReps() && !set.warmup()
                    && set.reps() != null && set.targetReps() > set.reps();
            added += setRepository.insertIfAbsent(session.getId(), exercise.getId(), round, setNo,
                    measureType.requiresWeight() ? set.weightKg() : null,
                    measureType.requiresReps() ? toShort(set.reps()) : null,
                    measureType.requiresDuration() ? set.durationSec() : null,
                    set.warmup(), failFits ? toShort(set.targetReps()) : null, start.plusSeconds(i), set.clientSetId());
        }

        if (existing.isEmpty() && added > 0 && minutes != null && minutes > 0) {
            session.changeDurationOverrideSec(minutes * 60);
        }
        return new ImportDayResult(session.getId(), added, sets.size() - added);
    }

    /**
     * 하루 기록. 그룹 멤버의 기록을 날짜로 열 때 쓴다. 운동하지 않은 날이면 빈 값이다.
     *
     * <p>권한은 보지 않는다 — 부르는 쪽(그룹)이 같은 그룹 멤버인지 먼저 확인한다.
     */
    public Optional<SessionResponse> dayOf(Long userId, LocalDate date) {
        return sessionRepository.findByUserIdAndPerformedOn(userId, date)
                .map(session -> toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId())))
                .filter(day -> !day.exercises().isEmpty());
    }

    /** 세션 상세. */
    public SessionResponse getSession(Long userId, Long sessionId) {
        WorkoutSession session = mustFindSession(userId, sessionId);
        return toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(sessionId));
    }

    /**
     * 히스토리 목록. 운동한 날만 준다.
     *
     * <p>날마다 세트를 조회하면 페이지 크기만큼 쿼리가 나간다. 페이지의 기록 ID를
     * 모아 한 번에 읽고 메모리에서 묶는다.
     */
    public Page<SessionSummary> history(Long userId, LocalDate from, LocalDate to,
                                        Long exerciseId, Pageable pageable) {
        Page<WorkoutSession> page = sessionRepository.search(userId, from, to, exerciseId, pageable);
        if (page.isEmpty()) {
            return page.map(session -> toSummary(session, List.of(), Map.of()));
        }

        List<Long> sessionIds = page.getContent().stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> sets = setRepository.findBySessionIdInOrderByRecordedAtAsc(sessionIds);
        Map<Long, Exercise> exercises = loadExercises(sets);
        Map<Long, List<WorkoutSet>> bySession = sets.stream()
                .collect(Collectors.groupingBy(WorkoutSet::getSessionId));

        return page.map(session ->
                toSummary(session, bySession.getOrDefault(session.getId(), List.of()), exercises));
    }

    /**
     * 직전 수행 기록.
     *
     * <p>기록이 없으면 빈 값이고 컨트롤러가 204로 바꾼다 — 오류가 아니라
     * "처음 하는 종목"이라는 정상 상태다.
     *
     * <p>그날 그 종목을 여러 회차에 했으면 마지막 회차의 세트를 준다(결정 기록 D-22).
     */
    public Optional<LastPerformanceResponse> findLastPerformance(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findVisible(exerciseId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다."));

        List<WorkoutSession> sessions = sessionRepository.findLastPerformed(
                userId, exerciseId, LocalDate.now(clock), PageRequest.of(0, 1));
        if (sessions.isEmpty()) {
            return Optional.empty();
        }

        WorkoutSession session = sessions.get(0);
        List<WorkoutSet> all = setRepository
                .findBySessionIdAndExerciseIdOrderByRoundNoAscSetNoAsc(session.getId(), exerciseId);
        int lastRound = all.stream().mapToInt(WorkoutSet::getRoundNo).max().orElse(1);
        List<LastPerformanceSet> sets = all.stream()
                .filter(set -> set.getRoundNo() == lastRound)
                .map(LastPerformanceSet::of)
                .toList();

        return Optional.of(new LastPerformanceResponse(exerciseId, exercise.getNameKo(),
                exercise.getMeasureType(), session.getId(), session.getPerformedOn(), sets));
    }

    /**
     * 종목 하나의 기록. 그 종목을 한 날마다 그날의 세트를 담는다. 오늘 포함, 최신순.
     *
     * <p>세트는 페이지의 날들을 한 번에 읽는다. 날마다 읽으면 페이지 크기만큼 쿼리가 나간다.
     */
    public Page<ExerciseHistoryDay> exerciseHistory(Long userId, Long exerciseId, Pageable pageable) {
        if (exerciseRepository.findVisible(exerciseId, userId).isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다.");
        }
        Page<WorkoutSession> page = sessionRepository.findPerformed(userId, exerciseId, pageable);
        List<Long> sessionIds = page.getContent().stream().map(WorkoutSession::getId).toList();
        Map<Long, List<LastPerformanceSet>> setsBySession = sessionIds.isEmpty()
                ? Map.of()
                : setRepository.findBySessionIdInAndExerciseIdOrderByRoundNoAscSetNoAsc(sessionIds, exerciseId).stream()
                        .collect(Collectors.groupingBy(WorkoutSet::getSessionId,
                                Collectors.mapping(LastPerformanceSet::of, Collectors.toList())));
        return page.map(session -> new ExerciseHistoryDay(session.getId(), session.getPerformedOn(),
                setsBySession.getOrDefault(session.getId(), List.of())));
    }

    /** 캘린더 월별 요약. 운동한 날만 담는다. */
    public CalendarResponse calendar(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<CalendarDay> days = sessionRepository.findRecorded(
                        List.of(userId), yearMonth.atDay(1), yearMonth.atEndOfMonth()).stream()
                .map(WorkoutSession::getPerformedOn)
                .sorted()
                .map(CalendarDay::new)
                .toList();
        return new CalendarResponse(year, month, days);
    }

    /**
     * 여러 날의 시각과 시간을 한 번에 계산한다. 그룹의 이번 주 운동 시간처럼 세트 없이 날짜만
     * 읽은 곳에서 쓴다. 날마다 세트를 읽으면 날 수만큼 쿼리가 나간다.
     */
    public Map<Long, DayTimes> timesOf(Collection<WorkoutSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<WorkoutSet>> bySession = setRepository
                .findBySessionIdInOrderByRecordedAtAsc(sessions.stream().map(WorkoutSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(WorkoutSet::getSessionId));
        return sessions.stream().collect(Collectors.toMap(WorkoutSession::getId,
                session -> times(session, bySession.getOrDefault(session.getId(), List.of()))));
    }

    /** 하루 기록 수정. 보내지 않은 항목은 건드리지 않는다. */
    @Transactional
    public SessionResponse updateSession(Long userId, Long sessionId, UpdateSessionRequest request) {
        WorkoutSession session = mustFindSession(userId, sessionId);

        if (request.memo() != null) {
            // 빈 문자열이 "메모 지우기"다. 아래 시간 보정의 0과 같은 규칙이다.
            session.changeMemo(request.memo().isBlank() ? null : request.memo());
        }

        if (request.performedOn() != null && !request.performedOn().isEqual(session.getPerformedOn())) {
            if (request.performedOn().isAfter(LocalDate.now(clock))) {
                throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "미래 날짜로는 기록할 수 없습니다.");
            }
            // 하루에 기록은 하나다. 합치지 않고 막는다 — 두 날의 메모와 시간 보정을 어떻게 합칠지
            // 서버가 정할 수 없다
            if (sessionRepository.existsByUserIdAndPerformedOn(userId, request.performedOn())) {
                throw new ApiException(ErrorCode.DAY_ALREADY_RECORDED, "그 날짜에는 이미 기록이 있습니다.");
            }
            session.changePerformedOn(request.performedOn());
        }

        if (request.durationOverrideSec() != null) {
            // 0은 보정 해제다 — 0초짜리 운동은 없으므로 값으로 쓰일 일이 없다.
            int override = request.durationOverrideSec();
            session.changeDurationOverrideSec(override == 0 ? null : override);
        }

        if (request.cardOrder() != null) {
            // (종목, 회차)만 담는다. 세트가 없는 카드(화면의 계획 종목)도 받는다 — 그 카드에 세트가 생기면 그 자리에 온다.
            // 남의 종목 id가 섞여도 내 기록에는 그 종목의 세트가 없어 읽을 때 걸러진다
            session.changeCardOrder(request.cardOrder().stream()
                    .map(card -> ExerciseOrder.key(card.exerciseId(), card.round()))
                    .toList());
        } else if (request.exerciseOrder() != null) {
            // 회차 전의 화면이 보내는 종목 순서 — 모두 1회차다
            session.changeCardOrder(request.exerciseOrder().stream().map(id -> ExerciseOrder.key(id, 1)).toList());
        }

        return toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(sessionId));
    }

    /** 하루 기록 삭제. 세트는 FK {@code ON DELETE CASCADE}로 함께 지워진다. */
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        sessionRepository.delete(mustFindSession(userId, sessionId));
    }

    /** 세트 수정. */
    @Transactional
    public SetResponse updateSet(Long userId, Long sessionId, Long setId, UpdateSetRequest request) {
        mustFindSession(userId, sessionId);
        WorkoutSet set = mustFindSet(sessionId, setId);
        Exercise exercise = exerciseRepository.findById(set.getExerciseId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다."));
        MeasureType measureType = exercise.getMeasureType();

        // 저장 때와 같은 기준으로 거른다. 쓰지 않는 값은 수정으로도 들어가지 않는다.
        if (request.targetReps() != null && request.targetReps() > 0 && !measureType.requiresReps()) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "횟수로 재지 않는 종목은 실패 세트로 둘 수 없습니다.");
        }
        if (request.targetReps() != null && request.targetReps() > 0 && Boolean.TRUE.equals(request.isWarmup())) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "워밍업 세트는 실패 세트로 둘 수 없습니다.");
        }
        // 고친 뒤의 값으로 먼저 본다 — 세트에 반영한 뒤 거르면 실패해도 바뀐 값이 남아 다음 저장에 섞일 수 있다
        Short reps = measureType.requiresReps() && request.reps() != null ? toShort(request.reps()) : set.getReps();
        boolean warmup = request.isWarmup() != null ? request.isWarmup() : set.isWarmup();
        Integer target = request.targetReps() == null
                ? (set.getTargetReps() == null ? null : set.getTargetReps().intValue())
                : (request.targetReps() == 0 ? null : request.targetReps());
        if (!warmup && target != null && (reps == null || reps >= target)) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "실패 세트는 해낸 횟수가 목표보다 적어야 합니다.");
        }
        set.modify(measureType.requiresWeight() ? request.weightKg() : null,
                measureType.requiresReps() ? toShort(request.reps()) : null,
                measureType.requiresDuration() ? request.durationSec() : null,
                request.isWarmup(), request.targetReps());
        return SetResponse.of(set, exercise.getNameKo());
    }

    /**
     * 세트 삭제.
     *
     * <p>마지막 세트를 지워도 기록은 남는다 — 메모가 있을 수 있다. 세트가 없는 날은
     * 목록·캘린더·횟수에 나오지 않는다.
     * {@code setNo}는 재부여하지 않아 번호에 구멍이 생기지만, 화면이 배열 순번으로
     * 그리므로 사용자에게는 보이지 않는다.
     */
    @Transactional
    public void deleteSet(Long userId, Long sessionId, Long setId) {
        mustFindSession(userId, sessionId);
        setRepository.delete(mustFindSet(sessionId, setId));
    }

    /**
     * 카드 단위 삭제. 그 회차의 그 종목 세트를 한 번에 지운다. 다른 회차의 같은 종목은 남는다.
     *
     * <p>세트 삭제만으로도 같은 결과를 낼 수 있지만, 5세트를 지우려면
     * 요청이 다섯 번 나가고 중간에 실패하면 일부만 지워진 상태가 남는다.
     * 기록 화면에서 "이 종목 빼기"는 세트 하나를 지우는 것보다 흔한 동작이다.
     */
    @Transactional
    public void deleteExercise(Long userId, Long sessionId, Long exerciseId, int round) {
        WorkoutSession session = mustFindSession(userId, sessionId);
        int deleted = setRepository.deleteBySessionIdAndExerciseIdAndRoundNo(sessionId, exerciseId, (short) round);
        if (deleted == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "이 기록에 해당 종목이 없습니다.");
        }
        // 다시 넣으면 화면처럼 맨 뒤에 오게 순서에서도 뺀다
        session.dropFromCardOrder(exerciseId, round);
    }

    private WorkoutSet mustFindSet(Long sessionId, Long setId) {
        return setRepository.findByIdAndSessionId(setId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "세트를 찾을 수 없습니다."));
    }

    private SessionSummary toSummary(WorkoutSession session, List<WorkoutSet> sets,
                                     Map<Long, Exercise> exercises) {
        // sets가 저장 시각 순이므로 distinct()가 곧 수행 순서다. 사용자가 정한 순서가 있으면 그것이 앞선다
        // 회차를 묻지 않는다 — 그날 한 종목을 처음 나온 카드 자리에 한 번씩
        List<ExerciseRef> refs = ExerciseOrder.apply(sets.stream()
                .map(WorkoutSet::getExerciseId)
                .distinct()
                .map(id -> new ExerciseRef(id, exercises.containsKey(id) ? exercises.get(id).getNameKo() : null))
                .toList(), ref -> String.valueOf(ref.id()), ExerciseOrder.exerciseKeys(session.getCardOrder()));

        return new SessionSummary(session.getId(), session.getPerformedOn(),
                times(session, sets).effectiveSec(session), sets.size(), refs.size(), refs);
    }

    private WorkoutSession mustFindSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "운동 기록을 찾을 수 없습니다."));
    }

    private static void validateMeasureInput(MeasureType measureType, BigDecimal weightKg, Integer reps,
                                             Integer durationSec) {
        if (measureType.requiresWeight() && weightKg == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 중량이 필요합니다.");
        }
        if (measureType.requiresReps() && reps == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 횟수가 필요합니다.");
        }
        if (measureType.requiresDuration() && durationSec == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 수행 시간이 필요합니다.");
        }
    }

    /**
     * 실패 세트(결정 기록 D-15)를 저장할 수 있는지. 횟수로 재는 종목만, 워밍업이 아니고, 해낸 횟수가 목표보다 적어야 한다.
     */
    private static void validateFailTarget(MeasureType measureType, boolean warmup, Integer reps, Integer targetReps) {
        if (targetReps == null) {
            return;
        }
        if (!measureType.requiresReps()) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "횟수로 재지 않는 종목은 실패 세트로 둘 수 없습니다.");
        }
        if (warmup) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "워밍업 세트는 실패 세트로 둘 수 없습니다.");
        }
        if (reps == null || reps >= targetReps) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "실패 세트는 해낸 횟수가 목표보다 적어야 합니다.");
        }
    }

    /** 회차마다 첫 세트 시각과 운동 시간. 세트가 있는 회차만, 회차 순서대로 */
    private List<RoundTimes> roundTimes(WorkoutSession session, List<WorkoutSet> sets) {
        Map<Integer, List<WorkoutSet>> byRound = new TreeMap<>();
        for (WorkoutSet set : sets) {
            byRound.computeIfAbsent((int) set.getRoundNo(), key -> new ArrayList<>()).add(set);
        }
        return byRound.entrySet().stream()
                .map(entry -> {
                    DayTimes times = times(session, entry.getValue());
                    return new RoundTimes(entry.getKey(), times.startedAt(), times.durationSec());
                })
                .toList();
    }

    /** 세트 저장 시각으로 그날의 시작·끝 시각과 운동 시간을 계산한다 */
    private DayTimes times(WorkoutSession session, List<WorkoutSet> sets) {
        if (sets.isEmpty()) {
            return new DayTimes(null, null, null);
        }
        List<OffsetDateTime> recordedAts = sets.stream().map(WorkoutSet::getRecordedAt).sorted().toList();
        return new DayTimes(recordedAts.get(0), recordedAts.get(recordedAts.size() - 1),
                SessionDuration.of(recordedAts, session.getPerformedOn(), clock.getZone(),
                        analysisProperties.durationPolicy()));
    }

    private SetResponse toSetResponse(WorkoutSet set) {
        String name = exerciseRepository.findById(set.getExerciseId())
                .map(Exercise::getNameKo)
                .orElse(null);
        return SetResponse.of(set, name);
    }

    /**
     * 하루 기록과 세트를 상세 응답의 형태로 묶는다.
     *
     * <p>세트가 저장 시각 오름차순으로 들어오므로 {@link LinkedHashMap}에 쌓으면
     * 카드 순서가 곧 <b>수행 순서</b>가 된다(각 카드의 가장 이른 저장 시각 순). 사용자가 순서를 바꿨으면
     * 그 순서가 앞선다({@link ExerciseOrder}). 카드는 (종목, 회차)이고, 마지막에 회차 순으로 다시 세운다 —
     * 회차 안의 순서는 그대로다.
     */
    private SessionResponse toResponse(WorkoutSession session, List<WorkoutSet> sets) {
        return toResponse(session, sets, loadExercises(sets));
    }

    private SessionResponse toResponse(WorkoutSession session, List<WorkoutSet> sets, Map<Long, Exercise> exercises) {
        Map<String, List<WorkoutSet>> byCard = new LinkedHashMap<>();
        for (WorkoutSet set : sets) {
            byCard.computeIfAbsent(ExerciseOrder.key(set.getExerciseId(), set.getRoundNo()), key -> new ArrayList<>())
                    .add(set);
        }

        List<ExerciseGroup> ordered = ExerciseOrder.apply(byCard.values().stream()
                .map(cardSets -> {
                    WorkoutSet first = cardSets.get(0);
                    Exercise exercise = exercises.get(first.getExerciseId());
                    return new ExerciseGroup(first.getExerciseId(), first.getRoundNo(),
                            exercise == null ? null : exercise.getNameKo(),
                            exercise == null ? null : exercise.getNameEn(),
                            exercise == null ? null : exercise.getMeasureType(),
                            cardSets.stream().map(SetInGroup::of).toList());
                })
                .toList(), group -> ExerciseOrder.key(group.exerciseId(), group.round()), session.getCardOrder());
        // 안정 정렬 — 회차 안의 순서는 그대로
        List<ExerciseGroup> groups = ordered.stream()
                .sorted(Comparator.comparingInt(ExerciseGroup::round))
                .toList();

        return SessionResponse.of(session, times(session, sets), groups, roundTimes(session, sets));
    }

    /** 종목을 한 번에 읽는다. 세트마다 조회하면 세트 수만큼 쿼리가 나간다 */
    private Map<Long, Exercise> loadExercises(List<WorkoutSet> sets) {
        if (sets.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = sets.stream().map(WorkoutSet::getExerciseId).distinct().toList();
        return exerciseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }
}
