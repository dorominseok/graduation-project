package com.fitness.backend.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 액세스 토큰 발급·검증. 명세 2.3 / 1.5의 TOKEN_EXPIRED · TOKEN_INVALID 구분. */
class JwtProviderTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes!!";
    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    private static JwtProvider provider(String secret, Duration ttl) {
        return new JwtProvider(new JwtProperties(
                secret, ttl, Duration.ofDays(14), "refreshToken", "/api/v1/auth", false));
    }

    private static JwtProvider provider() {
        return provider(SECRET, Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("발급한 토큰에서 userId와 email을 되찾는다")
    void roundTrip() {
        JwtProvider p = provider();
        String token = p.issueAccessToken(42L, "user@example.com", NOW);
        JwtProvider.AuthenticatedUser user = p.parse(token);
        assertEquals(42L, user.userId());
        assertEquals("user@example.com", user.email());
    }

    @Test
    @DisplayName("만료된 토큰은 TOKEN_EXPIRED로 구분된다 — 클라이언트는 refresh를 시도해야 한다")
    void expiredIsDistinguishable() {
        // 검증은 실제 시각을 기준으로 하므로, 발급 시각도 실제 시각에서 잡아야 한다.
        // 고정 상수(NOW)를 쓰면 그 값이 실행 시점보다 미래일 때 만료가 성립하지 않는다.
        JwtProvider p = provider(SECRET, Duration.ofMinutes(30));
        String token = p.issueAccessToken(1L, "a@b.c", Instant.now().minus(Duration.ofHours(2)));
        assertThrows(JwtProvider.JwtTokenExpiredException.class, () -> p.parse(token));
    }

    @Test
    @DisplayName("아직 만료되지 않은 토큰은 통과한다")
    void notYetExpired() {
        JwtProvider p = provider(SECRET, Duration.ofMinutes(30));
        String token = p.issueAccessToken(7L, "a@b.c", Instant.now().minus(Duration.ofMinutes(1)));
        assertEquals(7L, p.parse(token).userId());
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부한다 — 서명이 없으면 클레임을 신뢰할 수 없다")
    void rejectsForeignSignature() {
        String token = provider("another-secret-key-with-32-bytes-min!!!!", Duration.ofMinutes(30))
                .issueAccessToken(1L, "a@b.c", NOW);
        assertThrows(JwtProvider.JwtTokenInvalidException.class, () -> provider().parse(token));
    }

    @Test
    @DisplayName("페이로드를 변조하면 서명이 깨져 거부된다")
    void rejectsTamperedPayload() {
        JwtProvider p = provider();
        String token = p.issueAccessToken(1L, "a@b.c", NOW);
        int firstDot = token.indexOf('.');
        int lastDot = token.lastIndexOf('.');
        String payload = token.substring(firstDot + 1, lastDot);
        String tampered = token.substring(0, firstDot + 1)
                + payload.substring(0, payload.length() - 2) + "XY"
                + token.substring(lastDot);
        assertThrows(JwtProvider.JwtTokenInvalidException.class, () -> p.parse(tampered));
    }

    @Test
    @DisplayName("형식이 아예 아닌 문자열도 TOKEN_INVALID다")
    void rejectsGarbage() {
        JwtProvider p = provider();
        assertThrows(JwtProvider.JwtTokenInvalidException.class, () -> p.parse("not-a-token"));
        assertThrows(JwtProvider.JwtTokenInvalidException.class, () -> p.parse(""));
    }

    @Test
    @DisplayName("expiresIn은 설정한 TTL을 초로 돌려준다 (명세 4.1)")
    void exposesTtlSeconds() {
        assertEquals(1800, provider().accessTokenTtlSeconds());
    }

    @Test
    @DisplayName("HS256 최소 키 길이에 못 미치는 secret은 기동 시점에 거부한다")
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                "too-short", Duration.ofMinutes(30), Duration.ofDays(14),
                "refreshToken", "/api/v1/auth", false));
    }

    @Test
    @DisplayName("같은 입력이라도 발급 시각이 다르면 토큰이 달라진다")
    void tokensDifferByIssuedAt() {
        JwtProvider p = provider();
        assertNotEquals(
                p.issueAccessToken(1L, "a@b.c", NOW),
                p.issueAccessToken(1L, "a@b.c", NOW.plusSeconds(1)));
    }
}
