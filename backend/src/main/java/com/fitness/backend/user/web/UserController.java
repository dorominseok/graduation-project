package com.fitness.backend.user.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.auth.web.RefreshCookies;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 프로필 API. 명세 4.5~4.7. */
@ApiV1Controller
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;
    private final RefreshCookies refreshCookies;

    public UserController(UserService userService, RefreshCookies refreshCookies) {
        this.userService = userService;
        this.refreshCookies = refreshCookies;
    }

    @GetMapping
    public UserDtos.MeResponse me(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return UserDtos.MeResponse.from(userService.get(principal.userId()));
    }

    @PatchMapping
    public UserDtos.MeResponse update(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @Valid @RequestBody UserDtos.UpdateMeRequest request) {
        return UserDtos.MeResponse.from(userService.update(principal.userId(), request));
    }

    /**
     * 비밀번호 변경(명세 4.8).
     *
     * <p>성공하면 리프레시 토큰이 전부 폐기되므로 쿠키도 함께 만료시킨다 — 죽은
     * 쿠키를 남겨두면 클라이언트가 그걸로 재발급을 시도하고 실패만 반복한다(4.3).
     */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    /** 회원 탈퇴. 계정과 기록을 되돌릴 수 없게 지우고 리프레시 쿠키도 만료시킨다. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                       @Valid @RequestBody UserDtos.DeleteMeRequest request) {
        userService.delete(principal.userId(), request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }
}
