package com.fitness.backend.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    @DisplayName("날짜는 서버·컨테이너 시간대와 상관없이 한국 시간으로 센다")
    void clockIsSeoul() {
        assertEquals(ZoneId.of("Asia/Seoul"), new ClockConfig().clock().getZone());
    }
}
