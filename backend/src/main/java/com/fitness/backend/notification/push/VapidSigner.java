package com.fitness.backend.notification.push;

import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * VAPID(RFC 8292) — 푸시 서버에 "이 알림은 우리 서버가 보낸다"를 증명하는 서명. 서명은 이미 쓰는 jjwt의 ES256이다.
 *
 * <pre>
 *   Authorization: vapid t=&lt;JWT&gt;, k=&lt;공개키 base64url&gt;
 *   JWT = { typ: JWT, alg: ES256 } . { aud: 푸시 주소의 출처, exp: 지금 + 12시간, sub: 연락처 }
 * </pre>
 *
 * <p>공개키는 브라우저가 구독할 때 쓰는 그 키다. 비밀키와 짝이 맞지 않으면 모든 알림이 거절되므로, 만들 때
 * 시험 서명을 해 보고 맞지 않으면 기동을 멈춘다.
 */
public final class VapidSigner {

    /** 푸시 서버는 24시간까지 받는다. 반만 쓴다 */
    private static final Duration LIFETIME = Duration.ofHours(12);

    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;
    private final String publicKeyBase64;
    private final String subject;
    private final Clock clock;

    public VapidSigner(String publicKeyBase64, String privateKeyBase64, String subject, Clock clock) {
        this.publicKey = EcKeys.publicKey(EcKeys.fromBase64Url(publicKeyBase64));
        this.privateKey = EcKeys.privateKey(EcKeys.fromBase64Url(privateKeyBase64));
        this.publicKeyBase64 = EcKeys.toBase64Url(EcKeys.encode(publicKey));
        this.subject = subject;
        this.clock = clock;
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token("https://example.com"));
        } catch (RuntimeException e) {
            throw new IllegalStateException("VAPID 공개키와 비밀키가 짝이 아니다 — VAPID_PUBLIC_KEY·VAPID_PRIVATE_KEY를 확인한다", e);
        }
    }

    public String publicKey() {
        return publicKeyBase64;
    }

    /** 이 푸시 주소로 보낼 때의 Authorization 헤더 값 */
    public String authorization(String endpoint) {
        return "vapid t=" + token(origin(endpoint)) + ", k=" + publicKeyBase64;
    }

    // aud는 문자열 하나로 쓴다. jjwt는 배열을 권해 single()을 비권장으로 두지만, RFC 8292의 예와 푸시 서버들은 문자열이다
    @SuppressWarnings("deprecation")
    private String token(String audience) {
        Instant now = clock.instant();
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .audience().single(audience)
                .expiration(Date.from(now.plus(LIFETIME)))
                .subject(subject)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    /** 푸시 주소의 출처(scheme://host[:port]) — aud에 넣는다 */
    static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
