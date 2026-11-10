package com.fitness.backend.auth.security;

import com.fitness.backend.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 없이 부를 수 있는 인증 API 세 개(로그인·가입·구글 로그인)의 요청 횟수를 IP마다 센다.
 * 한도를 넘으면 컨트롤러까지 가지 않고 {@code 429 RATE_LIMITED}와 {@code Retry-After}(초)로 끝낸다.
 *
 * <p>IP는 {@code request.getRemoteAddr()}다. 운영에서는 Caddy가 앞에 있어 그대로면 모두 Caddy 주소가
 * 되는데, prod 프로필의 {@code forward-headers-strategy: native}가 내부망에서 온 {@code X-Forwarded-For}로
 * 바꿔 준다. 백엔드 포트는 밖에 열려 있지 않아(80/443은 Caddy만) 헤더를 꾸며 우회할 수 없다.
 *
 * <p>토큰 재발급({@code /auth/refresh})은 세지 않는다. 앱이 알아서 부르는 요청이라, 막으면 정상
 * 사용자가 로그아웃된다. 로그인해야 쓰는 나머지 API도 세지 않는다.
 *
 * <p>시큐리티 필터 체인에만 건다. 서블릿 필터로 한 번 더 등록되면 같은 요청을 두 번 셀 수 있어
 * {@code SecurityConfig}에서 자동 등록을 끈다.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final boolean enabled;
    private final Map<String, RateLimitProperties.Rule> rules;
    private final RateLimiter limiter;
    private final SecurityErrorResponder errorResponder;

    public RateLimitFilter(RateLimitProperties properties, Clock clock, SecurityErrorResponder errorResponder) {
        this.enabled = properties.enabled();
        this.rules = Map.of(
                "/api/v1/auth/login", properties.login(),
                "/api/v1/auth/signup", properties.signup(),
                "/api/v1/auth/google", properties.google());
        this.limiter = new RateLimiter(clock);
        this.errorResponder = errorResponder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !rules.containsKey(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = path(request);
        String ip = request.getRemoteAddr();
        RateLimiter.Decision decision = limiter.tryAcquire(path + " " + ip, rules.get(path));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        // 막힌 동안 요청마다 찍으면 공격받을 때 로그가 불어난다. 막히기 시작할 때 한 번만 남긴다
        if (decision.firstRejection()) {
            log.warn("요청 제한 시작: {} ip={} ({}초 뒤 다시)", path, ip, decision.retryAfterSeconds());
        }
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        errorResponder.write(request, response, ErrorCode.RATE_LIMITED);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
