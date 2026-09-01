package com.fitness.backend.auth.security;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 헤더를 검사해 인증 정보를 채운다. 명세 1.3.
 *
 * <p><b>헤더가 없으면 아무것도 하지 않고 통과시킨다.</b> 그 요청이 인증을 요구하는지는
 * {@link SecurityConfig}의 경로 규칙이 판단한다 — 비인증 허용 경로(부록 B)에도 이
 * 필터가 걸리기 때문이다. 예컨대 {@code GET /exercises}는 토큰 없이도 200이어야 하고,
 * 토큰이 있으면 {@code isFavorite}를 채워야 한다(명세 5.2).
 *
 * <p>반면 <b>토큰이 있는데 잘못된 경우는 즉시 실패</b>시킨다. 그냥 통과시키면 만료된
 * 토큰으로 보낸 요청이 "비로그인"으로 취급돼, 클라이언트가 재발급 시점을 놓친다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final SecurityErrorResponder errorResponder;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponder errorResponder) {
        this.jwtProvider = jwtProvider;
        this.errorResponder = errorResponder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtProvider.AuthenticatedUser user = jwtProvider.parse(token);
            var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtProvider.JwtTokenExpiredException e) {
            SecurityContextHolder.clearContext();
            errorResponder.write(request, response, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtProvider.JwtTokenInvalidException e) {
            SecurityContextHolder.clearContext();
            errorResponder.write(request, response, ErrorCode.TOKEN_INVALID);
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
