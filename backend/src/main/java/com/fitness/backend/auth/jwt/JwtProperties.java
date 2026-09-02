package com.fitness.backend.auth.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 토큰 정책. API 명세서 2.3.
 *
 * @param secret          HS256 서명 키. 환경변수 {@code JWT_SECRET}으로만 주입한다
 * @param accessTokenTtl  액세스 토큰 만료 (기본 30분)
 * @param refreshTokenTtl 리프레시 토큰 만료 (기본 14일)
 * @param cookieName      리프레시 토큰 쿠키 이름
 * @param cookiePath      쿠키 전송 경로. {@code /auth} 아래로 좁혀 다른 요청에 딸려가지 않게 한다
 * @param cookieSecure    {@code Secure} 속성. 운영은 반드시 {@code true} (아래 참조)
 * @param reuseGrace      재사용 감지의 유예 창. 이 시간 안에 다시 온 폐기 토큰은
 *                        공격이 아니라 동시 재발급으로 본다 (LOG-16)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String cookieName,
        @NotBlank String cookiePath,
        boolean cookieSecure,
        @NotNull Duration reuseGrace) {

    /**
     * HS256의 권장 키 길이(바이트). 서명 키가 해시 출력보다 짧으면 보안 강도가
     * 키 길이로 떨어지므로 jjwt가 아예 거부한다.
     */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT_SECRET은 최소 %d바이트여야 한다 (HS256 요구사항)".formatted(MIN_SECRET_BYTES));
        }
    }
}
