package com.fitness.backend.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 멤버십. {@code friend_group_members} (V8).
 *
 * <p>{@code joinedAt}은 감사(audit) 값이 아니라 <b>방장을 넘길 순서</b>라는 도메인 값이라
 * 공통 상위 타입의 {@code created_at}을 쓰지 않고 서비스가 시계로 채운다.
 */
@Entity
@Getter
@Table(name = "friend_group_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    private GroupMember(Long groupId, Long userId, OffsetDateTime joinedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public static GroupMember join(Long groupId, Long userId, OffsetDateTime joinedAt) {
        return new GroupMember(groupId, userId, joinedAt);
    }
}
