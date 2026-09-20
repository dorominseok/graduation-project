package com.fitness.backend.user.web;

import com.fitness.backend.user.domain.TrainingGoal;
import com.fitness.backend.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 프로필 API의 요청·응답. 명세 4.5~4.7. */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * 프로필 응답.
     *
     * <p>{@code profile}을 객체로 감싼 것은 항목이 하나여도 유지한다 — 향후 항목이
     * 늘 때 최상위 필드가 흩어지지 않게 하기 위함이다(명세 4.5).
     */
    public record MeResponse(Long userId, String email, String nickname,
                             Profile profile, OffsetDateTime createdAt) {

        public static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getEmail(), user.getNickname(),
                    new Profile(user.getGoal()), user.getCreatedAt());
        }
    }

    /** 프로필은 훈련 목표 1종이다. 키·체중·경력은 제외했다(LOG-11, LOG-14). */
    public record Profile(TrainingGoal goal) {
    }

    /**
     * 부분 수정. 포함된 필드만 갱신한다(명세 4.6).
     *
     * <p><b>"없음"과 "null로 지움"의 구분</b>: {@code profile} 객체 자체가 없으면
     * 프로필을 건드리지 않고, 있으면 그 안의 {@code goal}로 설정한다 —
     * {@code goal}이 {@code null}이면 미설정으로 되돌린다.
     */
    public record UpdateMeRequest(
            @Size(min = 1, max = 50, message = "1자 이상 50자 이하여야 합니다.") String nickname,
            Profile profile) {
    }

    /**
     * 회원 탈퇴 요청(명세 4.7).
     *
     * <p>비밀번호를 다시 받는 것은 액세스 토큰만으로 실행되는 되돌릴 수 없는
     * 동작이기 때문이다.
     */
    public record DeleteMeRequest(@NotBlank(message = "필수 항목입니다.") String password) {
    }

    /**
     * 비밀번호 변경 요청(명세 4.8).
     *
     * <p>현재 비밀번호를 다시 받는 것은 탈퇴와 같은 이유다 — 액세스 토큰만 있으면
     * 실행되는 동작인데, 자리를 비운 사이 남이 바꾸면 계정을 통째로 빼앗긴다.
     *
     * <p>{@code newPassword}의 8~72자는 4.1의 가입 규칙과 같은 값이다. 상한 72자는
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
