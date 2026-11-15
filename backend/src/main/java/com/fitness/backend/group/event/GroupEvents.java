package com.fitness.backend.group.event;

import java.time.LocalDate;

/**
 * 그룹에서 일어난 일. {@code GroupService}가 트랜잭션 안에서 내고, 알림({@code notification})이 커밋된 뒤에
 * 받아 보낸다 — 알림이 실패해도 응원·참여·나가기는 그대로 성공한다.
 */
public final class GroupEvents {

    private GroupEvents() {
    }

    /** 멤버가 다른 멤버의 기록에 응원했다. 같은 사람이 같은 기록에 다시 누르면 내지 않는다 */
    public record CheerAdded(Long groupId, Long recipientUserId, Long cheererUserId, LocalDate performedOn) {
    }

    /** 새 멤버가 들어왔다 — 방장에게 알린다 */
    public record MemberJoined(Long groupId, String groupName, Long ownerId, Long memberUserId) {
    }

    /** 방장이 나가거나 탈퇴해 방장이 넘어왔다 — 새 방장에게 알린다 */
    public record OwnershipTransferred(Long groupId, String groupName, Long newOwnerId) {
    }
}
