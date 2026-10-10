package com.fitness.backend.user.service;

import com.fitness.backend.auth.google.GoogleTokenVerifier;
import com.fitness.backend.auth.repository.RefreshTokenRepository;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.user.domain.User;
import com.fitness.backend.user.repository.UserRepository;
import com.fitness.backend.user.web.UserDtos;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 조회·수정·탈퇴. 명세 4.5~4.7. */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final Clock clock;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       GoogleTokenVerifier googleTokenVerifier,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자"));
    }

    public User update(Long userId, UserDtos.UpdateMeRequest request) {
        User user = get(userId);
        user.updateNickname(request.nickname());
        // profile 객체가 없으면 건드리지 않는다. 있으면 goal을 그대로 반영한다
        // (null이면 미설정으로 되돌린다).
        if (request.profile() != null) {
            user.changeGoal(request.profile().goal());
        }
        return user;
    }

    /**
     * 회원 탈퇴. {@code users} 행을 지우면 FK {@code ON DELETE CASCADE}로
     * 리프레시 토큰·즐겨찾기·세션·세트가 함께 지워진다(명세 4.7).
     *
     * <p>소프트 삭제를 쓰지 않는다 — 탈퇴의 목적이 개인정보를 남기지 않는 것이다.
     *
     * <p>본인 확인은 비밀번호나 구글 재로그인 중 하나로 받는다(LOG-37). 둘 다 가진 계정은
     * 어느 쪽이든 된다 — 구글을 묶은 뒤 비밀번호를 잊은 사람도 탈퇴할 수 있어야 한다.
     */
    public void delete(Long userId, String rawPassword, String googleCredential) {
        User user = get(userId);
        if (!confirmsIdentity(user, rawPassword, googleCredential)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        userRepository.delete(user);
    }

    private boolean confirmsIdentity(User user, String rawPassword, String googleCredential) {
        if (rawPassword != null && !rawPassword.isBlank()) {
            return user.hasPassword() && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        }
        if (googleCredential != null && !googleCredential.isBlank()) {
            // 아무 구글 계정이 아니라 이 계정에 묶인 그 구글 계정이어야 한다
            return user.getGoogleId() != null
                    && user.getGoogleId().equals(googleTokenVerifier.verify(googleCredential).subject());
        }
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호 또는 구글 확인이 필요합니다.");
    }

    /**
     * 비밀번호 변경(명세 4.8).
     *
     * <p><b>변경에 성공하면 그 사용자의 리프레시 토큰을 전부 폐기한다.</b> 비밀번호를
     * 바꾸는 동기 중 하나가 "계정이 털린 것 같다"인데, 기존 세션이 살아 있으면 목적을
     * 이루지 못한다. 바꾼 본인도 다시 로그인한다.
     *
     * <p>액세스 토큰은 남은 만료 시간(최대 30분) 동안 유효하다 — 로그아웃과 같은
     * 한계이며 같은 이유로 감수한다(명세 2.3).
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = get(userId);

        // 구글로만 가입한 계정은 바꿀 비밀번호가 없다. 화면도 이 메뉴를 숨긴다
        if (!user.hasPassword()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "구글로 가입한 계정은 비밀번호가 없습니다.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 같은 값으로의 변경은 막는다. 바꿨다고 생각하는데 실제로는 그대로인 상태가
        // 남으면, 위의 "세션을 끊는다"는 보장만 소모하고 목적은 이루지 못한다.
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "현재 비밀번호와 다른 값이어야 합니다.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now(clock));
    }
}
