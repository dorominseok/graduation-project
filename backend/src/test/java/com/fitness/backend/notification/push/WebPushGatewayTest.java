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

/** 허용 목록이 생기기 전에 저장된 주소가 남아 있어도 그쪽으로는 보내지 않는다 */
class WebPushGatewayTest {

    @Test
    @DisplayName("허용하지 않는 호스트의 구독은 요청을 보내지 않고 지울 것으로 돌려준다")
    void neverCallsUnknownHost() {
        KeyPair vapid = EcKeys.generate();
        HttpClient http = mock(HttpClient.class);
        WebPushGateway gateway = new WebPushGateway(new VapidSigner(
                EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) vapid.getPublic())),
                EcKeys.toBase64Url(EcKeys.encode((ECPrivateKey) vapid.getPrivate())),
                "https://app.example", Clock.systemUTC()), http);
        String p256dh = EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) EcKeys.generate().getPublic()));
        String auth = EcKeys.toBase64Url(new byte[16]);

        PushGateway.Result result = gateway.send(
                new PushGateway.Target("https://10.0.0.5/internal", p256dh, auth), "{}".getBytes(), Duration.ofHours(1));

        assertEquals(PushGateway.Result.GONE, result);
        verifyNoInteractions(http);
    }
}
