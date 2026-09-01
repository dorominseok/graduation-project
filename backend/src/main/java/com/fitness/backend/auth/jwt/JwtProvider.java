package com.fitness.backend.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 발급·검증. API 명세서 2.3.
 *
 * <p>클레임은 {@code sub}(userId) / {@code email} / {@code iat} / {@code exp}만 담는다.
 * 서버는 매 요청에서 서명과 만료만 확인하고 <b>DB를 조회하지 않는다</b> — 그것이
 * JWT를 택한 이유다(재배포와 무관하게 로그인이 유지된다).
 *
 * <p>토큰에 최소한만 담는 것은 JWT가 서명될 뿐 <b>암호화되지 않기</b> 때문이다.
 * 누구나 페이로드를 열어볼 수 있으므로 민감한 값을 넣지 않는다.
 */
@Component
public class JwtProvider {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtProvider(JwtProperties properties) {
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.accessTokenTtl();
    }

    /** 액세스 토큰을 발급한다. */
    public String issueAccessToken(Long userId, String email, Instant now) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** 응답의 {@code expiresIn}(초). 명세 4.1. */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /**
     * 토큰을 검증하고 사용자 정보를 꺼낸다.
     *
     * <p>만료와 그 밖의 실패를 구분해 던지는 이유는 클라이언트의 대응이 다르기
     * 때문이다 — {@code TOKEN_EXPIRED}면 {@code /auth/refresh}를 시도하고,
     * {@code TOKEN_INVALID}면 로그인 화면으로 가야 한다(명세 1.5).
     *
     * @throws JwtTokenExpiredException 만료된 토큰
     * @throws JwtTokenInvalidException 서명 불일치·형식 오류 등
     */
    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()), claims.get(CLAIM_EMAIL, String.class));
        } catch (ExpiredJwtException e) {
            throw new JwtTokenExpiredException(e);
        } catch (JwtException | IllegalArgumentException e) {
            // NumberFormatException(sub가 숫자가 아님)도 IllegalArgumentException이다.
            throw new JwtTokenInvalidException(e);
        }
    }

    /** 검증을 통과한 토큰에서 꺼낸 사용자. */
    public record AuthenticatedUser(Long userId, String email) {
    }

    public static class JwtTokenExpiredException extends RuntimeException {
        public JwtTokenExpiredException(Throwable cause) {
            super(cause);
        }
    }

    public static class JwtTokenInvalidException extends RuntimeException {
        public JwtTokenInvalidException(Throwable cause) {
            super(cause);
        }
    }
}
