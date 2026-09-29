package com.fitness.backend.analysis.domain;

import java.util.List;

/**
 * 균형 판정에서 맞대는 한쪽. API 명세서 8.3 / 「운동 분석 로직 설계서」 4.3.
 *
 * <p>구성 부위를 응답에 그대로 실어 보낸다({@code components}). 어느 부위를 더해
 * 이 수치가 나왔는지 화면이 근거로 보여줄 수 있어야 하고, 그래야 "왜 내가 불균형이냐"는
 * 물음에 답이 된다.
 */
public enum BalanceSide {

    PUSH("밀기", MuscleGroup.CHEST, MuscleGroup.DELT_FRONT, MuscleGroup.TRICEPS),
    PULL("당기기", MuscleGroup.BACK, MuscleGroup.DELT_REAR, MuscleGroup.BICEPS),

    UPPER("상체", MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.DELT_FRONT,
            MuscleGroup.DELT_REAR, MuscleGroup.TRICEPS, MuscleGroup.BICEPS),

    /** 하체에는 종아리가 들어간다 — 판정 9종 밖이지만 상하체 비교에서는 빼면 하체가 과소평가된다(분석 4.3). */
    LOWER("하체", MuscleGroup.QUADS, MuscleGroup.POSTERIOR, MuscleGroup.CALVES);

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
