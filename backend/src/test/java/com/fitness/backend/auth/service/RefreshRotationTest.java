package com.fitness.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.auth.domain.RefreshToken;
import com.fitness.backend.auth.jwt.OpaqueTokenFactory;
import com.fitness.backend.auth.repository.RefreshTokenRepository;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 회전과 재사용 감지. 명세 2.3 / LOG-16.
 *
 * <p>유예 창 안팎의 동작이 갈리는 것이 핵심이다. 창 안이면 동시 재발급으로 보아
 * 그 요청만 거부하고, 창 밖이면 탈취로 보아 그 사용자의 토큰을 전부 폐기한다.
 */
@SpringBootTest
@Transactional
class RefreshRotationTest {

    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired OpaqueTokenFactory opaqueTokenFactory;

    private String email;

    @BeforeEach
    void setUp() {
        email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private AuthService.AuthResult signUp() {
        return authService.signUp(email, "hunter2hunter2", "민석");
    }

    private RefreshToken row(String rawToken) {
        return refreshTokenRepository.findByTokenHash(opaqueTokenFactory.hash(rawToken)).orElseThrow();
    }

    private List<RefreshToken> tokensOf(Long userId) {
        return refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId))
                .toList();
    }

    @Test
    @DisplayName("재발급하면 옛 토큰이 폐기되고 새 토큰이 생긴다")
    void rotates() {
        AuthService.AuthResult signedUp = signUp();
        String first = signedUp.tokens().refreshTokenRaw();

        TokenPair rotated = authService.refresh(first);

        assertNotEquals(first, rotated.refreshTokenRaw());
        assertTrue(row(first).isRevoked(), "옛 토큰이 폐기되지 않았다");
        assertTrue(!row(rotated.refreshTokenRaw()).isRevoked(), "새 토큰이 살아 있지 않다");
    }

    @Test
    @DisplayName("재발급할 때마다 만료가 다시 잡힌다 — 계속 쓰면 로그인이 끊기지 않는다")
    void slidingExpiry() {
        AuthService.AuthResult signedUp = signUp();
        OffsetDateTime firstExpiry = row(signedUp.tokens().refreshTokenRaw()).getExpiresAt();

        TokenPair rotated = authService.refresh(signedUp.tokens().refreshTokenRaw());

        assertTrue(!row(rotated.refreshTokenRaw()).getExpiresAt().isBefore(firstExpiry),
                "새 토큰의 만료가 앞당겨졌다");
    }

    @Test
    @DisplayName("유예 창 안의 재사용은 그 요청만 거부한다 — 동시 재발급으로 본다")
    void reuseWithinGraceRejectsOnlyThatRequest() {
        AuthService.AuthResult signedUp = signUp();
        Long userId = signedUp.user().getId();
        String first = signedUp.tokens().refreshTokenRaw();

        TokenPair rotated = authService.refresh(first);

        // 방금 폐기된 토큰 재사용
        ApiException e = assertThrows(ApiException.class, () -> authService.refresh(first));
        assertEquals(ErrorCode.TOKEN_INVALID, e.errorCode());

        // 전체 폐기가 일어나지 않아 회전된 토큰은 그대로 쓸 수 있어야 한다
        assertTrue(!row(rotated.refreshTokenRaw()).isRevoked(), "유예 창 안인데 전체 폐기됐다");
        long alive = tokensOf(userId).stream().filter(t -> !t.isRevoked()).count();
        assertEquals(1, alive, "살아 있는 토큰 수가 다르다");
    }

    @Test
    @DisplayName("유예 창 밖의 재사용은 탈취로 보고 그 사용자의 토큰을 전부 폐기한다")
    void reuseOutsideGraceRevokesAll() {
        AuthService.AuthResult signedUp = signUp();
        Long userId = signedUp.user().getId();
        String stolen = signedUp.tokens().refreshTokenRaw();

        // 도둑이 먼저 사용 → 새 토큰을 받아 간다
        TokenPair thiefToken = authService.refresh(stolen);
        assertTrue(!row(thiefToken.refreshTokenRaw()).isRevoked());

        // 폐기 시각을 유예 창 밖으로 되돌린다(시간을 실제로 기다리지 않기 위함)
        RefreshToken used = row(stolen);
        used.revoke(OffsetDateTime.now().minusHours(1));
        forceRevokedAt(used, OffsetDateTime.now().minusHours(1));

        // 정상 사용자가 옛 토큰으로 재발급 시도 → 재사용 감지
        ApiException e = assertThrows(ApiException.class, () -> authService.refresh(stolen));
        assertEquals(ErrorCode.TOKEN_INVALID, e.errorCode());

        // 도둑이 받아 간 토큰까지 함께 죽어야 한다
        long alive = tokensOf(userId).stream().filter(t -> !t.isRevoked()).count();
        assertEquals(0, alive, "도둑의 토큰이 살아남았다");
    }

    /** {@code revoke()}는 이미 폐기된 값을 덮어쓰지 않으므로 리플렉션으로 되돌린다. */
    private void forceRevokedAt(RefreshToken token, OffsetDateTime at) {
        try {
            var field = RefreshToken.class.getDeclaredField("revokedAt");
            field.setAccessible(true);
            field.set(token, at);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("만료된 토큰으로는 재발급할 수 없다")
    void expiredTokenRejected() {
        AuthService.AuthResult signedUp = signUp();
        String raw = signedUp.tokens().refreshTokenRaw();
        RefreshToken token = row(raw);
        try {
            var field = RefreshToken.class.getDeclaredField("expiresAt");
            field.setAccessible(true);
            field.set(token, OffsetDateTime.now().minusDays(1));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }

        ApiException e = assertThrows(ApiException.class, () -> authService.refresh(raw));
        assertEquals(ErrorCode.TOKEN_INVALID, e.errorCode());
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 거부한다")
    void unknownTokenRejected() {
        ApiException e = assertThrows(ApiException.class, () -> authService.refresh("never-issued"));
        assertEquals(ErrorCode.TOKEN_INVALID, e.errorCode());
    }

    @Test
    @DisplayName("남의 리프레시 토큰을 넘겨 로그아웃시킬 수 없다")
    void logoutOnlyAffectsOwnToken() {
        AuthService.AuthResult victim = signUp();
        email = "other" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
        AuthService.AuthResult attacker = signUp();

        authService.logout(attacker.user().getId(), victim.tokens().refreshTokenRaw());

        assertTrue(!row(victim.tokens().refreshTokenRaw()).isRevoked(),
                "다른 사용자가 넘긴 토큰이 폐기됐다");
    }
}
