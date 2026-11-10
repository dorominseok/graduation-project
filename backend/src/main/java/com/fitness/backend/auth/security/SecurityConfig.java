package com.fitness.backend.auth.security;

import com.fitness.backend.common.error.ErrorCode;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인증 설정.
 *
 * <p>세션을 만들지 않는다({@code STATELESS}). JWT를 택한 이유가 재배포와 무관하게
 * 로그인을 유지하는 것인데, 서버가 세션을 들면 그 이점이 사라진다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 인증이 필요 없는 엔드포인트. */
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",   // 리프레시 쿠키로 인증한다
    };

    private static final String[] PUBLIC_GET = {
            "/api/v1/exercises",
            "/api/v1/exercises/*",
            "/actuator/health",
            "/actuator/info",
    };

    /** API 문서. 계약을 눈으로 확인하는 용도라 열어둔다. */
    private static final String[] DOCS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final SecurityErrorResponder errorResponder;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          SecurityErrorResponder errorResponder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.errorResponder = errorResponder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 인증을 Authorization 헤더로 하므로 CSRF가 성립하지 않는다.
                // 쿠키를 쓰는 곳은 /auth/refresh 하나이고, 그 쿠키는 SameSite=Strict라
                // 다른 사이트에서 유발한 요청에는 실리지 않는다.
                .csrf(csrf -> csrf.disable())
                // 브라우저 기본 인증 팝업과 폼 로그인은 쓰지 않는다. SPA가 화면을 그린다.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(DOCS).permitAll()
                        // 위 목록 외 전부 인증이 필요하다.
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        // 인증 정보가 아예 없을 때. 토큰이 있으나 잘못된 경우는
                        // JwtAuthenticationFilter가 TOKEN_EXPIRED / TOKEN_INVALID로 먼저 응답한다.
                        .authenticationEntryPoint((request, response, e) ->
                                errorResponder.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, e) ->
                                errorResponder.write(request, response, ErrorCode.ACCESS_DENIED)))

                // 요청 횟수 제한을 먼저 — 막을 요청이면 토큰 검사도 하지 않는다. 같은 자리에 건 필터는 건 순서대로 돈다
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 요청 횟수 제한 필터를 서블릿 필터로는 등록하지 않는다. {@code @Component} 필터는 스프링 부트가
     * 서블릿 필터로도 자동 등록해, 시큐리티 체인과 합쳐 같은 요청을 두 번 셀 수 있다.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 비밀번호 해시. BCrypt는 72바이트를 넘는 입력을 잘라내서 {@code password} 상한을 72자로 둔다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
