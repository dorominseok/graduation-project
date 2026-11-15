package com.fitness.backend.notification.push;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 알림 배선. VAPID 키가 있으면 실제로 보내고, 없으면(로컬·테스트 기본) 보내지 않는 발송기를 쓴다.
 *
 * <p>예약 작업(리마인더·주간 요약)은 {@code @EnableScheduling}, 그룹 일에 걸린 즉시 알림은 요청을 붙잡지 않게
 * {@code @EnableAsync}로 따로 보낸다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class PushConfig {

    @Bean
    public PushGateway pushGateway(PushProperties properties, Clock clock) {
        if (!properties.enabled()) {
            return new DisabledPushGateway();
        }
        VapidSigner vapid = new VapidSigner(properties.publicKey(), properties.privateKey(), properties.subject(), clock);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new WebPushGateway(vapid, http);
    }

    /** 키가 없을 때. 아무것도 보내지 않고 구독도 지우지 않는다 */
    static final class DisabledPushGateway implements PushGateway {

        @Override
        public Result send(Target target, byte[] payload, Duration ttl) {
            return Result.SKIPPED;
        }

        @Override
        public String publicKey() {
            return null;
        }
    }
}
