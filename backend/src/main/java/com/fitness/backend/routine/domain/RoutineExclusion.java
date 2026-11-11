package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.RecoveryEvaluation;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 회복 때문에 오늘 루틴에서 빠지는 부위. DB·스프링에 의존하지 않는 순수 계산이다.
 *
 * <p>루틴({@link SlotPlanner})과 분석 탭의 회복 지도가 같이 쓴다. 지도가 이 규칙을 따로 흉내 내면
 * "지도는 회복됐다는데 루틴은 그 부위를 뺀다"가 다시 생긴다.
 *
 * <pre>
 *   회복 중인 판정 부위        → 빠진다 (SELF_RECOVERING)
 *   그 부위와 같은 묶음의 부위  → 회복됐어도 같이 빠진다 (BUNDLE_RECOVERING)
 *   코어                      → 묶음이 없어 코어만 빠지고, 다른 부위 때문에 빠지지도 않는다
 *   종아리·전완                → 루틴이 원래 고르지 않는 부위라 빠진다고 하지 않고, 다른 부위를 빼지도 않는다
 * </pre>
 *
 * <p>묶음을 통째로 빼는 이유 — 어제 가슴만 했어도 오늘 삼두를 하면 미는 동작이 이틀 이어진다.
 */
public final class RoutineExclusion {

    public enum Reason {
        /** 이 부위가 회복 중이다. */
        SELF_RECOVERING,
        /** 이 부위는 회복 중이 아니지만 같은 묶음의 다른 부위가 회복 중이다. */
        BUNDLE_RECOVERING
    }

    /**
     * @param reason 빠지는 이유
     * @param causes 같은 묶음에서 회복 중인 부위({@code MuscleGroup} 순서). 본인이 회복 중이면 비어 있다
     */
    public record Excluded(Reason reason, List<MuscleGroup> causes) {
    }

    private RoutineExclusion() {
    }

    /**
     * @param lastTrained  부위별 마지막 운동일(오늘 포함). 없는 부위는 한 번도 안 한 것으로 본다
     * @param recoveryDays 오늘 포함 이 일수 안에 한 부위가 회복 중이다. 0이면 아무것도 빠지지 않는다
     * @return 빠지는 부위만 담는다. 여기 없는 부위는 회복 때문에 빠지지는 않는다
     */
    public static Map<MuscleGroup, Excluded> of(Map<MuscleGroup, LocalDate> lastTrained, LocalDate today,
                                                int recoveryDays) {
        List<MuscleGroup> recovering = Arrays.stream(MuscleGroup.values())
                .filter(MuscleGroup::judged)
                .filter(muscle -> RecoveryEvaluation.recovering(lastTrained.get(muscle), today, recoveryDays))
                .toList();

        Map<MuscleGroup, Excluded> excluded = new EnumMap<>(MuscleGroup.class);
        for (MuscleGroup muscle : MuscleGroup.values()) {
            if (!muscle.judged()) {
                continue;
            }
            if (recovering.contains(muscle)) {
                excluded.put(muscle, new Excluded(Reason.SELF_RECOVERING, List.of()));
                continue;
            }
            List<MuscleGroup> causes = MuscleBundle.of(muscle)
                    .map(bundle -> recovering.stream().filter(bundle::contains).toList())
                    .orElse(List.of());
            if (!causes.isEmpty()) {
                excluded.put(muscle, new Excluded(Reason.BUNDLE_RECOVERING, causes));
            }
        }
        return excluded;
    }
}
