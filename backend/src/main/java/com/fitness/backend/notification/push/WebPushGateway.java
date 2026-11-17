package com.fitness.backend.notification.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 표준 웹 푸시(RFC 8030)로 보낸다 — 본문은 RFC 8291로 암호화하고 VAPID로 서명한다. 외부 라이브러리 없이
 * JDK의 {@link HttpClient}를 쓴다.
 *
 * <p>푸시 서버는 본문을 풀지 못한다(브라우저만 가진 키로 암호화된다). 푸시 서버가 404·410을 주면 그 구독은
 * 없어진 것이다 — 부르는 쪽이 지운다.
 */
public final class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final VapidSigner vapid;
    private final HttpClient http;

    public WebPushGateway(VapidSigner vapid, HttpClient http) {
        this.vapid = vapid;
        this.http = http;
    }

    @Override
    public Result send(Target target, byte[] payload, Duration ttl) {
        // 등록할 때 막지만, 허용 목록이 생기기 전에 저장된 주소가 남아 있을 수 있다
        if (!PushEndpointPolicy.allowed(target.endpoint())) {
            log.warn("허용하지 않는 푸시 서버라 보내지 않고 구독을 지운다: host={}",
                    PushEndpointPolicy.hostOf(target.endpoint()));
            return Result.GONE;
        }
        byte[] body;
        // 키도 등록할 때 막지만(곡선 위의 점인지까지), 그 검사가 생기기 전에 저장된 키가 남아 있을 수 있다.
        // 여기서 걸러 지우지 않으면 암호화가 예외로 멈춰 그 사람의 다른 기기에도 보내지 못한다
        try {
            body = WebPushEncryption.encrypt(payload, EcKeys.publicKey(EcKeys.fromBase64Url(target.p256dh())),
                    EcKeys.fromBase64Url(target.auth()));
        } catch (IllegalArgumentException e) {
            log.warn("푸시 구독의 키가 올바르지 않아 지운다: {}", e.getMessage());
            return Result.GONE;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                .timeout(TIMEOUT)
                .header("TTL", String.valueOf(ttl.toSeconds()))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Urgency", "normal")
                .header("Authorization", vapid.authorization(target.endpoint()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Result.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            log.warn("푸시 서버가 거절했다: {} {} — {}", status, VapidSigner.origin(target.endpoint()),
                    abbreviate(response.body()));
            return Result.FAILED;
        } catch (IOException e) {
            log.warn("푸시 서버에 닿지 못했다: {} — {}", VapidSigner.origin(target.endpoint()), e.toString());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    @Override
    public String publicKey() {
        return vapid.publicKey();
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
