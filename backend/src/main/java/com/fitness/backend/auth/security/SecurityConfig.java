package com.fitness.backend.auth.security;

import com.fitness.backend.common.error.ErrorCode;
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
 * 인증 설정. API 명세서 1.3 / 2장 / 부록 B.
 *
 * <p>세션을 만들지 않는다({@code STATELESS}). JWT를 택한 이유가 재배포와 무관하게
 * 로그인을 유지하는 것인데, 서버가 세션을 들면 그 이점이 사라진다(명세 2.2).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 부록 B — 인증이 필요 없는 엔드포인트. */
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
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
    private final SecurityErrorResponder errorResponder;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityErrorResponder errorResponder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.errorResponder = errorResponder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 인증을 Authorization 헤더로 하므로 CSRF가 성립하지 않는다(명세 2.1).
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
                        // 명세 1.3: 위 목록 외 전부 인증이 필요하다.
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        // 인증 정보가 아예 없을 때. 토큰이 있으나 잘못된 경우는
                        // JwtAuthenticationFilter가 TOKEN_EXPIRED / TOKEN_INVALID로 먼저 응답한다.
                        .authenticationEntryPoint((request, response, e) ->
                                errorResponder.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, e) ->
                                errorResponder.write(request, response, ErrorCode.ACCESS_DENIED)))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 비밀번호 해시. 명세 4.1이 BCrypt를 전제로 {@code password} 상한을 72자로 정했다
     * — BCrypt가 72바이트를 넘는 입력을 잘라내기 때문이다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
