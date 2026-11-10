package com.fitness.backend.auth.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키(경로 + IP)마다 요청 횟수를 센다.
 *
 * <p>키마다 "다음 요청이 무리 없이 들어올 시각" 하나만 둔다(GCRA). 요청이 오면 그 시각을
 * 간격만큼 뒤로 민다. 그 시각이 지금보다 {@code (capacity - 1) × 간격} 넘게 앞서 있으면
 * 한꺼번에 {@code capacity}번을 이미 쓴 것이라 거절한다. 토큰 통과 결과가 같지만 정수만 써서
 * 경계가 오차 없이 맞는다.
 *
 * <p>서버가 한 대라 메모리에 둔다. 재시작하면 처음부터 센다 — 막는 대상이 스크립트라 괜찮다.
 * 그 시각이 이미 지난 키는 처음 보는 키와 같으므로 1분에 한 번 지워 메모리가 늘지 않게 한다.
 */
class RateLimiter {

    private static final long SWEEP_INTERVAL_MILLIS = 60_000;

    /** @param retryAfterSeconds 거절일 때 다시 해도 되는 때까지의 초(1 이상). 통과면 0 */
    record Decision(boolean allowed, long retryAfterSeconds, boolean firstRejection) {
    }

    /** @param readyAt 다음 요청이 무리 없이 들어올 시각(밀리초) @param rejected 마지막 요청이 거절됐는지 */
    private record State(long readyAt, boolean rejected) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep;

    RateLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweep = new AtomicLong(clock.millis());
    }

    Decision tryAcquire(String key, RateLimitProperties.Rule rule) {
        long now = clock.millis();
        sweepIfDue(now);
        long interval = rule.intervalMillis();
        long tolerance = interval * (rule.capacity() - 1);
        Decision[] decision = new Decision[1];
        states.compute(key, (k, state) -> {
            long readyAt = state == null ? now : Math.max(state.readyAt(), now);
            if (readyAt - now > tolerance) {
                long waitMillis = readyAt - now - tolerance;
                boolean first = state == null || !state.rejected();
                decision[0] = new Decision(false, Math.ceilDiv(waitMillis, 1000), first);
                return new State(readyAt, true);
            }
            decision[0] = new Decision(true, 0, false);
            return new State(readyAt + interval, false);
        });
        return decision[0];
    }

    /** 지금 들고 있는 키 수. 테스트용. */
    int size() {
        return states.size();
    }

    private void sweepIfDue(long now) {
        long last = lastSweep.get();
        if (now - last < SWEEP_INTERVAL_MILLIS || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        // 값이 그사이 바뀐 키는 지우지 않는다(ConcurrentHashMap의 removeIf는 값이 같을 때만 지운다)
        states.values().removeIf(state -> state.readyAt() <= now);
    }
}
