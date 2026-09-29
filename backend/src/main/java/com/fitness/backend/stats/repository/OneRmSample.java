package com.fitness.backend.stats.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1RM 추정에 쓰는 세트 한 줄. 명세 7.1.
 *
 * <p>추정값을 DB에서 계산하지 않고 재료만 꺼내 온다 — Epley 공식과 {@code reps ≤ 12}
 * 경계는 설정값이라(8.5) SQL에 박으면 조정이 안 되고, 무엇보다
 * {@link com.fitness.backend.stats.domain.OneRepMax} 단위 테스트가 실제 계산 경로를
 * 덮지 못하게 된다.
 */
public record OneRmSample(LocalDate performedOn, BigDecimal weightKg, short reps) {
}
