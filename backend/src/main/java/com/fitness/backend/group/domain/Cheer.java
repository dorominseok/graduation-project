package com.fitness.backend.group.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 응원. {@code cheers} (V8). 그룹 멤버의 운동 한 번에 한 사람이 한 번 누른다.
 *
 * <p>그룹이 아니라 세션에 붙는다 — 같은 사람과 그룹 두 개를 함께해도 그 사람의
 * 운동은 하나이고, 어느 그룹에서 눌렀든 같은 응원이다.
 */
@Entity
@Getter
@Table(name = "cheers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cheer extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 응원한 사람 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Cheer(Long sessionId, Long userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public static Cheer of(Long sessionId, Long userId) {
        return new Cheer(sessionId, userId);
    }
}
