package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.MuscleGroup;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 하루에 같이 하는 부위 묶음. 코어는 어느 묶음에도 들지 않고 남는 자리에 붙는다.
 *
 * <p>불균형 판정의 하체·밀기·당기기를 그대로 쓴다 — 묶음 하나가 불균형의 한쪽이라, 적은 쪽 묶음을
 * 하는 날이 곧 불균형을 줄이는 날이다. 종아리는 판정 부위가 아니라 빠진다.
 */
public enum MuscleBundle {

    LOWER(BalanceSide.LOWER),
    PUSH(BalanceSide.PUSH),
    PULL(BalanceSide.PULL);

    private final List<MuscleGroup> muscles;

    MuscleBundle(BalanceSide side) {
        this.muscles = side.components().stream().filter(MuscleGroup::judged).toList();
    }

    public boolean contains(MuscleGroup muscle) {
        return muscles.contains(muscle);
    }

    /** 코어처럼 묶음이 없는 부위는 비어 있다 */
    public static Optional<MuscleBundle> of(MuscleGroup muscle) {
        return Arrays.stream(values()).filter(b -> b.contains(muscle)).findFirst();
    }
}
