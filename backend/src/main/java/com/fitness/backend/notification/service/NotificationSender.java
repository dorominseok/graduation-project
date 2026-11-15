package com.fitness.backend.notification.service;

import com.fitness.backend.notification.domain.NotificationSchedule;
import com.fitness.backend.notification.domain.NotificationType;
import com.fitness.backend.notification.domain.PushSubscription;
import com.fitness.backend.notification.push.PushGateway;
import com.fitness.backend.notification.repository.NotificationSettingsRepository;
import com.fitness.backend.notification.repository.PushSubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 한 사람에게 알림 하나를 보낸다 — 그 사람이 등록한 기기(구독) 모두에.
 *
 * <pre>
 *   그 종류를 껐으면                         보내지 않는다
 *   바로 보내는 알림(응원·그룹)이 조용한 시간이면  버린다(22:00~08:00)
 *   푸시 서버가 "구독이 없다"(404·410)면          그 구독을 지운다
 * </pre>
 *
 * <p>본문은 서비스워커가 받는 JSON이다: {@code {title, body, url, tag}}. 같은 {@code tag}의 알림은 기기에서
 * 앞의 것을 바꿔 친다(리마인더가 하루에 두 번 쌓이지 않게).
 */
@Service
public class NotificationSender {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** @param url 알림을 누르면 열 화면 */
    public record Message(String title, String body, String url, String tag) {
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationSender(PushSubscriptionRepository subscriptionRepository,
                              NotificationSettingsRepository settingsRepository,
                              PushGateway gateway,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.settingsRepository = settingsRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** @return 푸시 서버가 받은 기기 수 */
    public int send(Long userId, NotificationType type, Message message) {
        // 설정이 없으면 모든 종류가 켜진 것으로 본다 — 설정은 구독할 때 만들어지니 구독이 없는 사람이다
        boolean enabled = settingsRepository.findById(userId).map(s -> s.enabled(type)).orElse(true);
        if (!enabled) {
            return 0;
        }
        if (type.instant() && NotificationSchedule.quiet(LocalTime.now(clock.withZone(SEOUL)))) {
            return 0;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return 0;
        }
        byte[] payload = payload(message);
        int delivered = 0;
        for (PushSubscription subscription : subscriptions) {
            PushGateway.Result result = gateway.send(
                    new PushGateway.Target(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth()),
                    payload, ttl(type));
            if (result == PushGateway.Result.DELIVERED) {
                delivered++;
            } else if (result == PushGateway.Result.GONE) {
                subscriptionRepository.delete(subscription);
            }
        }
        return delivered;
    }

    /**
     * 푸시 서버가 꺼진 기기를 기다려 줄 시간. 리마인더·요약은 그날 저녁이 지나면 의미가 없고, 응원·그룹 소식은
     * 하루 늦어도 볼 만하다.
     */
    static Duration ttl(NotificationType type) {
        return type.instant() ? Duration.ofHours(24) : Duration.ofHours(3);
    }

    private byte[] payload(Message message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", message.title());
        body.put("body", message.body());
        body.put("url", message.url());
        body.put("tag", message.tag());
        return objectMapper.writeValueAsBytes(body);
    }
}
