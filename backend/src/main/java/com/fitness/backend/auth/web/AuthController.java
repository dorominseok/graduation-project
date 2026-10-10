package com.fitness.backend.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "회원가입")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공 — 액세스 토큰과 리프레시 쿠키 발급"),
            @ApiResponse(responseCode = "409", description = "이미 쓰는 이메일 (DUPLICATE_EMAIL)")
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.TokenResponse> signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        AuthService.AuthResult result =
                authService.signUp(request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.tokens().refreshTokenRaw()).toString())
                .body(AuthDtos.TokenResponse.of(
                        result.tokens().accessToken(), result.tokens().expiresInSeconds(), result.user()));
    }

    @Operation(summary = "로그인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 — 액세스 토큰과 리프레시 쿠키 발급"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 맞지 않음")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.tokens().refreshTokenRaw()).toString())
                .body(AuthDtos.TokenResponse.of(
                        result.tokens().accessToken(), result.tokens().expiresInSeconds(), result.user()));
    }

    /** 구글 로그인. 처음이면 가입까지 한다 — 응답은 로그인과 같다(LOG-37). */
    @Operation(summary = "구글 로그인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인(또는 가입) 성공 — 액세스 토큰과 리프레시 쿠키 발급"),
            @ApiResponse(responseCode = "401", description = "구글 토큰 검증 실패 (GOOGLE_LOGIN_FAILED)"),
            @ApiResponse(responseCode = "409", description = "이메일이 이미 다른 구글 계정에 묶여 있음")
    })
    @PostMapping("/google")
    public ResponseEntity<AuthDtos.TokenResponse> google(@Valid @RequestBody AuthDtos.GoogleLoginRequest request) {
        AuthService.AuthResult result = authService.googleLogin(request.credential());
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
    @Operation(summary = "액세스 토큰 재발급")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공 — 리프레시 토큰도 회전된다"),
            @ApiResponse(responseCode = "401", description = "쿠키가 없거나 이미 쓴 토큰 — 재사용이 감지되면 해당 계열을 전부 폐기한다")
    })
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

    @Operation(summary = "로그아웃")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "리프레시 토큰 폐기와 쿠키 만료 완료")
    })
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
