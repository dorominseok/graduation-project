package com.fitness.backend.notification;

import com.fitness.backend.notification.push.PushGateway;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 보내지 않고 모아 두는 발송기. 정한 푸시 주소에는 "구독이 없다"(GONE)를 돌려준다 */
public class RecordingPushGateway implements PushGateway {

    public record Sent(String endpoint, String payload, Duration ttl) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private final Set<String> gone = new HashSet<>();

    @Override
    public synchronized Result send(Target target, byte[] payload, Duration ttl) {
        if (gone.contains(target.endpoint())) {
            return Result.GONE;
        }
        sent.add(new Sent(target.endpoint(), new String(payload, StandardCharsets.UTF_8), ttl));
        return Result.DELIVERED;
    }

    @Override
    public String publicKey() {
        return "test-public-key";
    }

    public synchronized List<Sent> to(String endpoint) {
        return sent.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
    }

    public synchronized void goneFor(String endpoint) {
        gone.add(endpoint);
    }

    public synchronized void clear() {
        sent.clear();
        gone.clear();
    }
}
