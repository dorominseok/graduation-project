package com.fitness.backend.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 빈으로 주입한다.
 *
 * <p>{@code OffsetDateTime.now()}를 코드 안에서 직접 부르면 시간에 의존하는 로직
 * (토큰 만료, 재사용 유예 창)을 테스트할 때 실제로 기다리는 수밖에 없다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
