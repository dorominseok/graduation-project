package com.fitness.backend.analysis.domain;

import java.util.List;

/**
 * 균형 판정에서 맞대는 한쪽.
 *
 * <p>구성 부위를 응답에 그대로 실어 보낸다({@code components}). 어느 부위를 더해
 * 이 수치가 나왔는지 화면이 근거로 보여줄 수 있어야 하고, 그래야 "왜 내가 불균형이냐"는
 * 물음에 답이 된다.
 *
 * <p>양쪽은 판정 부위의 <b>부위당 평균</b>으로 맞댄다. 상체는 6부위, 하체는 2부위라 합으로 비교하면
 * 모든 부위가 최적이어도 상체가 3배로 나온다. 평균이면 둘 다 최적 범위(10~20) 안일 때
 * 2배를 넘지 않는다 — 그래서 기준이 2배다.
 */
public enum BalanceSide {

    PUSH("밀기", MuscleGroup.CHEST, MuscleGroup.DELT_FRONT, MuscleGroup.TRICEPS),
    PULL("당기기", MuscleGroup.BACK, MuscleGroup.DELT_REAR, MuscleGroup.BICEPS),

    UPPER("상체", MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.DELT_FRONT,
            MuscleGroup.DELT_REAR, MuscleGroup.TRICEPS, MuscleGroup.BICEPS),

    /** 종아리는 판정 부위가 아니라 뺀다. 평균에 넣으면 종아리를 안 하는 사람의 하체가 낮게 나온다. */
    LOWER("하체", MuscleGroup.QUADS, MuscleGroup.POSTERIOR);

    private final String label;
    private final List<MuscleGroup> components;

    BalanceSide(String label, MuscleGroup... components) {
        this.label = label;
        this.components = List.of(components);
    }

    public String label() {
        return label;
    }

    public List<MuscleGroup> components() {
        return components;
    }
}
