package com.fitness.backend.group.domain;

import com.fitness.backend.common.entity.BaseTimeEntity;
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
 * 친구 그룹. {@code friend_groups} (V8, LOG-38).
 *
 * <p>엔티티 이름을 {@code Group}으로 두지 않는다 — JPQL에서 {@code GROUP}은 예약어라
 * {@code select g from Group g}가 파싱되지 않는다.
 *
 * <p>멤버를 {@code @OneToMany}로 달지 않는다. 다른 엔티티와 같이 {@link GroupMember}가
 * {@code groupId}를 값으로 갖고, 필요한 곳에서 리포지토리로 읽는다.
 */
@Entity
@Getter
@Table(name = "friend_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendGroup extends BaseTimeEntity {

    public static final int NAME_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = InviteCode.LENGTH)
    private String inviteCode;

    /** 방장. 이름 변경·코드 재발급·내보내기·삭제를 할 수 있다 */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    private FriendGroup(String name, String inviteCode, Long ownerId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.ownerId = ownerId;
    }

    public static FriendGroup create(String name, String inviteCode, Long ownerId) {
        return new FriendGroup(name, inviteCode, ownerId);
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public void rename(String name) {
        this.name = name;
    }

    /** 코드가 엉뚱한 곳에 퍼졌을 때. 옛 코드로는 더 들어올 수 없다 */
    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public void transferOwnership(Long userId) {
        this.ownerId = userId;
    }
}
