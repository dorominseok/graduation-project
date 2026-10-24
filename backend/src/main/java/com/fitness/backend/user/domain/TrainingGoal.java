package com.fitness.backend.user.domain;

/**
 * 훈련 목표.
 *
 * <p><b>체중 목표(증량/유지/감량)가 아니다.</b> 체중은 분석·추천 어디에도 쓰이지 않는다.
 *
 * <p>지금은 화면에서 쓰지 않는다(2026-09-20). 루틴의 반복 범위를 목표가 아니라 측정 유형으로
 * 정해서 쓰는 곳이 없고, 컬럼과 API 필드만 남아 있다.
 */
public enum TrainingGoal {

    /** 근력 — 고중량 저반복 */
    STRENGTH("근력"),
    /** 근비대 */
    HYPERTROPHY("근비대"),
    /** 지구력 — 저중량 고반복 */
    ENDURANCE("지구력"),
    /** 일반 체력 */
    GENERAL_FITNESS("일반 체력");

    private final String label;

    TrainingGoal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
