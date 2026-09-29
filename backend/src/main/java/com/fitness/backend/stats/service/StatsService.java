package com.fitness.backend.stats.service;

import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.stats.domain.OneRepMax;
import com.fitness.backend.stats.repository.OneRmSample;
import com.fitness.backend.stats.repository.OneRmTrendRepository;
import com.fitness.backend.stats.web.StatsDtos.BasedOnSet;
import com.fitness.backend.stats.web.StatsDtos.ExerciseIntensity;
import com.fitness.backend.stats.web.StatsDtos.IntensitySet;
import com.fitness.backend.stats.web.StatsDtos.OneRmTrendResponse;
import com.fitness.backend.stats.web.StatsDtos.SessionIntensityResponse;
import com.fitness.backend.stats.web.StatsDtos.TrendPoint;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.domain.WorkoutSet;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import com.fitness.backend.workout.repository.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추정 1RM 추이와 세션 강도 스냅샷. 명세 7.1 · 7.2.
 *
 * <p>사용자에게 1RM을 직접 묻지 않는다 — 실측은 부상 위험이 있고 대부분 자기 1RM을
 * 모른다. 대신 [중량 × 횟수]에서 Epley 공식으로 추정한다(LOG-07). 공식과 {@code reps}
 * 상한은 {@link OneRepMax}에 있고 여기서는 조회와 조립만 한다.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /**
     * {@code from}·{@code to}를 생략했을 때의 기본 구간(명세 7.1 "생략 시 최근 12주").
     *
     * <p>8.5의 설정값 표에 없어 프로퍼티로 빼지 않았다. 판정에 쓰이는 수치가 아니라
     * 차트의 기본 표시 범위라 조정 근거가 문헌이 아닌 화면이다.
     */
    private static final int DEFAULT_TREND_WEEKS = 12;

    private final OneRmTrendRepository trendRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;
    private final AnalysisProperties properties;
    private final Clock clock;

    public StatsService(OneRmTrendRepository trendRepository,
                        WorkoutSessionRepository sessionRepository,
                        WorkoutSetRepository setRepository,
                        ExerciseRepository exerciseRepository,
                        AnalysisProperties properties,
                        Clock clock) {
        this.trendRepository = trendRepository;
        this.sessionRepository = sessionRepository;
        this.setRepository = setRepository;
        this.exerciseRepository = exerciseRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 명세 7.1. 그날 세트들의 추정값 중 최댓값 하나를 점으로 찍는다. */
    public OneRmTrendResponse oneRmTrend(Long userId, Long exerciseId, LocalDate fromParam, LocalDate toParam) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> ApiException.notFound("종목"));
        if (!exercise.getMeasureType().supportsOneRm()) {
            // 맨몸·시간 종목은 Epley 공식의 전제인 중량이 없다. 0으로 계산해 0.0kg
            // 그래프를 그리느니 요청 자체를 거절하는 쪽이 화면에서 원인이 보인다.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "중량과 횟수를 기록하는 종목만 1RM을 추정할 수 있습니다.");
        }

        LocalDate to = toParam == null ? LocalDate.now(clock) : toParam;
        LocalDate from = fromParam == null ? to.minusDays((long) DEFAULT_TREND_WEEKS * 7 - 1) : fromParam;
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "시작일이 종료일보다 늦습니다.");
        }

        List<OneRmSample> samples = trendRepository.findSamples(
                userId, exerciseId, from, to, properties.oneRmMaxReps());

        // 쿼리가 날짜 오름차순이라 삽입 순서가 곧 표시 순서다. 같은 날짜를 다시 넣어도
        // LinkedHashMap은 자리를 옮기지 않으므로 정렬을 한 번 더 할 필요가 없다.
        Map<LocalDate, TrendPoint> best = new LinkedHashMap<>();
        for (OneRmSample sample : samples) {
            BigDecimal estimate = OneRepMax.epley(sample.weightKg(), sample.reps());
            TrendPoint current = best.get(sample.performedOn());
            if (current == null || estimate.compareTo(current.estimatedOneRm()) > 0) {
                best.put(sample.performedOn(), new TrendPoint(sample.performedOn(), estimate,
                        new BasedOnSet(sample.weightKg(), sample.reps())));
            }
        }

        return new OneRmTrendResponse(exercise.getId(), exercise.getNameKo(), "kg",
                from, to, List.copyOf(best.values()));
    }

    /** 명세 7.2. 날짜 사이를 잇지 않는 그날 하루의 스냅샷이다. */
    public SessionIntensityResponse sessionIntensity(Long userId, Long sessionId) {
        WorkoutSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("운동 기록"));

        // 종목 등장 순서는 저장 순서다 — 화면의 종목 목록과 같은 차례로 보여야 한다.
        Map<Long, List<WorkoutSet>> byExercise = new LinkedHashMap<>();
        for (WorkoutSet set : setRepository.findBySessionIdOrderByRecordedAtAsc(sessionId)) {
            byExercise.computeIfAbsent(set.getExerciseId(), key -> new ArrayList<>()).add(set);
        }

        Map<Long, Exercise> exercises = exerciseRepository.findAllById(byExercise.keySet()).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<ExerciseIntensity> result = new ArrayList<>();
        byExercise.forEach((exerciseId, sets) -> {
            Exercise exercise = exercises.get(exerciseId);
            BigDecimal oneRm = estimateOneRm(exercise, sets);

            List<IntensitySet> rows = sets.stream()
                    .sorted(Comparator.comparing(WorkoutSet::getSetNo))
                    .map(set -> new IntensitySet(
                            set.getSetNo(), set.getWeightKg(), set.getReps(), set.getDurationSec(),
                            set.isWarmup(), OneRepMax.intensityPercent(set.getWeightKg(), oneRm)))
                    .toList();

            result.add(new ExerciseIntensity(exerciseId,
                    exercise == null ? null : exercise.getNameKo(),
                    exercise == null ? null : exercise.getMeasureType(),
                    oneRm, rows));
        });

        return new SessionIntensityResponse(session.getId(), session.getPerformedOn(), List.copyOf(result));
    }

    /**
     * 그날 그 종목의 추정 1RM. 7.1과 같은 규칙이다.
     *
     * <p>워밍업도 계산에 넣는다(명세 7.2) — 중량이 낮아 최댓값이 될 일이 거의 없고,
     * 빼면 워밍업으로 잘못 체크된 본세트가 통째로 빠져 강도가 100%를 넘게 나온다.
     */
    private BigDecimal estimateOneRm(Exercise exercise, List<WorkoutSet> sets) {
        if (exercise == null || !exercise.getMeasureType().supportsOneRm()) {
            return null;
        }
        BigDecimal max = null;
        for (WorkoutSet set : sets) {
            if (set.getWeightKg() == null || set.getReps() == null
                    || !OneRepMax.isEligible(set.getReps(), properties.oneRmMaxReps())) {
                continue;
            }
            BigDecimal estimate = OneRepMax.epley(set.getWeightKg(), set.getReps());
            if (max == null || estimate.compareTo(max) > 0) {
                max = estimate;
            }
        }
        return max;
    }
}
