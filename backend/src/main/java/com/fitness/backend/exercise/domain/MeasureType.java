package com.fitness.backend.exercise.domain;

/**
 * 측정 유형. 세트에 무엇을 입력받을지 정한다(명세 5.1 / 6.4).
 *
 * <p>추정 1RM 대상은 {@link #WEIGHT_REPS} 하나뿐이다 — 나머지는 중량이 없거나
 * (맨몸·시간) 추가 중량이라 Epley 공식의 전제가 서지 않는다.
 */
public enum MeasureType {
    /** 중량 + 횟수. 벤치프레스·스쿼트. 1RM 산출 대상 */
    WEIGHT_REPS,
    /** 횟수만. 푸시업·크런치 */
    BODYWEIGHT_REPS,
    /** 추가 중량 + 횟수. 중량 딥스 */
    WEIGHTED_BODYWEIGHT,
    /** 시간만. 플랭크 */
    TIME;

    /** 추정 1RM을 낼 수 있는 유형인지(명세 7.1). */
    public boolean supportsOneRm() {
        return this == WEIGHT_REPS;
    }
}
