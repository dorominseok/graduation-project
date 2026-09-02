package com.fitness.backend.user.service;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.user.domain.User;
import com.fitness.backend.user.repository.UserRepository;
import com.fitness.backend.user.web.UserDtos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 조회·수정·탈퇴. 명세 4.5~4.7. */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
     */
    public void delete(Long userId, String rawPassword) {
        User user = get(userId);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        userRepository.delete(user);
    }
}
