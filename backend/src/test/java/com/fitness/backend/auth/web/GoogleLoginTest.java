package com.fitness.backend.auth.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.google.GoogleIdentity;
import com.fitness.backend.auth.google.GoogleTokenVerifier;
import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.user.domain.User;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 구글 로그인(LOG-37). 구글 서명 검증은 가짜로 바꾸고, 검증을 통과한 뒤의 계정 처리 —
 * 가입·로그인·기존 계정 연결·탈퇴 확인 — 를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoogleLoginTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired AuthService authService;

    @MockitoBean GoogleTokenVerifier verifier;

    private String email;
    private String sub;

    @BeforeEach
    void setUp() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        email = "g" + id + "@gmail.com";
        sub = "1098" + id;
    }

    private void googleSays(GoogleIdentity identity) {
        given(verifier.verify(anyString())).willReturn(identity);
    }

    private ResultActions googleLogin() throws Exception {
        return mvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"id-token\"}"));
    }

    private String bearerOf(MvcResult result) throws Exception {
        return "Bearer " + om.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    @Test
    @DisplayName("처음 들어온 구글 계정은 그 자리에서 가입된다 — 비밀번호 없이, 닉네임은 구글 이름")
    void firstLoginSignsUp() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));

        googleLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.nickname").value("이영희"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")));

        User user = userRepository.findByGoogleId(sub).orElseThrow();
        assertEquals(email, user.getEmail());
        assertNull(user.getPasswordHash());
    }

    @Test
    @DisplayName("두 번째부터는 같은 계정으로 로그인한다 — 새로 만들지 않는다")
    void secondLoginReusesAccount() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        googleLogin().andExpect(status().isOk());
        Long firstId = userRepository.findByGoogleId(sub).orElseThrow().getId();

        // 구글 쪽 이메일이 바뀌어도 회원번호로 찾는다
        googleSays(new GoogleIdentity(sub, "changed-" + email, true, "이영희"));
        googleLogin().andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(firstId));
    }

    @Test
    @DisplayName("같은 이메일로 가입한 계정이 있으면 거기에 묶는다 — 기록이 두 계정으로 쪼개지지 않는다")
    void linksExistingEmailAccount() throws Exception {
        Long existingId = authService.signUp(email, "hunter2hunter2", "민석").user().getId();
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));

        googleLogin().andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(existingId))
                .andExpect(jsonPath("$.user.nickname").value("민석"));

        User user = userRepository.findById(existingId).orElseThrow();
        assertEquals(sub, user.getGoogleId());
        assertNotNull(user.getPasswordHash(), "비밀번호 로그인도 그대로 된다");
    }

    @Test
    @DisplayName("구글이 확인하지 않은 이메일로는 기존 계정에 묶지 않는다")
    void unverifiedEmailIsRejected() throws Exception {
        authService.signUp(email, "hunter2hunter2", "민석");
        googleSays(new GoogleIdentity(sub, email, false, "누군가"));

        googleLogin().andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_LOGIN_FAILED"));
        assertNull(userRepository.findByEmail(email).orElseThrow().getGoogleId());
    }

    @Test
    @DisplayName("구글 토큰 검증에 실패하면 401 — 계정을 만들지 않는다")
    void invalidTokenIsRejected() throws Exception {
        given(verifier.verify(anyString())).willThrow(new ApiException(ErrorCode.GOOGLE_LOGIN_FAILED));

        googleLogin().andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_LOGIN_FAILED"));
        assertFalse(userRepository.findByEmail(email).isPresent());
    }

    @Test
    @DisplayName("구글로만 가입한 계정은 이메일·비밀번호 로그인이 일반 실패와 같은 오류다")
    void passwordLoginOnGoogleOnlyAccount() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        googleLogin().andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"anything123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("프로필에 로그인 방법이 실린다 — 화면이 비밀번호 메뉴를 숨기는 근거")
    void meShowsLoginMethods() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        String bearer = bearerOf(googleLogin().andReturn());

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.loginMethods.password").value(false))
                .andExpect(jsonPath("$.loginMethods.google").value(true));
    }

    @Test
    @DisplayName("구글로만 가입한 계정은 비밀번호를 바꿀 수 없다")
    void googleOnlyCannotChangePassword() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        String bearer = bearerOf(googleLogin().andReturn());

        mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("구글 계정의 탈퇴는 구글 재로그인으로 확인한다 — 묶인 그 계정이어야 한다")
    void deleteWithGoogleConfirmation() throws Exception {
        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        String bearer = bearerOf(googleLogin().andReturn());

        // 다른 구글 계정으로 확인하면 거절
        googleSays(new GoogleIdentity("other-" + sub, "other@gmail.com", true, "남"));
        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"googleCredential\":\"id-token\"}"))
                .andExpect(status().isUnauthorized());

        googleSays(new GoogleIdentity(sub, email, true, "이영희"));
        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"googleCredential\":\"id-token\"}"))
                .andExpect(status().isNoContent());
        assertFalse(userRepository.findByGoogleId(sub).isPresent());
    }
}
