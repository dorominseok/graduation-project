package com.fitness.backend.user.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.repository.RefreshTokenRepository;
import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 조회·수정·탈퇴. 명세 4.5~4.7 / LOG-14. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private String email;
    private String bearer;
    private Long userId;

    @BeforeEach
    void setUp() {
        email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        AuthService.AuthResult result = authService.signUp(email, "hunter2hunter2", "민석");
        bearer = "Bearer " + result.tokens().accessToken();
        userId = result.user().getId();
    }

    @Test
    @DisplayName("프로필은 훈련 목표 1종이다 — 키·체중은 응답에 없다")
    void profileHasGoalOnly() throws Exception {
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("민석"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.profile.heightCm").doesNotExist())
                .andExpect(jsonPath("$.profile.weightKg").doesNotExist());
    }

    @Test
    @DisplayName("닉네임만 보내면 목표는 건드리지 않는다 — profile 객체가 없으면 미변경")
    void partialUpdateKeepsGoal() throws Exception {
        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"goal\":\"HYPERTROPHY\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.goal").value("HYPERTROPHY"));

        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민석2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("민석2"))
                .andExpect(jsonPath("$.profile.goal").value("HYPERTROPHY"));
    }

    @Test
    @DisplayName("profile.goal을 null로 보내면 미설정으로 되돌린다")
    void nullGoalUnsets() throws Exception {
        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"goal\":\"STRENGTH\"}}"))
                .andExpect(jsonPath("$.profile.goal").value("STRENGTH"));

        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"goal\":null}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.goal").doesNotExist());
    }

    @Test
    @DisplayName("닉네임 길이 제한을 넘기면 VALIDATION_ERROR와 필드 사유를 준다")
    void nicknameTooLong() throws Exception {
        String tooLong = "가".repeat(51);
        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    @DisplayName("정의되지 않은 목표 값은 거부한다")
    void unknownGoalRejected() throws Exception {
        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"goal\":\"BULKING\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("탈퇴하면 계정과 리프레시 토큰이 함께 지워진다 (CASCADE)")
    void deleteRemovesUserAndTokens() throws Exception {
        assertTrue(refreshTokenRepository.findAll().stream()
                .anyMatch(t -> t.getUserId().equals(userId)), "사전 조건: 토큰이 있어야 한다");

        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"hunter2hunter2\"}"))
                .andExpect(status().isNoContent());

        // JPA는 users DELETE를 곧바로 내보내지 않는다. 뒤이은 조회 대상이
        // refresh_tokens라 Hibernate가 겹치지 않는다고 보고 auto-flush를 건너뛴다.
        // DB 캐스케이드는 DELETE가 실제로 나간 뒤에야 작동하므로 여기서 밀어준다.
        userRepository.flush();

        assertTrue(userRepository.findById(userId).isEmpty(), "사용자가 남아 있다");
        assertTrue(refreshTokenRepository.findAll().stream()
                .noneMatch(t -> t.getUserId().equals(userId)), "리프레시 토큰이 남아 있다");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴하지 않는다 — 토큰만으로 지워지면 안 된다")
    void deleteRequiresPassword() throws Exception {
        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertTrue(userRepository.findById(userId).isPresent(), "계정이 지워졌다");
    }

    @Test
    @DisplayName("탈퇴 시 리프레시 쿠키도 만료시킨다")
    void deleteClearsCookie() throws Exception {
        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"hunter2hunter2\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    if (setCookie == null || !setCookie.contains("Max-Age=0")) {
                        throw new AssertionError("쿠키가 만료되지 않았다: " + setCookie);
                    }
                });
    }

    // ── 비밀번호 변경 (명세 4.8 / LOG-21)

    @Test
    @DisplayName("비밀번호를 바꾸면 새 값으로 로그인되고 옛 값은 막힌다")
    void changePasswordSwapsCredential() throws Exception {
        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"hunter3hunter3\"}"))
                .andExpect(status().isNoContent());

        assertDoesNotThrow(() -> authService.login(email, "hunter3hunter3"), "새 비밀번호로 로그인되지 않는다");
        assertThrows(ApiException.class, () -> authService.login(email, "hunter2hunter2"),
                "옛 비밀번호가 아직 통한다");
    }

    /**
     * 이 검사가 4.8의 핵심이다. 비밀번호를 바꾸는 동기 중 하나가 "계정이 털린 것
     * 같다"인데, 기존 세션이 살아 있으면 바꿔도 목적을 이루지 못한다.
     */
    @Test
    @DisplayName("비밀번호를 바꾸면 그 사용자의 리프레시 토큰이 전부 폐기된다")
    void changePasswordRevokesAllRefreshTokens() throws Exception {
        authService.login(email, "hunter2hunter2"); // 다른 기기에서 한 번 더 로그인한 상황
        assertTrue(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId))
                .anyMatch(t -> !t.isRevoked()), "사전 조건: 살아 있는 토큰이 있어야 한다");

        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"hunter3hunter3\"}"))
                .andExpect(status().isNoContent());

        assertTrue(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId))
                .allMatch(t -> t.isRevoked()), "살아 있는 리프레시 토큰이 남아 있다");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 바꾸지 않는다 — 토큰만으로 바뀌면 안 된다")
    void changePasswordRequiresCurrent() throws Exception {
        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"hunter3hunter3\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertDoesNotThrow(() -> authService.login(email, "hunter2hunter2"), "비밀번호가 바뀌었다");
    }

    @Test
    @DisplayName("현재와 같은 값으로는 바꿀 수 없다")
    void changePasswordRejectsSameValue() throws Exception {
        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"hunter2hunter2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("새 비밀번호는 가입과 같은 8~72자 규칙을 따른다 (명세 4.1)")
    void changePasswordAppliesSignUpRule() throws Exception {
        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"short7c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("비밀번호 변경 시 리프레시 쿠키도 만료시킨다")
    void changePasswordClearsCookie() throws Exception {
        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"hunter3hunter3\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    if (setCookie == null || !setCookie.contains("Max-Age=0")) {
                        throw new AssertionError("쿠키가 만료되지 않았다: " + setCookie);
                    }
                });
    }

    @Test
    @DisplayName("토큰 없이는 프로필에 접근할 수 없다")
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
