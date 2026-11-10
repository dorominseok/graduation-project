package com.fitness.backend.auth.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 로그인 없이 부를 수 있는 인증 API의 요청 횟수 제한. IP마다 센다({@link RateLimitFilter}).
 *
 * @param enabled 끄면 세지 않는다. 운영(prod)에서만 켠다 — 로컬과 테스트는 같은 주소(127.0.0.1)로
 *                가입·로그인을 수십 번 한다
 * @param login   {@code POST /auth/login} — 비밀번호 대입
 * @param signup  {@code POST /auth/signup} — 계정 대량 생성. 계정마다 오늘 루틴 LLM이 한 번 불린다
 * @param google  {@code POST /auth/google} — 처음 로그인하면 가입이 된다
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull @Valid Rule login,
        @NotNull @Valid Rule signup,
        @NotNull @Valid Rule google) {

    /**
     * {@code period} 동안 {@code capacity}번. 한꺼번에 {@code capacity}번까지 되고, 다 쓰면
     * {@code period / capacity}마다 한 번씩 다시 생긴다.
     */
    public record Rule(@Min(1) int capacity, @NotNull Duration period) {

        public Rule {
            if (period != null && period.toMillis() < capacity) {
                throw new IllegalArgumentException("period는 capacity밀리초 이상이어야 한다: " + period);
            }
        }

        /** 한 번이 다시 생기는 간격(밀리초). */
        long intervalMillis() {
            return period.toMillis() / capacity;
        }
    }
}
