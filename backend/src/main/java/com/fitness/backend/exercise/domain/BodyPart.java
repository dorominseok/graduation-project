package com.fitness.backend.exercise.domain;

/**
 * 저장·검색·차트용 부위 6종. 부록 A {@code exercises.body_part}.
 *
 * <p><b>판정 부위와 다르다.</b> 약점 판정은 {@code primary_muscle} 기반 9종으로
 * 하고(명세 8.1), 이 6종은 화면 표시와 필터에 쓴다. 둘을 섞으면 "팔 12세트 최적"
 * 같은 오판이 생긴다(LOG-09·LOG-10).
 */
public enum BodyPart {
    CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE
}
