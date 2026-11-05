package com.fitness.backend.user.web;

import com.fitness.backend.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 프로필 API의 요청·응답. */
public final class UserDtos {

    private UserDtos() {
    }

    /** 프로필 응답. 계정 정보와 로그인 방법만 준다. */
    public record MeResponse(Long userId, String email, String nickname,
                             LoginMethods loginMethods, OffsetDateTime createdAt) {

        public static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getEmail(), user.getNickname(),
                    new LoginMethods(user.hasPassword(), user.getGoogleId() != null),
                    user.getCreatedAt());
        }
    }

    /**
     * 이 계정의 로그인 방법. 화면이 비밀번호 변경을 보일지, 탈퇴 확인을 비밀번호와
     * 구글 중 무엇으로 받을지 정한다.
     */
    public record LoginMethods(boolean password, boolean google) {
    }

    /** 부분 수정. 바꿀 수 있는 항목은 닉네임뿐이고, 보내지 않으면 그대로 둔다. */
    public record UpdateMeRequest(
            @Size(min = 1, max = 50, message = "1자 이상 50자 이하여야 합니다.") String nickname) {
    }

    /**
     * 회원 탈퇴 요청.
     *
     * <p>본인 확인을 다시 받는 것은 액세스 토큰만으로 실행되는 되돌릴 수 없는
     * 동작이기 때문이다. 비밀번호가 있는 계정은 비밀번호로, 구글로만 가입한 계정은
     * 구글 로그인을 한 번 더 해서 받은 ID 토큰({@code googleCredential})으로 확인한다.
     */
    public record DeleteMeRequest(String password, String googleCredential) {
    }

    /**
     * 비밀번호 변경 요청.
     *
     * <p>현재 비밀번호를 다시 받는 것은 탈퇴와 같은 이유다 — 액세스 토큰만 있으면
     * 실행되는 동작인데, 자리를 비운 사이 남이 바꾸면 계정을 통째로 빼앗긴다.
     *
     * <p>{@code newPassword}의 8~72자는 가입 규칙과 같은 값이다. 상한 72자는
     * BCrypt가 그 이상을 잘라내기 때문이다.
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "필수 항목입니다.")
            String currentPassword,

            @NotBlank(message = "필수 항목입니다.")
            @Size(min = 8, max = 72, message = "8자 이상 72자 이하여야 합니다.")
            String newPassword) {
    }
}
