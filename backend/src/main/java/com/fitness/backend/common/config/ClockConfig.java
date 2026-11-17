package com.fitness.backend.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 빈으로 주입한다.
 *
 * <p>{@code OffsetDateTime.now()}를 코드 안에서 직접 부르면 시간에 의존하는 로직
 * (토큰 만료, 재사용 유예 창)을 테스트할 때 실제로 기다리는 수밖에 없다.
 *
 * <p>시간대는 <b>한국으로 고정한다</b>. "오늘"이 서버·컨테이너 설정({@code TZ})을 따르면, 그 설정이 빠진
 * 서버(UTC)에서는 한국 0~9시의 기록이 전날로 들어가고 분석·회복·루틴의 기준일도 하루 밀린다.
 */
@Configuration
public class ClockConfig {

    /** 사용자의 하루를 가르는 시간대 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
