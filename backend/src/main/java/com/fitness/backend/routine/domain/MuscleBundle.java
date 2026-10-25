package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 하루에 같이 하는 부위 묶음. 코어는 어느 묶음에도 들지 않고 남는 자리에 붙는다.
 *
 * <p>밀기·당기기는 분석의 불균형 판정과 같은 구분이다.
 */
public enum MuscleBundle {

    LOWER(MuscleGroup.QUADS, MuscleGroup.POSTERIOR),
    PUSH(MuscleGroup.CHEST, MuscleGroup.DELT_FRONT, MuscleGroup.TRICEPS),
    PULL(MuscleGroup.BACK, MuscleGroup.DELT_REAR, MuscleGroup.BICEPS);

    private final List<MuscleGroup> muscles;

    MuscleBundle(MuscleGroup... muscles) {
        this.muscles = List.of(muscles);
    }

    public boolean contains(MuscleGroup muscle) {
        return muscles.contains(muscle);
    }

    /** 코어처럼 묶음이 없는 부위는 비어 있다 */
    public static Optional<MuscleBundle> of(MuscleGroup muscle) {
        return Arrays.stream(values()).filter(b -> b.contains(muscle)).findFirst();
    }
}
