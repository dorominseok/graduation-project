package com.fitness.backend.workout.service;

import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.workout.domain.SessionDuration;
import com.fitness.backend.workout.domain.SessionSource;
import com.fitness.backend.workout.domain.SessionStatus;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.domain.WorkoutSet;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import com.fitness.backend.workout.repository.WorkoutSetRepository;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSessionRequest;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseGroup;
import com.fitness.backend.workout.web.WorkoutDtos.SessionResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SetInGroup;
import com.fitness.backend.workout.web.WorkoutDtos.SetResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SetSaveResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 기록. 명세 6.2~6.7.
 *
 * <p>기본 흐름은 <b>세션 생성 → 세트 저장(반복) → 세션 종료</b>다. 프론트는 사용자가
 * 첫 세트를 완료 체크하는 순간 세션을 만들고 이어서 세트를 보낸다 — 계획 행은
 * 서버로 오지 않는다(LOG-05).
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
     * 세션 생성(명세 6.2).
     *
     * <p>{@code LIVE}는 오늘 날짜만 받고 {@code DRAFT} 중복을 막는다. 이 두 제약이
     * 재전송을 자연히 걸러낸다 — 응답이 유실돼 다시 보내도 두 번째는 409다.
     * {@code BACKFILL}은 하루 복수 세션이 허용되므로 같은 방어가 없고, 멱등 키는
     * 미결 과제다(명세 6.2 주석, 9.1).
     */
    @Transactional
    public SessionResponse createSession(Long userId, CreateSessionRequest request) {
        SessionSource source = request.sourceOrDefault();
        LocalDate today = LocalDate.now(clock);
        LocalDate performedOn = request.performedOn();

        if (performedOn.isAfter(today)) {
            // 미리 짜두는 운동은 "계획"이고 이 테이블은 "기록"만 담는다(LOG-05).
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "미래 날짜로는 기록할 수 없습니다.");
        }
        if (source == SessionSource.LIVE) {
            if (!performedOn.isEqual(today)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "진행 중인 운동은 오늘 날짜로만 기록합니다.");
            }
            if (sessionRepository.existsByUserIdAndStatus(userId, SessionStatus.DRAFT)) {
                throw new ApiException(ErrorCode.DRAFT_SESSION_EXISTS,
                        "진행 중인 운동이 있습니다. 이어서 기록하거나 종료해주세요.");
            }
        }

        WorkoutSession session = sessionRepository.save(
                WorkoutSession.start(userId, performedOn, source, request.routineId()));
        return SessionResponse.of(session, List.of());
    }

    /**
     * 세트 저장(명세 6.4).
     *
     * <p>같은 {@code clientSetId}가 다시 오면 저장하지 않고 기존 세트를 그대로 돌려준다.
     * 본문이 달라도 기존 값을 유지한다 — 이 키는 "같은 완료 체크 한 번"을 뜻하므로
     * 값 수정은 재전송이 아니라 {@code PATCH}가 할 일이다.
     */
    @Transactional
    public SetSaveResult addSet(Long userId, Long sessionId, CreateSetRequest request) {
        WorkoutSession session = mustFindSession(userId, sessionId);

        Optional<WorkoutSet> duplicate = setRepository.findBySessionIdAndClientSetId(sessionId, request.clientSetId());
        if (duplicate.isPresent()) {
            return new SetSaveResult(toSetResponse(duplicate.get()), false);
        }

        Exercise exercise = exerciseRepository.findById(request.exerciseId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "종목을 찾을 수 없습니다."));
        MeasureType measureType = exercise.getMeasureType();
        validateMeasureInput(measureType, request);

        OffsetDateTime recordedAt = OffsetDateTime.now(clock);
        short setNo = request.setNo() != null
                ? request.setNo().shortValue()
                : (short) (setRepository.countBySessionIdAndExerciseId(sessionId, request.exerciseId()) + 1);

        // 측정 유형이 쓰지 않는 값은 담지 않는다. 맨몸 운동에 중량이 들어가면
        // 볼륨 집계가 부풀려져 부족한 부위를 충분한 것으로 오판한다.
        WorkoutSet set = WorkoutSet.record(sessionId, request.exerciseId(), setNo,
                measureType.requiresWeight() ? request.weightKg() : null,
                measureType.requiresReps() ? toShort(request.reps()) : null,
                measureType.requiresDuration() ? request.durationSec() : null,
                request.warmupOrDefault(), recordedAt, request.clientSetId());

        WorkoutSet saved;
        try {
            saved = setRepository.saveAndFlush(set);
        } catch (DataIntegrityViolationException e) {
            // 같은 키가 거의 동시에 둘 들어온 경우. UNIQUE 제약이 최종 방어선이고,
            // 여기서는 먼저 저장된 쪽을 돌려준다.
            WorkoutSet existing = setRepository.findBySessionIdAndClientSetId(sessionId, request.clientSetId())
                    .orElseThrow(() -> e);
            return new SetSaveResult(toSetResponse(existing), false);
        }

        session.markStartedAt(recordedAt);
        if (session.isDone() && session.isLive()) {
            // 종료한 세션에 사후로 세트를 붙인 경우(기록 방식 5.3). 시간이 달라진다.
            recalculateDuration(session);
        }
        return new SetSaveResult(SetResponse.of(saved, exercise.getNameKo()), true);
    }

    /**
     * 세션 종료(명세 6.3). {@code DRAFT} → {@code DONE}이며 이 시점에 운동 시간을 산출한다.
     *
     * <p>종료 시각을 벽시계가 아니라 마지막 세트의 저장 시각으로 두는 것은,
     * 운동을 끝내고 한참 뒤에 종료를 누르면 그 공백이 운동 시간에 들어가기 때문이다.
     */
    @Transactional
    public SessionResponse complete(Long userId, Long sessionId) {
        WorkoutSession session = mustFindSession(userId, sessionId);
        if (session.isDone()) {
            throw new ApiException(ErrorCode.SESSION_ALREADY_COMPLETED, "이미 종료한 운동입니다.");
        }

        List<WorkoutSet> sets = setRepository.findBySessionIdOrderByRecordedAtAsc(sessionId);
        if (sets.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_SESSION, "저장된 세트가 없어 종료할 수 없습니다.");
        }

        OffsetDateTime lastRecordedAt = sets.get(sets.size() - 1).getRecordedAt();
        // BACKFILL은 저장 시각이 실제 운동 시각과 무관해 산출하지 않는다(기록 방식 4.3).
        Duration duration = session.isLive() ? computeDuration(sets) : null;
        session.complete(lastRecordedAt, duration);

        return toResponse(session, sets);
    }

    /** 세션 상세(명세 6.5). */
    public SessionResponse getSession(Long userId, Long sessionId) {
        WorkoutSession session = mustFindSession(userId, sessionId);
        return toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(sessionId));
    }

    /** 진행 중 세션 이어쓰기(명세 6.7). 없으면 빈 값이고 컨트롤러가 204로 바꾼다. */
    public Optional<SessionResponse> findCurrent(Long userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.DRAFT)
                .map(session -> toResponse(session, setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId())));
    }

    private WorkoutSession mustFindSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "운동 기록을 찾을 수 없습니다."));
    }

    private void validateMeasureInput(MeasureType measureType, CreateSetRequest request) {
        if (measureType.requiresWeight() && request.weightKg() == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 중량이 필요합니다.");
        }
        if (measureType.requiresReps() && request.reps() == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 횟수가 필요합니다.");
        }
        if (measureType.requiresDuration() && request.durationSec() == null) {
            throw new ApiException(ErrorCode.INVALID_MEASURE_INPUT, "이 종목은 수행 시간이 필요합니다.");
        }
    }

    private void recalculateDuration(WorkoutSession session) {
        List<WorkoutSet> sets = setRepository.findBySessionIdOrderByRecordedAtAsc(session.getId());
        session.recalculateDuration(sets.isEmpty() ? null : computeDuration(sets));
    }

    private Duration computeDuration(List<WorkoutSet> sets) {
        return SessionDuration.of(sets.stream().map(WorkoutSet::getRecordedAt).toList(),
                analysisProperties.durationPolicy());
    }

    private SetResponse toSetResponse(WorkoutSet set) {
        String name = exerciseRepository.findById(set.getExerciseId())
                .map(Exercise::getNameKo)
                .orElse(null);
        return SetResponse.of(set, name);
    }

    /**
     * 세션과 세트를 명세 6.5의 형태로 묶는다.
     *
     * <p>세트가 저장 시각 오름차순으로 들어오므로 {@link LinkedHashMap}에 쌓으면
     * 종목 순서가 곧 <b>수행 순서</b>가 된다(각 종목의 가장 이른 저장 시각 순).
     */
    private SessionResponse toResponse(WorkoutSession session, List<WorkoutSet> sets) {
        Map<Long, Exercise> exercises = loadExercises(sets);

        Map<Long, List<WorkoutSet>> byExercise = new LinkedHashMap<>();
        for (WorkoutSet set : sets) {
            byExercise.computeIfAbsent(set.getExerciseId(), key -> new ArrayList<>()).add(set);
        }

        List<ExerciseGroup> groups = byExercise.entrySet().stream()
                .map(entry -> {
                    Exercise exercise = exercises.get(entry.getKey());
                    return new ExerciseGroup(entry.getKey(),
                            exercise == null ? null : exercise.getNameKo(),
                            exercise == null ? null : exercise.getMeasureType(),
                            entry.getValue().stream().map(SetInGroup::of).toList());
                })
                .toList();

        return SessionResponse.of(session, groups);
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
