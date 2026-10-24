package com.fitness.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 생성 시각만 갖는 엔티티의 공통 상위 타입. 시각은 JPA 경유 INSERT에서 Hibernate가 채운다.
 *
 * <p>컬럼이 {@code TIMESTAMPTZ}라 {@link OffsetDateTime}으로 받는다. {@code LocalDateTime}으로 받으면
 * 오프셋이 사라져 응답의 시각이 서버 기본 시간대에 기대게 된다.
 *
 * <p><b>주의</b>: {@code workout_sets.recorded_at}은 이 상위 타입을 쓰지 않는다. 감사 값이 아니라
 * 운동 시간 계산에 쓰는 도메인 값이라 세트를 고쳐도 바뀌면 안 된다.
 */
@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
