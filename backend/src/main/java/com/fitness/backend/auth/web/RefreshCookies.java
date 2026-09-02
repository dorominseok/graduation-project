package com.fitness.backend.auth.web;

import com.fitness.backend.auth.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키. 명세 2.3 / 4.1.
 *
 * <pre>
 * Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600
 * </pre>
 *
 * <p>각 속성이 하는 일:
 * <ul>
 *   <li>{@code HttpOnly} — JS가 못 읽는다. XSS로 스크립트가 들어와도 토큰을 빼가지 못한다</li>
 *   <li>{@code SameSite=Strict} — 다른 사이트가 유발한 요청에 실리지 않는다. CSRF 방어</li>
 *   <li>{@code Path} — {@code /auth} 아래에서만 전송된다. 모든 API 요청에 딸려갈 이유가 없다</li>
 *   <li>{@code Secure} — HTTPS에서만. <b>http 오리진에서는 브라우저가 저장 자체를 하지 않는다</b></li>
 * </ul>
 */
@Component
public class RefreshCookies {

    private final JwtProperties properties;

    public RefreshCookies(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken).maxAge(properties.refreshTokenTtl()).build();
    }

    /** 로그아웃·재사용 감지 시 즉시 만료시킨다. */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> properties.cookieName().equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(properties.cookiePath());
    }
}
