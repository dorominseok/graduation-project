package com.fitness.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 생성·수정 시각을 함께 갖는 엔티티의 공통 상위 타입.
 *
 * <p>V1의 {@code users} / {@code workout_sessions}가 여기에 해당한다.
 *
 * <p><b>이 클래스를 두는 이유</b>: 두 테이블의 {@code updated_at}은
 * {@code DEFAULT now()}만 갖고 있어 <b>갱신 트리거가 없다.</b> 엔티티마다
 * 수정 시각을 직접 챙기면 빠뜨려도 예외가 나지 않고 화면도 정상으로 보이며,
 * 값이 틀어진 사실은 데이터가 쌓인 뒤에야 드러난다. 상위 타입을 상속하는
 * 형태로 두어 빠뜨릴 수 없게 한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
