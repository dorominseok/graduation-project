package com.fitness.backend.notification.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 등록 검사가 생기기 전에 저장된 구독(허용하지 않는 주소, 곡선 위에 없는 키)이 남아 있어도 보내지 않고 지운다 */
class WebPushGatewayTest {

    private final HttpClient http = mock(HttpClient.class);
    private final WebPushGateway gateway = gateway(http);

    @Test
    @DisplayName("허용하지 않는 호스트의 구독은 요청을 보내지 않고 지울 것으로 돌려준다")
    void neverCallsUnknownHost() {
        String p256dh = EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) EcKeys.generate().getPublic()));
        String auth = EcKeys.toBase64Url(new byte[16]);

        PushGateway.Result result = gateway.send(
                new PushGateway.Target("https://10.0.0.5/internal", p256dh, auth), "{}".getBytes(), Duration.ofHours(1));

        assertEquals(PushGateway.Result.GONE, result);
        verifyNoInteractions(http);
    }

    @Test
    @DisplayName("곡선 위에 없는 공개키로 저장된 구독은 예외로 멈추지 않고 지울 것으로 돌려준다 — 그 사람의 다른 기기도 받게")
    void dropsKeyOffTheCurve() {
        byte[] point = new byte[65];
        point[0] = 0x04;
        point[32] = 1;
        point[64] = 1;
        String auth = EcKeys.toBase64Url(new byte[16]);

        PushGateway.Result result = gateway.send(
                new PushGateway.Target("https://fcm.googleapis.com/fcm/send/x", EcKeys.toBase64Url(point), auth),
                "{}".getBytes(), Duration.ofHours(1));

        assertEquals(PushGateway.Result.GONE, result);
        verifyNoInteractions(http);
    }

    private static WebPushGateway gateway(HttpClient http) {
        KeyPair vapid = EcKeys.generate();
        return new WebPushGateway(new VapidSigner(
                EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) vapid.getPublic())),
                EcKeys.toBase64Url(EcKeys.encode((ECPrivateKey) vapid.getPrivate())),
                "https://app.example", Clock.systemUTC()), http);
    }
}
