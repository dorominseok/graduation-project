package com.fitness.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** 1분에 3번 — 20초마다 한 번씩 다시 생긴다. */
    private static final RateLimitProperties.Rule THREE_PER_MINUTE =
            new RateLimitProperties.Rule(3, Duration.ofMinutes(1));
    /** 1시간에 20번 — 3분마다 한 번씩 다시 생긴다. */
    private static final RateLimitProperties.Rule TWENTY_PER_HOUR =
            new RateLimitProperties.Rule(20, Duration.ofHours(1));

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    @DisplayName("한꺼번에 capacity번까지 되고, 그다음은 한 번이 다시 생길 때까지 거절한다")
    void burstThenReject() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("a", THREE_PER_MINUTE).allowed());
        }
        RateLimiter.Decision rejected = limiter.tryAcquire("a", THREE_PER_MINUTE);
        assertFalse(rejected.allowed());
        assertEquals(20, rejected.retryAfterSeconds());
        assertTrue(rejected.firstRejection());

        RateLimiter.Decision again = limiter.tryAcquire("a", THREE_PER_MINUTE);
        assertFalse(again.allowed());
        assertEquals(20, again.retryAfterSeconds(), "거절은 기다릴 시각을 뒤로 밀지 않는다");
        assertFalse(again.firstRejection(), "막힌 동안에는 처음 거절이 아니다");
    }

    @Test
    @DisplayName("간격만큼 지나면 한 번 더 되고, 다시 막히면 그때가 새 거절의 시작이다")
    void refillsOneAtATime() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a", THREE_PER_MINUTE);
        }
        clock.advance(Duration.ofSeconds(19));
        assertFalse(limiter.tryAcquire("a", THREE_PER_MINUTE).allowed());

        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire("a", THREE_PER_MINUTE).allowed());
        RateLimiter.Decision next = limiter.tryAcquire("a", THREE_PER_MINUTE);
        assertFalse(next.allowed());
        assertTrue(next.firstRejection());
    }

    @Test
    @DisplayName("다시 해도 되는 때까지의 초는 올림한다 — 0초라고 알려 주지 않는다")
    void retryAfterRoundsUp() {
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryAcquire("a", TWENTY_PER_HOUR).allowed());
        }
        assertEquals(180, limiter.tryAcquire("a", TWENTY_PER_HOUR).retryAfterSeconds());

        clock.advance(Duration.ofMillis(179_500));
        assertEquals(1, limiter.tryAcquire("a", TWENTY_PER_HOUR).retryAfterSeconds());
    }

    @Test
    @DisplayName("키가 다르면 따로 센다")
    void keysAreIndependent() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a", THREE_PER_MINUTE);
        }
        assertTrue(limiter.tryAcquire("b", THREE_PER_MINUTE).allowed());
    }

    @Test
    @DisplayName("다 찬 키는 1분마다 지운다 — 아직 기다리는 키는 남긴다")
    void sweepsRecoveredKeys() {
        limiter.tryAcquire("recovered", THREE_PER_MINUTE);          // 20초 뒤면 다 찬다
        for (int i = 0; i < 20; i++) {
            limiter.tryAcquire("waiting", TWENTY_PER_HOUR);          // 1시간 뒤에야 다 찬다
        }
        clock.advance(Duration.ofSeconds(61));
        limiter.tryAcquire("new", THREE_PER_MINUTE);

        assertEquals(2, limiter.size());
        assertFalse(limiter.tryAcquire("waiting", TWENTY_PER_HOUR).allowed(), "지운 키만 처음부터 센다");
    }

    @Test
    @DisplayName("period가 capacity밀리초보다 짧으면 간격이 0이 되므로 받지 않는다")
    void rejectsTooShortPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitProperties.Rule(10, Duration.ofMillis(5)));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-10T03:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
