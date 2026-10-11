package com.fitness.backend.group.repository;

/** 세션별 응원 수. 화면에 보이는 세션들을 한 번에 센다 */
public record CheerCount(Long sessionId, long count) {
}
