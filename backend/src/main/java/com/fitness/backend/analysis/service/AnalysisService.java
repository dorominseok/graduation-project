package com.fitness.backend.analysis.service;

import com.fitness.backend.analysis.domain.AveragingBasis;
import com.fitness.backend.analysis.domain.BalanceEvaluation;
import com.fitness.backend.analysis.domain.BalancePair;
import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.ConfidenceLevel;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.SummaryBadge;
import com.fitness.backend.analysis.domain.TierGroup;
import com.fitness.backend.analysis.domain.VolumeThresholds;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.analysis.repository.MuscleDaySetCount;
import com.fitness.backend.analysis.repository.MuscleSetCount;
import com.fitness.backend.analysis.repository.MuscleVolumeRepository;
import com.fitness.backend.analysis.web.AnalysisDtos.BalanceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.ChildResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.ConfidenceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.DisplayOnlyResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.FrequencyResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleTrendResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleTrendRowResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleVolumeResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.PairResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.SideResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.TierResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.TrendThresholdsResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.TrendWeekResponse;
import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.routine.RoutineProperties;
import com.fitness.backend.routine.domain.RoutinePolicy;
import com.fitness.backend.routine.domain.TrainingFrequency;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부위별 볼륨 판정과 균형 판정.
 *
 * <p>여기서 하는 일은 <b>집계와 조립</b>뿐이다. 구간 분류·배지 결정·비율 판정은
 * {@code analysis.domain}의 순수 함수가 맡는다 — 판정 규칙이 DB 접근과 섞이면
 * 경계값(10.0·20.0·2.0배)을 테스트로 고정할 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class AnalysisService {

    private final MuscleVolumeRepository volumeRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final AnalysisProperties properties;
    private final RoutineProperties routineProperties;
    private final Clock clock;

    public AnalysisService(MuscleVolumeRepository volumeRepository,
                           WorkoutSessionRepository sessionRepository,
                           AnalysisProperties properties,
                           RoutineProperties routineProperties,
                           Clock clock) {
        this.volumeRepository = volumeRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.routineProperties = routineProperties;
        this.clock = clock;
    }

    /** 부위별 볼륨. 상위 6종 + 하위 9종 + 표시 전용 2종을 완성해 내려준다. */
    public MuscleVolumeResponse muscleVolume(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        Period period = period(userId, weeksParam, referenceDateParam);
        Aggregate aggregate = aggregate(userId, period);

        List<TierResponse> tiers = new ArrayList<>();
        for (TierGroup tier : TierGroup.values()) {
            tiers.add(toTier(tier, aggregate, period.basisDays()));
        }

        List<DisplayOnlyResponse> displayOnly = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            if (!group.judged()) {
                long sets = aggregate.total(group);
                displayOnly.add(new DisplayOnlyResponse(
                        group, group.label(), weeklyAverage(sets, period.basisDays()), sets));
            }
        }

        ConfidenceResponse confidence = confidence(userId, period);
        return new MuscleVolumeResponse(
                period.referenceDate(), period.weeks(), period.from(), period.to(),
                period.basisDays(), period.to().minusDays(period.basisDays() - 1L),
                aggregate.shoulderSplitResolved(), confidence,
                List.copyOf(tiers), List.copyOf(displayOnly),
                frequency(userId, period, confidence.workoutDayCount()));
    }

    /**
     * 주당 운동 일수({@link TrainingFrequency} — 기준일까지 꽉 찬 주 단위로 세고 반올림). 오늘의 루틴이 이 값을
     * 그대로 받아 부위당 세트를 정하므로, 화면에 적는 일수와 루틴이 쓰는 일수가 같다.
     *
     * @param knownDays 집계 구간에서 운동한 날. 너무 적으면 모른다고 보고 기본값을 쓴다
     */
    private FrequencyResponse frequency(Long userId, Period period, int knownDays) {
        RoutinePolicy policy = routineProperties.policy(properties.volume().optimalThrough());
        int fullWeeks = TrainingFrequency.fullWeeks(period.basisDays());
        int daysInFullWeeks = sessionRepository.countRecordedDays(
                userId, period.to().minusDays(fullWeeks * 7L - 1), period.to());
        int daysPerWeek = TrainingFrequency.daysPerWeek(knownDays, daysInFullWeeks, fullWeeks, policy);
        return new FrequencyResponse(knownDays >= policy.minKnownDays(), daysPerWeek, policy.dailyMaxSets(),
                TrainingFrequency.maxWeeklySetsPerMuscle(daysPerWeek, policy), policy.targetWeeklySets(),
                TrainingFrequency.daysForTarget(policy));
    }

    /** 주간 추이의 기본 주 수 — 최근 4주와 그 앞 4주를 나란히 본다 */
    public static final int TREND_WEEKS = 8;

    /**
     * 부위별 주간 추이. 기준일을 포함해 7일씩 끊은 {@code weeks}개 주마다 본세트를 센다(오래된 주부터).
     *
     * <p>주를 달력(월~일)이 아니라 기준일에서 7일씩 끊는다. 그래야 마지막 네 주의 합이 부위별 볼륨의 집계
     * 구간(기준일 포함 28일)과 같아져 약점 탭의 숫자와 어긋나지 않는다. 달력 주로 끊으면 월요일에는 이번 주
     * 막대가 거의 비어 "줄었다"로 읽힌다. 세트 조건(본세트·수행일 기준)도 볼륨과 같은 쿼리를 쓴다.
     *
     * <p>첫 기록 전의 주는 0세트가 아니라 기록 전으로 표시한다 — 막 시작한 사람에게 "7주 동안 안 했다"로
     * 보이지 않게.
     */
    public MuscleTrendResponse muscleTrend(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        int weeks = weeksParam == null ? TREND_WEEKS : weeksParam;
        LocalDate to = referenceDateParam == null ? LocalDate.now(clock) : referenceDateParam;
        LocalDate from = to.minusDays(weeks * 7L - 1);
        LocalDate firstRecordedOn = sessionRepository.findFirstRecordedOn(userId, to);

        Map<MuscleGroup, long[]> sets = new EnumMap<>(MuscleGroup.class);
        for (MuscleDaySetCount row : volumeRepository.aggregateByDay(userId, from, to)) {
            MuscleGroup.of(row.primaryMuscle(), row.deltRegion()).ifPresent(group -> {
                int week = (int) (ChronoUnit.DAYS.between(from, row.performedOn()) / 7);
                sets.computeIfAbsent(group, g -> new long[weeks])[week] += row.sets();
            });
        }

        List<TrendWeekResponse> periods = new ArrayList<>();
        for (int week = 0; week < weeks; week++) {
            LocalDate start = from.plusDays(week * 7L);
            LocalDate end = start.plusDays(6);
            periods.add(new TrendWeekResponse(start, end, firstRecordedOn == null || end.isBefore(firstRecordedOn)));
        }

        // 최근 평균은 부위별 볼륨과 같은 구간·같은 분모로 낸다 — 약점 탭의 주당 세트와 같은 값이 나온다
        int recentWeeks = properties.weeks();
        int basisDays = AveragingBasis.days(firstRecordedOn, to, recentWeeks * 7, properties.minBasisDays());
        // 그 앞 구간은 통째로 기록한 뒤일 때만 — 기록 전 날을 "안 한 날"로 나누면 평균이 낮게 나온다
        LocalDate previousFrom = to.minusDays(recentWeeks * 14L - 1);
        boolean previousKnown = weeks >= recentWeeks * 2
                && firstRecordedOn != null && !firstRecordedOn.isAfter(previousFrom);

        VolumeThresholds thresholds = properties.volumeThresholds();
        List<MuscleTrendRowResponse> groups = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            if (!group.judged()) {
                continue;
            }
            long[] weekly = sets.getOrDefault(group, new long[weeks]);
            BigDecimal recent = weeks >= recentWeeks
                    ? weeklyAverage(sum(weekly, weeks - recentWeeks, weeks), basisDays)
                    : null;
            BigDecimal previous = previousKnown
                    ? weeklyAverage(sum(weekly, weeks - recentWeeks * 2, weeks - recentWeeks), recentWeeks * 7)
                    : null;
            // 부위의 판정은 4주 평균으로 한다(약점 탭과 같다). 주별 구간은 막대 색용 — 약점 탭의 막대처럼 막대마다
            // 그 길이가 닿은 구간 색을 입힌다. 12세트 한 주를 4주 평균이 부족이라고 빨갛게 칠하지 않게
            VolumeVerdict verdict = recent == null ? null : VolumeVerdict.classify(recent, thresholds);
            List<VolumeVerdict> weeklyVerdicts = new ArrayList<>();
            for (int week = 0; week < weeks; week++) {
                weeklyVerdicts.add(periods.get(week).beforeFirstRecord()
                        ? null : VolumeVerdict.classify(BigDecimal.valueOf(weekly[week]), thresholds));
            }
            groups.add(new MuscleTrendRowResponse(group, group.label(),
                    Arrays.stream(weekly).boxed().toList(), Collections.unmodifiableList(weeklyVerdicts),
                    recent, verdict, verdict == null ? null : verdict.label(), previous));
        }

        return new MuscleTrendResponse(to, weeks, firstRecordedOn,
                new TrendThresholdsResponse(thresholds.insufficientBelow(), thresholds.optimalFrom(),
                        thresholds.optimalThrough()),
                List.copyOf(periods), List.copyOf(groups));
    }

    private static long sum(long[] values, int fromInclusive, int toExclusive) {
        long total = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            total += values[i];
        }
        return total;
    }

    /** 균형 판정. 계산 재료는 부위별 볼륨과 같은 주당 평균 세트다. */
    public BalanceResponse balance(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        Period period = period(userId, weeksParam, referenceDateParam);
        Aggregate aggregate = aggregate(userId, period);
        BigDecimal threshold = properties.balanceRatioThreshold();

        List<PairResponse> pairs = new ArrayList<>();
        for (BalancePair pair : BalancePair.values()) {
            SideResponse left = toSide(pair.left(), aggregate, period.basisDays());
            SideResponse right = toSide(pair.right(), aggregate, period.basisDays());
            // 부위당 평균의 비율 = 왼쪽 합 × 오른쪽 부위 수 ÷ (오른쪽 합 × 왼쪽 부위 수).
            // 반올림한 평균끼리 나누지 않고 합에 곱해 맞대야 3 대 3인 밀기/당기기가 전과 같은 값이 된다
            BigDecimal leftScaled = left.weeklySets().multiply(BigDecimal.valueOf(pair.right().components().size()));
            BigDecimal rightScaled = right.weeklySets().multiply(BigDecimal.valueOf(pair.left().components().size()));
            BalanceEvaluation evaluation = BalanceEvaluation.of(leftScaled, rightScaled, threshold);

            pairs.add(new PairResponse(
                    pair, pair.label(), left, right,
                    biggerSide(pair, leftScaled, rightScaled),
                    evaluation.ratio(), evaluation.smallerSideZero(),
                    evaluation.verdict(), evaluation.verdict().label()));
        }

        return new BalanceResponse(period.referenceDate(), period.weeks(), period.basisDays(), threshold,
                aggregate.shoulderSplitResolved(), List.copyOf(pairs));
    }

    // ---------- 집계 ----------

    /**
     * 쿼리 결과를 판정 부위별 합으로 접는다.
     *
     * <p>같은 판정 부위에 여러 {@code primary_muscle}이 들어온다 — 등은 넷
     * ({@code lats}·{@code middle back}·{@code traps}·{@code lower back})이라
     * 행 단위로 더해야 한다.
     */
    private Aggregate aggregate(Long userId, Period period) {
        Map<MuscleGroup, Long> totals = new EnumMap<>(MuscleGroup.class);
        boolean anyShoulderRow = false;
        boolean anyShoulderResolved = false;

        for (MuscleSetCount row : volumeRepository.aggregate(userId, period.from(), period.to())) {
            if (MuscleGroup.isShoulder(row.primaryMuscle())) {
                anyShoulderRow = true;
                anyShoulderResolved |= row.deltRegion() != null;
            }
            Optional<MuscleGroup> group = MuscleGroup.of(row.primaryMuscle(), row.deltRegion());
            group.ifPresent(g -> totals.merge(g, row.sets(), Long::sum));
        }

        // 어깨 기록이 아예 없으면 해소할 것도 없으므로 true다. 정제가 밀려 delt_region이
        // NULL뿐일 때만 false가 되고, 그때 그 세트들은 집계에서 빠진다 — 전량 앞쪽으로
        // 몰아넣는 폴백을 쓰지 않기로 한 대가다.
        boolean resolved = !anyShoulderRow || anyShoulderResolved;
        return new Aggregate(totals, resolved);
    }

    private TierResponse toTier(TierGroup tier, Aggregate aggregate, int basisDays) {
        List<ChildResponse> children = new ArrayList<>();
        List<VolumeVerdict> verdicts = new ArrayList<>();
        long total = 0;

        for (MuscleGroup child : tier.children()) {
            long sets = aggregate.total(child);
            BigDecimal weekly = weeklyAverage(sets, basisDays);
            VolumeVerdict verdict = VolumeVerdict.classify(weekly, properties.volumeThresholds());

            children.add(new ChildResponse(child, child.label(), weekly, sets, verdict, verdict.label()));
            verdicts.add(verdict);
            total += sets;
        }

        // 하위가 둘이면 상위 판정을 비운다. "팔 12세트 · 최적"이 삼두 0세트를 가리지 않게
        // 배지로 "일부 부족"만 알리고 판정은 하위에 맡긴다.
        boolean hasChildren = tier.hasChildren();
        VolumeVerdict verdict = hasChildren ? null : verdicts.get(0);
        SummaryBadge badge = hasChildren ? SummaryBadge.resolve(verdicts) : null;

        return new TierResponse(
                tier, tier.label(), weeklyAverage(total, basisDays), total, hasChildren,
                verdict, verdict == null ? null : verdict.label(),
                badge, badge == null ? null : badge.label(),
                List.copyOf(children));
    }

    private SideResponse toSide(BalanceSide side, Aggregate aggregate, int basisDays) {
        long total = 0;
        for (MuscleGroup component : side.components()) {
            total += aggregate.total(component);
        }
        // 부위 수만큼 일수를 늘려 나누면 부위당 주 평균이다 (합 × 7 ÷ (일수 × 부위 수))
        BigDecimal perMuscle = weeklyAverage(total, basisDays * side.components().size());
        return new SideResponse(side, side.label(), weeklyAverage(total, basisDays), perMuscle, side.components());
    }

    /** 세트가 많은 쪽. 양쪽 다 0이면 가릴 것이 없어 {@code null}이고, 같으면 왼쪽으로 둔다. */
    private BalanceSide biggerSide(BalancePair pair, BigDecimal left, BigDecimal right) {
        if (left.signum() == 0 && right.signum() == 0) {
            return null;
        }
        return left.compareTo(right) >= 0 ? pair.left() : pair.right();
    }

    private ConfidenceResponse confidence(Long userId, Period period) {
        int threshold = properties.confidenceSessionThreshold();
        int done = sessionRepository.countRecordedDays(userId, period.from(), period.to());

        if (done >= threshold) {
            // 충분할 때 띄울 문구는 없다. 있으면 화면이 늘 무언가를 보여줘야 한다.
            return new ConfidenceResponse(ConfidenceLevel.NORMAL, done, threshold, null);
        }
        return new ConfidenceResponse(ConfidenceLevel.LOW, done, threshold,
                "최근 %d주 운동한 날이 %d일로 적어 판정 신뢰도가 낮습니다.".formatted(period.weeks(), done));
    }

    private BigDecimal weeklyAverage(long totalSets, int basisDays) {
        return VolumeVerdict.weeklyAverageOverDays(Math.toIntExact(totalSets), basisDays);
    }

    // ---------- 집계 구간 ----------

    /**
     * 기준일을 <b>포함해</b> 과거로 {@code weeks × 7}일.
     *
     * <p>하한에서 하루를 빼야 구간 길이가 분모와 맞는다. 빼지 않으면 4주에 29일을
     * 더해 놓고 4로 나누게 되어 주당 평균이 소리 없이 부풀고, 부족 판정이 한 칸씩
     * 위로 밀린다.
     */
    private Period period(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        int weeks = weeksParam == null ? properties.weeks() : weeksParam;
        if (weeks < 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "집계 기간은 1주 이상이어야 합니다.");
        }
        LocalDate to = referenceDateParam == null ? LocalDate.now(clock) : referenceDateParam;
        int windowDays = weeks * 7;
        // 분모는 구간 길이가 아니라 기록을 시작한 뒤 지난 기간이다. 4주가 넘었으면 같다
        int basisDays = AveragingBasis.days(
                sessionRepository.findFirstRecordedOn(userId, to), to, windowDays, properties.minBasisDays());
        return new Period(weeks, to, to.minusDays(windowDays - 1L), to, basisDays);
    }

    /**
     * @param from      집계 구간 시작. 세트는 이 구간에서 센다
     * @param basisDays 주당 평균을 낼 때 나눌 일수. 기록이 짧으면 구간보다 작다
     */
    private record Period(int weeks, LocalDate referenceDate, LocalDate from, LocalDate to, int basisDays) {
    }

    private record Aggregate(Map<MuscleGroup, Long> totals, boolean shoulderSplitResolved) {

        long total(MuscleGroup group) {
            return totals.getOrDefault(group, 0L);
        }
    }
}
