package com.fitness.backend.analysis.service;

import com.fitness.backend.analysis.domain.AveragingBasis;
import com.fitness.backend.analysis.domain.BalanceEvaluation;
import com.fitness.backend.analysis.domain.BalancePair;
import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.ConfidenceLevel;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.SummaryBadge;
import com.fitness.backend.analysis.domain.TierGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.analysis.repository.DailyMuscleSetCount;
import com.fitness.backend.analysis.repository.MuscleSetCount;
import com.fitness.backend.analysis.repository.MuscleVolumeRepository;
import com.fitness.backend.analysis.web.AnalysisDtos.BalanceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.ChildResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.ConfidenceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.DisplayOnlyResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleVolumeResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.PairResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.SideResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.TierResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.WeekRange;
import com.fitness.backend.analysis.web.AnalysisDtos.WeeklyGroup;
import com.fitness.backend.analysis.web.AnalysisDtos.WeeklyVolumeResponse;
import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.workout.domain.SessionStatus;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부위별 볼륨 판정과 균형 판정. 명세 8.2 · 8.3.
 *
 * <p>여기서 하는 일은 <b>집계와 조립</b>뿐이다. 구간 분류·배지 결정·비율 판정은
 * {@code analysis.domain}의 순수 함수가 맡는다. 판정 규칙이 DB 접근과 섞이면
 * 경계값(10.0·20.0·2.0배)을 테스트로 고정할 수 없고, 이 작품에서 "계산 방법은
 * 직접 설계했다"고 말할 수 있는 근거가 거기에 있다.
 */
@Service
@Transactional(readOnly = true)
public class AnalysisService {

    /** 주간 기록 기본 주 수. 완료된 7주 + 이번 주 — 목업 통계 탭의 "완료된 7주"와 같다 */
    private static final int DEFAULT_WEEKLY_WEEKS = 8;

    private final MuscleVolumeRepository volumeRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final AnalysisProperties properties;
    private final Clock clock;

    public AnalysisService(MuscleVolumeRepository volumeRepository,
                           WorkoutSessionRepository sessionRepository,
                           AnalysisProperties properties,
                           Clock clock) {
        this.volumeRepository = volumeRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 명세 8.2. 상위 6종 + 하위 9종 + 표시 전용 2종을 완성해 내려준다. */
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

        return new MuscleVolumeResponse(
                period.referenceDate(), period.weeks(), period.from(), period.to(),
                period.basisDays(), period.to().minusDays(period.basisDays() - 1L),
                aggregate.shoulderSplitResolved(), confidence(userId, period),
                List.copyOf(tiers), List.copyOf(displayOnly));
    }

    /** 명세 8.3. 계산 재료는 8.2와 같은 주당 평균 세트다. */
    public BalanceResponse balance(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        Period period = period(userId, weeksParam, referenceDateParam);
        Aggregate aggregate = aggregate(userId, period);
        BigDecimal threshold = properties.balanceRatioThreshold();

        List<PairResponse> pairs = new ArrayList<>();
        for (BalancePair pair : BalancePair.values()) {
            SideResponse left = toSide(pair.left(), aggregate, period.basisDays());
            SideResponse right = toSide(pair.right(), aggregate, period.basisDays());
            BalanceEvaluation evaluation =
                    BalanceEvaluation.of(left.weeklySets(), right.weeklySets(), threshold);

            pairs.add(new PairResponse(
                    pair, pair.label(), left, right,
                    biggerSide(pair, left.weeklySets(), right.weeklySets()),
                    evaluation.ratio(), evaluation.smallerSideZero(),
                    evaluation.verdict(), evaluation.verdict().label()));
        }

        return new BalanceResponse(period.referenceDate(), period.weeks(), period.basisDays(), threshold,
                aggregate.shoulderSplitResolved(), List.copyOf(pairs));
    }

    /**
     * 주차별 부위 세트 (LOG-30).
     *
     * <p>판정을 붙이지 않는다. 판정은 28일 롤링 평균으로만 한다 — 주 단위로 판정하면
     * 월요일마다 전부 0이 되고 수요일엔 대부분 "부족"이 된다(분석 설계서 2.1). 이건
     * "주마다 얼마나 했나"를 보는 기록이고, 그래서 주당 평균이 아니라 그 주에 한 세트
     * 수를 그대로 준다.
     *
     * <p>주는 월요일에 시작한다(명세 1.2). 마지막 주는 기준일이 속한 주라 아직 끝나지
     * 않았을 수 있다({@code inProgress}).
     */
    public WeeklyVolumeResponse weeklyVolume(Long userId, Integer weeksParam, LocalDate referenceDateParam) {
        int weeks = weeksParam == null ? DEFAULT_WEEKLY_WEEKS : weeksParam;
        LocalDate reference = referenceDateParam == null ? LocalDate.now(clock) : referenceDateParam;
        LocalDate lastMonday = reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstMonday = lastMonday.minusWeeks(weeks - 1L);

        // 기준일 뒤로는 세지 않는다. 과거 시점으로 조회할 때 그 뒤의 기록이 섞이면 안 된다
        Map<MuscleGroup, long[]> byGroup = new EnumMap<>(MuscleGroup.class);
        for (MuscleGroup group : MuscleGroup.values()) {
            byGroup.put(group, new long[weeks]);
        }
        for (DailyMuscleSetCount row : volumeRepository.aggregateByDay(userId, firstMonday, reference)) {
            int index = (int) ChronoUnit.WEEKS.between(firstMonday, row.performedOn());
            MuscleGroup.of(row.primaryMuscle(), row.deltRegion())
                    .ifPresent(group -> byGroup.get(group)[index] += row.sets());
        }

        List<WeekRange> ranges = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate start = firstMonday.plusWeeks(i);
            LocalDate end = start.plusDays(6);
            LocalDate countTo = end.isAfter(reference) ? reference : end;
            int done = sessionRepository.countByUserIdAndStatusAndPerformedOnBetween(
                    userId, SessionStatus.DONE, start, countTo);
            ranges.add(new WeekRange(start, end, reference.isBefore(end), done));
        }

        List<WeeklyGroup> groups = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            List<Long> sets = new ArrayList<>();
            for (long value : byGroup.get(group)) {
                sets.add(value);
            }
            groups.add(new WeeklyGroup(group, group.label(), group.judged(), List.copyOf(sets)));
        }
        return new WeeklyVolumeResponse(reference, List.copyOf(ranges), List.copyOf(groups));
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
        // 몰아넣는 폴백을 쓰지 않기로 한 대가다(명세 8.1).
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

        // 하위가 둘이면 상위 판정을 비운다. "팔 12세트 · 최적"이 삼두 0세트를 가리는
        // 것이 LOG-09를 쓴 이유이므로, 배지로 "일부 부족"만 알리고 판정은 하위에 맡긴다.
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
        return new SideResponse(side, side.label(), weeklyAverage(total, basisDays), side.components());
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
        int done = sessionRepository.countByUserIdAndStatusAndPerformedOnBetween(
                userId, SessionStatus.DONE, period.from(), period.to());

        if (done >= threshold) {
            // 충분할 때 띄울 문구는 없다. 있으면 화면이 늘 무언가를 보여줘야 한다.
            return new ConfidenceResponse(ConfidenceLevel.NORMAL, done, threshold, null);
        }
        return new ConfidenceResponse(ConfidenceLevel.LOW, done, threshold,
                "최근 %d주 완료된 운동이 %d회로 적어 판정 신뢰도가 낮습니다.".formatted(period.weeks(), done));
    }

    private BigDecimal weeklyAverage(long totalSets, int basisDays) {
        return VolumeVerdict.weeklyAverageOverDays(Math.toIntExact(totalSets), basisDays);
    }

    // ---------- 집계 구간 ----------

    /**
     * 기준일을 <b>포함해</b> 과거로 {@code weeks × 7}일(LOG-13, 명세 8.2).
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
        // 분모는 구간 길이가 아니라 기록을 시작한 뒤 지난 기간이다(LOG-31). 4주가 넘었으면 같다
        int basisDays = AveragingBasis.days(
                sessionRepository.findFirstDonePerformedOn(userId, to), to, windowDays, properties.minBasisDays());
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
