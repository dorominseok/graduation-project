package com.fitness.backend.auth.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.repository.RefreshTokenRepository;
import com.fitness.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 가입 → 로그인 → 재발급(회전) → 로그아웃 전체 흐름. 명세 4.1~4.4 / 2.3. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private String email;

    @BeforeEach
    void setUp() {
        email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private MvcResult signUp() throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"hunter2hunter2\",\"nickname\":\"민석\"}";
        return mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** {@code refreshToken=값; HttpOnly; ...} 에서 값만 꺼낸다. */
    private String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "Set-Cookie가 없다");
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private String accessTokenOf(MvcResult result) throws Exception {
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }

    @Test
    @DisplayName("가입하면 바로 로그인 상태가 된다 — 토큰과 사용자 정보를 함께 준다")
    void signUpIssuesTokens() throws Exception {
        MvcResult result = signUp();
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertEquals("Bearer", body.get("tokenType").asString());
        assertEquals(1800, body.get("expiresIn").asInt());
        assertEquals(email, body.get("user").get("email").asString());
        assertTrue(userRepository.existsByEmail(email));
    }

    @Test
    @DisplayName("리프레시 토큰은 본문이 아니라 HttpOnly 쿠키로만 내려간다")
    void refreshTokenOnlyInCookie() throws Exception {
        MvcResult result = signUp();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("HttpOnly"), "HttpOnly가 없다: " + setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), "SameSite=Strict가 없다: " + setCookie);
        assertTrue(setCookie.contains("Path=/api/v1/auth"), "Path가 좁혀지지 않았다: " + setCookie);
        assertTrue(!result.getResponse().getContentAsString().contains("refreshToken"),
                "응답 본문에 리프레시 토큰이 노출됐다");
    }

    @Test
    @DisplayName("비밀번호는 해시로만 저장된다")
    void passwordIsHashed() throws Exception {
        signUp();
        String stored = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals("hunter2hunter2", stored);
        assertTrue(stored.startsWith("$2"), "BCrypt 해시가 아니다: " + stored);
    }

    @Test
    @DisplayName("같은 이메일로 다시 가입하면 409")
    void duplicateEmail() throws Exception {
        signUp();
        String body = "{\"email\":\"" + email + "\",\"password\":\"hunter2hunter2\",\"nickname\":\"다른사람\"}";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호가 같은 오류를 낸다 — 가입 여부를 알려주지 않는다")
    void loginFailuresAreIndistinguishable() throws Exception {
        signUp();
        String wrongPw = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPw))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String noUser = "{\"email\":\"nobody@example.com\",\"password\":\"hunter2hunter2\"}";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(noUser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("재발급하면 리프레시 토큰이 새 값으로 바뀐다 (회전)")
    void refreshRotatesToken() throws Exception {
        MvcResult signUpResult = signUp();
        String oldCookie = refreshCookieOf(signUpResult);

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", oldCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        assertNotEquals(oldCookie, refreshCookieOf(refreshed), "회전되지 않았다");
    }

    @Test
    @DisplayName("회전된 옛 토큰은 다시 쓸 수 없다 — 유예 창 안이라 전체 폐기는 하지 않는다")
    void rotatedTokenIsRejectedWithinGrace() throws Exception {
        MvcResult signUpResult = signUp();
        String first = refreshCookieOf(signUpResult);

        MvcResult second = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk()).andReturn();
        String rotated = refreshCookieOf(second);

        // 옛 토큰 재사용 → 그 요청만 거부
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // 유예 창 안이므로 회전으로 받은 새 토큰은 살아 있어야 한다
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", rotated)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("재발급 실패 시 쿠키를 지운다 — 죽은 토큰으로 재시도를 반복하지 않게")
    void failedRefreshClearsCookie() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "garbage")))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"), "쿠키가 만료되지 않았다: " + setCookie);
    }

    @Test
    @DisplayName("쿠키가 아예 없으면 TOKEN_INVALID")
    void refreshWithoutCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("로그아웃하면 리프레시 토큰이 죽고 쿠키가 만료된다")
    void logoutRevokesToken() throws Exception {
        MvcResult signUpResult = signUp();
        String cookie = refreshCookieOf(signUpResult);
        String access = accessTokenOf(signUpResult);

        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .cookie(new Cookie("refreshToken", cookie)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("발급한 액세스 토큰으로 보호된 경로에 접근할 수 있다")
    void accessTokenWorks() throws Exception {
        MvcResult signUpResult = signUp();
        mvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(signUpResult)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.profile.goal").doesNotExist());
    }

    @Test
    @DisplayName("가입 시 리프레시 토큰 행이 생긴다")
    void refreshTokenPersisted() throws Exception {
        long before = refreshTokenRepository.count();
        signUp();
        assertEquals(before + 1, refreshTokenRepository.count());
    }
}
