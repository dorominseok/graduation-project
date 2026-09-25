package com.fitness.backend.workout.domain;

/**
 * 세션 진행 상태. 부록 A {@code workout_sessions.status} (V1).
 *
 * <p>{@code DRAFT}는 사용자당 하나만 존재할 수 있다(명세 6.2). 진행 중인 세션을
 * 끝내야 다음 세션을 시작할 수 있고, 이 제약이 {@code LIVE} 생성의 재전송을 막는다.
 */
public enum SessionStatus {

    /** 기록 중. 세트를 계속 붙일 수 있다 */
    DRAFT,

    /** 종료됨. 이 시점에 운동 시간이 산출된다(명세 6.3) */
    DONE
}
