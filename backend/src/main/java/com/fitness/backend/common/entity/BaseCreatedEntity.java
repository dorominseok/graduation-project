package com.fitness.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 생성 시각만 갖는 엔티티의 공통 상위 타입.
 *
 * <p>V1·V3의 {@code exercises} / {@code refresh_tokens} /
 * {@code user_favorite_exercises}가 여기에 해당한다. 스키마가
 * {@code DEFAULT now()}를 갖고 있으나 그것은 SQL로 직접 INSERT 할 때의
 * 방어선이고, JPA 경유 INSERT에서는 Hibernate가 채운다.
 *
 * <p>컬럼 타입이 {@code TIMESTAMPTZ}이므로 {@link OffsetDateTime}으로 받는다.
 * {@code LocalDateTime}으로 받으면 오프셋이 소실되어, API 명세서 1.2가 정한
 * "ISO 8601 오프셋 포함" 직렬화를 서버 기본 시간대에 의존하게 된다.
 *
 * <p><b>주의</b>: {@code workout_sets.recorded_at}은 이 상위 타입을 쓰지 않는다.
 * 이름만 비슷할 뿐 감사(audit) 값이 아니라 운동 시간 산출에 쓰이는
 * <b>도메인 값</b>이며(「운동기록_방식_설계서」 4.2), 세트 수정 시에도
 * 갱신되면 안 된다.
 */
@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
