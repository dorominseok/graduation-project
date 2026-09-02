package com.fitness.backend.auth.web;

import com.fitness.backend.user.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증 API의 요청·응답. 값 규칙은 API 명세서 4.1~4.3. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 비밀번호 상한 72자는 BCrypt가 그 이상을 잘라내기 때문이다(명세 4.1). */
    public record SignUpRequest(
            @NotBlank(message = "필수 항목입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 255, message = "255자를 넘을 수 없습니다.")
            String email,

            @NotBlank(message = "필수 항목입니다.")
            @Size(min = 8, max = 72, message = "8자 이상 72자 이하여야 합니다.")
            String password,

            @NotBlank(message = "필수 항목입니다.")
            @Size(min = 1, max = 50, message = "1자 이상 50자 이하여야 합니다.")
            String nickname) {
    }

    public record LoginRequest(
            @NotBlank(message = "필수 항목입니다.") String email,
            @NotBlank(message = "필수 항목입니다.") String password) {
    }

    /**
     * 가입·로그인 응답.
     *
     * <p>리프레시 토큰은 여기 없다 — {@code HttpOnly} 쿠키로만 내려간다. 본문에 담으면
     * JS가 읽을 수 있게 되어 XSS 시 탈취 경로가 생긴다(명세 2.3).
     */
    public record TokenResponse(String tokenType, String accessToken, long expiresIn, UserSummary user) {

        public static TokenResponse of(String accessToken, long expiresIn, User user) {
            return new TokenResponse("Bearer", accessToken, expiresIn, UserSummary.from(user));
        }
    }

    /** 재발급 응답. 사용자 정보는 이미 클라이언트가 갖고 있으므로 담지 않는다(명세 4.3). */
    public record RefreshResponse(String tokenType, String accessToken, long expiresIn) {

        public static RefreshResponse of(String accessToken, long expiresIn) {
            return new RefreshResponse("Bearer", accessToken, expiresIn);
        }
    }

    public record UserSummary(Long userId, String email, String nickname) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getNickname());
        }
    }
}
