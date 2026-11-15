package com.fitness.backend.notification.service;

import com.fitness.backend.group.event.GroupEvents;
import com.fitness.backend.notification.domain.NotificationType;
import com.fitness.backend.notification.service.NotificationSender.Message;
import com.fitness.backend.user.domain.User;
import com.fitness.backend.user.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹에서 일어난 일을 바로 알린다(「알림_설계서」 1·3장) — 응원 받음, 새 멤버(방장에게), 방장 위임(새 방장에게).
 *
 * <p>커밋된 뒤에, 요청 스레드와 따로 보낸다. 푸시 서버가 느리거나 실패해도 응원·참여·나가기는 이미 끝났다.
 * 조용한 시간(22:00~08:00)에 생긴 일은 {@link NotificationSender}가 버린다 — 그룹 화면에서 보인다.
 */
@Component
public class GroupNotificationListener {

    private final NotificationSender sender;
    private final UserRepository userRepository;

    public GroupNotificationListener(NotificationSender sender, UserRepository userRepository) {
        this.sender = sender;
        this.userRepository = userRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheer(GroupEvents.CheerAdded event) {
        String body = "%s님이 %d월 %d일 운동에 응원했어요.".formatted(nickname(event.cheererUserId()),
                event.performedOn().getMonthValue(), event.performedOn().getDayOfMonth());
        sender.send(event.recipientUserId(), NotificationType.CHEER,
                new Message("응원을 받았어요", body, groupPath(event.groupId()), "cheer-" + event.groupId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoin(GroupEvents.MemberJoined event) {
        if (event.ownerId().equals(event.memberUserId())) {
            return;
        }
        sender.send(event.ownerId(), NotificationType.GROUP, new Message(event.groupName(),
                nickname(event.memberUserId()) + "님이 그룹에 들어왔어요.", groupPath(event.groupId()),
                "group-" + event.groupId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOwnershipTransferred(GroupEvents.OwnershipTransferred event) {
        sender.send(event.newOwnerId(), NotificationType.GROUP, new Message(event.groupName(),
                "이제 이 그룹의 방장이에요.", groupPath(event.groupId()), "group-" + event.groupId()));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("멤버");
    }

    /** 화면의 그룹 경로(frontend paths.groupDetail) */
    private static String groupPath(Long groupId) {
        return "/group/" + groupId;
    }
}
