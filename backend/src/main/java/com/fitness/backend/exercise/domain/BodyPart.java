package com.fitness.backend.exercise.domain;

/**
 * 저장·검색·차트용 부위 7종. 목(NECK)은 2026-11-20에 더했다(V19) — 다른 여섯 어디에도 맞지 않는다.
 *
 * <p><b>판정 부위와 다르다.</b> 약점 판정은 {@code primary_muscle} 기반 9종으로
 * 하고, 이 6종은 화면 표시와 필터에 쓴다. 둘을 섞으면 "팔 12세트 최적"
 * 같은 오판이 생긴다.
 */
public enum BodyPart {
    CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, NECK
}
