package com.fitness.backend.auth.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.auth.service.TokenPair;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.common.web.ApiV1Controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 인증 API. 명세 4.1~4.4. */
@ApiV1Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService, RefreshCookies refreshCookies) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
    }

    /** 회원가입. 가입 후 바로 로그인 상태가 되도록 토큰을 함께 준다 — 로그인 왕복이 없다. */
    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.TokenResponse> signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        AuthService.AuthResult result =
                authService.signUp(request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.tokens().refreshTokenRaw()).toString())
                .body(AuthDtos.TokenResponse.of(
                        result.tokens().accessToken(), result.tokens().expiresInSeconds(), result.user()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.tokens().refreshTokenRaw()).toString())
                .body(AuthDtos.TokenResponse.of(
                        result.tokens().accessToken(), result.tokens().expiresInSeconds(), result.user()));
    }

    /**
     * 액세스 토큰 재발급. 본문 없이 쿠키만으로 인증한다.
     *
     * <p>실패하면 쿠키를 지운다. 남겨두면 클라이언트가 죽은 토큰으로 계속 재시도하고,
     * 그 재시도가 재사용 감지에 걸려 상황을 악화시킨다.
     *
     * <p><b>클라이언트 주의</b>: 이 요청은 <b>한 번에 하나만</b> 보내야 한다. 액세스
     * 토큰이 만료되는 순간 여러 요청이 동시에 401을 받으므로, API 클라이언트가
     * 재발급을 하나로 묶고 나머지는 그 결과를 기다렸다 재시도하게 한다(명세 4.3).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.RefreshResponse> refresh(HttpServletRequest request) {
        String raw = refreshCookies.read(request)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        try {
            TokenPair tokens = authService.refresh(raw);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(tokens.refreshTokenRaw()).toString())
                    .body(AuthDtos.RefreshResponse.of(tokens.accessToken(), tokens.expiresInSeconds()));
        } catch (ApiException e) {
            throw new CookieClearingException(e);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                       HttpServletRequest request) {
        authService.logout(principal.userId(), refreshCookies.read(request).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    /** 재발급 실패를 쿠키 삭제와 함께 응답하기 위한 표시용 예외. */
    public static class CookieClearingException extends RuntimeException {
        public CookieClearingException(ApiException cause) {
            super(cause);
        }

        public ApiException apiException() {
            return (ApiException) getCause();
        }
    }
}
