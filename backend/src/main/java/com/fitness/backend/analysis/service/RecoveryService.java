package com.fitness.backend.analysis.service;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.RecoveryEvaluation;
import com.fitness.backend.analysis.domain.RecoveryState;
import com.fitness.backend.analysis.repository.MuscleDaySetCount;
import com.fitness.backend.analysis.repository.MuscleVolumeRepository;
import com.fitness.backend.analysis.web.AnalysisDtos.RecoveryMuscleResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.RecoveryResponse;
import com.fitness.backend.common.config.AnalysisProperties;
import com.fitness.backend.routine.RoutineProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부위별 회복 상태(「회복지도_설계서」).
 *
 * <p>회복 일수와 "많이 함"의 기준은 <b>루틴 설정에서 받는다</b>({@code app.routine.recovery-days},
 * {@code per-muscle-max-sets}). 지도가 루틴과 다른 값을 쓰면 루틴이 빼는 부위를 지도는 회복됐다고 한다.
 * 마지막 운동일을 찾는 기간은 분석과 같은 {@code app.analysis.weeks}주다.
 */
@Service
@Transactional(readOnly = true)
public class RecoveryService {

    private final MuscleVolumeRepository volumeRepository;
    private final AnalysisProperties analysisProperties;
    private final RoutineProperties routineProperties;
    private final Clock clock;

    public RecoveryService(MuscleVolumeRepository volumeRepository,
                           AnalysisProperties analysisProperties,
                           RoutineProperties routineProperties,
                           Clock clock) {
        this.volumeRepository = volumeRepository;
        this.analysisProperties = analysisProperties;
        this.routineProperties = routineProperties;
        this.clock = clock;
    }

    public RecoveryResponse recovery(Long userId) {
        LocalDate today = LocalDate.now(clock);
        int recoveryDays = routineProperties.recoveryDays();
        int heavySets = routineProperties.perMuscleMaxSets();
        LocalDate from = today.minusDays(analysisProperties.weeks() * 7L - 1);
        LocalDate recentFrom = today.minusDays(Math.max(recoveryDays, 1) - 1L);

        Map<MuscleGroup, LocalDate> lastTrained = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, Long> recentSets = new EnumMap<>(MuscleGroup.class);
        for (MuscleDaySetCount row : volumeRepository.aggregateByDay(userId, from, today)) {
            MuscleGroup.of(row.primaryMuscle(), row.deltRegion()).ifPresent(group -> {
                lastTrained.merge(group, row.performedOn(), (a, b) -> a.isAfter(b) ? a : b);
                if (recoveryDays > 0 && !row.performedOn().isBefore(recentFrom)) {
                    recentSets.merge(group, row.sets(), Long::sum);
                }
            });
        }

        List<RecoveryMuscleResponse> muscles = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            LocalDate last = lastTrained.get(group);
            long sets = recentSets.getOrDefault(group, 0L);
            RecoveryState state = RecoveryEvaluation.evaluate(last, sets, today, recoveryDays, heavySets);
            muscles.add(new RecoveryMuscleResponse(
                    group, group.label(), group.judged(), state, state.label(), last,
                    last == null ? null : RecoveryEvaluation.daysSince(last, today), sets));
        }
        return new RecoveryResponse(today, recoveryDays, heavySets, List.copyOf(muscles));
    }
}
