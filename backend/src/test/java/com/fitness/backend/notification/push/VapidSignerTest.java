package com.fitness.backend.notification.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VapidSignerTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private static String b64(byte[] bytes) {
        return EcKeys.toBase64Url(bytes);
    }

    @Test
    @DisplayName("Authorization은 vapid t=JWT, k=공개키 — aud는 푸시 주소의 출처, exp는 12시간 뒤, ES256")
    void authorizationHeader() {
        KeyPair pair = EcKeys.generate();
        String publicKey = b64(EcKeys.encode((ECPublicKey) pair.getPublic()));
        VapidSigner signer = new VapidSigner(publicKey, b64(EcKeys.encode((ECPrivateKey) pair.getPrivate())),
                "https://balancefit.duckdns.org", Clock.fixed(NOW, ZoneOffset.UTC));

        String header = signer.authorization("https://fcm.googleapis.com/fcm/send/abc123");
        assertTrue(header.startsWith("vapid t="), header);
        assertTrue(header.endsWith(", k=" + publicKey), header);

        String token = header.substring("vapid t=".length(), header.indexOf(','));
        Jws<Claims> jws = Jwts.parser().verifyWith(pair.getPublic()).clock(() -> java.util.Date.from(NOW)).build()
                .parseSignedClaims(token);
        assertEquals("ES256", jws.getHeader().getAlgorithm());
        assertEquals("JWT", jws.getHeader().getType());
        assertEquals("https://fcm.googleapis.com", jws.getPayload().getAudience().iterator().next());
        assertEquals("https://balancefit.duckdns.org", jws.getPayload().getSubject());
        assertEquals(NOW.plusSeconds(12 * 3600), jws.getPayload().getExpiration().toInstant());
    }

    @Test
    @DisplayName("aud에는 포트가 기본값이 아닐 때만 붙는다")
    void origin() {
        assertEquals("https://updates.push.services.mozilla.com",
                VapidSigner.origin("https://updates.push.services.mozilla.com/wpush/v2/xyz"));
        assertEquals("https://push.example.com:8443", VapidSigner.origin("https://push.example.com:8443/p/1"));
    }

    @Test
    @DisplayName("공개키와 비밀키가 짝이 아니면 만들 때 멈춘다 — 모든 알림이 거절되기 전에")
    void mismatchedKeysFailFast() {
        KeyPair a = EcKeys.generate();
        KeyPair b = EcKeys.generate();
        assertThrows(IllegalStateException.class, () -> new VapidSigner(
                b64(EcKeys.encode((ECPublicKey) a.getPublic())), b64(EcKeys.encode((ECPrivateKey) b.getPrivate())),
                "https://balancefit.duckdns.org", Clock.systemUTC()));
    }
}
