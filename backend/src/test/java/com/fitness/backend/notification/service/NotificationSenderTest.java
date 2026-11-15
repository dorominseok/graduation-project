package com.fitness.backend.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.backend.notification.RecordingPushGateway;
import com.fitness.backend.notification.domain.NotificationSettings;
import com.fitness.backend.notification.domain.NotificationType;
import com.fitness.backend.notification.domain.PushSubscription;
import com.fitness.backend.notification.repository.NotificationSettingsRepository;
import com.fitness.backend.notification.repository.PushSubscriptionRepository;
import com.fitness.backend.notification.service.NotificationSender.Message;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 보낼지 말지 — 종류별 끄기, 조용한 시간, 없어진 구독. 저장소는 가짜로 둔다 */
class NotificationSenderTest {

    private static final Long USER = 7L;
    private static final Message MESSAGE = new Message("제목", "본문", "/", "tag");

    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final NotificationSettingsRepository settings = mock(NotificationSettingsRepository.class);
    private final RecordingPushGateway gateway = new RecordingPushGateway();
    private final PushSubscription device = PushSubscription.of(USER, "https://push.example.com/1", "k", "a");

    private NotificationSender senderAt(int hour, int minute) {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 16, hour, minute, 0, 0, NotificationSender.SEOUL).toInstant(),
                NotificationSender.SEOUL);
        when(subscriptions.findByUserId(USER)).thenReturn(List.of(device));
        return new NotificationSender(subscriptions, settings, gateway, JsonMapper.builder().build(), clock);
    }

    @Test
    @DisplayName("본문은 서비스워커가 받는 JSON {title, body, url, tag}")
    void payloadIsJson() {
        assertEquals(1, senderAt(12, 0).send(USER, NotificationType.CHEER, MESSAGE));
        assertEquals("{\"title\":\"제목\",\"body\":\"본문\",\"url\":\"/\",\"tag\":\"tag\"}",
                gateway.to(device.getEndpoint()).get(0).payload());
        assertEquals(Duration.ofHours(24), gateway.to(device.getEndpoint()).get(0).ttl());
    }

    @Test
    @DisplayName("조용한 시간(22:00~08:00)에는 바로 보내는 알림(응원·그룹)을 버린다")
    void quietHoursDropInstantNotifications() {
        assertEquals(0, senderAt(23, 0).send(USER, NotificationType.CHEER, MESSAGE));
        assertEquals(0, senderAt(7, 30).send(USER, NotificationType.GROUP, MESSAGE));
        assertEquals(1, senderAt(8, 0).send(USER, NotificationType.GROUP, MESSAGE));
    }

    @Test
    @DisplayName("끈 종류는 보내지 않는다")
    void disabledTypeIsNotSent() {
        NotificationSettings off = NotificationSettings.defaults(USER, EnumSet.of(DayOfWeek.MONDAY));
        off.update(true, EnumSet.of(DayOfWeek.MONDAY), LocalTime.of(19, 0), true, DayOfWeek.SUNDAY, LocalTime.of(20, 0),
                false, true);
        when(settings.findById(USER)).thenReturn(Optional.of(off));
        NotificationSender sender = senderAt(12, 0);
        assertEquals(0, sender.send(USER, NotificationType.CHEER, MESSAGE));
        assertEquals(1, sender.send(USER, NotificationType.GROUP, MESSAGE));
    }

    @Test
    @DisplayName("푸시 서버가 구독이 없다고 하면(404·410) 그 구독을 지운다")
    void goneSubscriptionIsDeleted() {
        gateway.goneFor(device.getEndpoint());
        assertEquals(0, senderAt(12, 0).send(USER, NotificationType.REMINDER, MESSAGE));
        verify(subscriptions).delete(device);
    }

    @Test
    @DisplayName("구독이 없으면 보내지 않는다")
    void noSubscription() {
        NotificationSender sender = senderAt(12, 0);
        when(subscriptions.findByUserId(USER)).thenReturn(List.of());
        assertEquals(0, sender.send(USER, NotificationType.REMINDER, MESSAGE));
        verify(subscriptions, never()).delete(device);
    }
}
