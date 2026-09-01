package com.fitness.backend.user.domain;

/**
 * 훈련 목표. 부록 A {@code user.goal}.
 *
 * <p><b>체중 목표(증량/유지/감량)가 아니다.</b> 목업이 체중 축으로 그려 두었으나
 * 체중 목표는 분석·추천 어디에도 쓰이지 않아 훈련 목표로 교체했다(명세 9.2).
 *
 * <p>프로필에 남은 유일한 항목이다. 루틴 추천의 출력이 "운동명·세트·반복 횟수·순서"
 * 인데 반복 횟수와 강도 범위를 ACSM이 목표별로 다르게 제시하므로, 목표를 모르면
 * 정할 근거가 없다(LOG-14).
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
