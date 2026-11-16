package com.fitness.backend.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.user.repository.UserRepository;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 잘못 보낸 요청이 서버 오류(500)가 아니라 요청 오류(4xx)로 나가는지 본다.
 *
 * <p>여기 있는 경우들은 DB까지 내려가서야 실패한다. 테스트에 트랜잭션을 걸면 그 실패가 테스트의 트랜잭션을
 * 망가뜨려 뒤따르는 조회가 실제와 다르게 동작하므로 걸지 않는다. 만든 계정은 끝나고 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvalidRequestStatusTest {

    /** 한글 28자 = 84바이트. 글자 수(72 이하)로는 통과하지만 BCrypt 한도(72바이트)를 넘는다 */
    private static final String LONG_KOREAN = "가나다라마바사아자차카타파하가나다라마바사아자차카타파하";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;

    private Long userId;
    private String bearer;

    @BeforeEach
    void signUp() {
        AuthService.AuthResult result = authService.signUp(email(), "hunter2hunter2", "민석");
        userId = result.user().getId();
        bearer = "Bearer " + result.tokens().accessToken();
    }

    @AfterEach
    void cleanUp() {
        userRepository.findById(userId).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("없는 정렬 기준은 400 — 로그인 없이 부르는 종목 목록도")
    void unknownSort() throws Exception {
        mvc.perform(get("/api/v1/exercises?sort=nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        authed(get("/api/v1/workout-sessions?sort=nope")).andExpect(status().isBadRequest());
        authed(get("/api/v1/workout-sessions?sort=performedOn,asc")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용하지 않는 메서드는 405, JSON이 아닌 본문은 415")
    void methodAndMediaType() throws Exception {
        authed(delete("/api/v1/workout-sessions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(header().exists(HttpHeaders.ALLOW));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("DB가 담지 못하는 날짜는 400")
    void dateOutOfRange() throws Exception {
        authed(get("/api/v1/workout-sessions/calendar?year=999999999&month=1")).andExpect(status().isBadRequest());
        authed(get("/api/v1/workout-sessions/calendar?year=-999999999&month=1")).andExpect(status().isBadRequest());
        authed(get("/api/v1/workout-sessions/calendar?year=2026&month=11")).andExpect(status().isOk());
        authed(get(URI.create("/api/v1/analysis/muscle-volume?referenceDate=%2B999999999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        authed(json(post("/api/v1/workout-sessions"), "{\"performedOn\":\"-999999999-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호는 72바이트까지 — 한글 28자는 가입도 변경도 400")
    void passwordByteLimit() throws Exception {
        mvc.perform(json(post("/api/v1/auth/signup"),
                        "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"민석\"}".formatted(email(), LONG_KOREAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        authed(json(patch("/api/v1/users/me/password"),
                "{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"%s\"}".formatted(LONG_KOREAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        // 한글 24자 = 72바이트는 된다
        authed(json(patch("/api/v1/users/me/password"),
                "{\"currentPassword\":\"hunter2hunter2\",\"newPassword\":\"%s\"}".formatted(LONG_KOREAN.substring(0, 24))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("세트 횟수는 저장할 수 있는 범위(32767)까지 — 넘으면 엉뚱한 값으로 저장하지 않고 400")
    void repsLimit() throws Exception {
        long exerciseId = om.readTree(mvc.perform(get("/api/v1/exercises?size=1&measureType=WEIGHT_REPS"))
                .andReturn().getResponse().getContentAsString()).get("content").get(0).get("id").asLong();
        long dayId = om.readTree(authed(json(post("/api/v1/workout-sessions"), "{}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String set = "{\"clientSetId\":\"%s\",\"exerciseId\":%d,\"weightKg\":40,\"reps\":%d}";

        authed(json(post("/api/v1/workout-sessions/" + dayId + "/sets"), set.formatted(UUID.randomUUID(), exerciseId, 40000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reps"));
        String saved = authed(json(post("/api/v1/workout-sessions/" + dayId + "/sets"),
                set.formatted(UUID.randomUUID(), exerciseId, 32767)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reps").value(32767))
                .andReturn().getResponse().getContentAsString();
        long setId = om.readTree(saved).get("id").asLong();
        authed(json(patch("/api/v1/workout-sessions/" + dayId + "/sets/" + setId), "{\"reps\":70000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("닉네임을 공백만으로 바꿀 수 없다 — 보내지 않으면 그대로 둔다")
    void blankNickname() throws Exception {
        authed(json(patch("/api/v1/users/me"), "{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
        authed(json(patch("/api/v1/users/me"), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("민석"));
    }

    @Test
    @DisplayName("탈퇴한 사용자의 남은 액세스 토큰으로 쓰기를 하면 401 — 다시 로그인하게")
    void deletedUsersToken() throws Exception {
        userRepository.deleteById(userId);

        authed(json(post("/api/v1/workout-sessions"), "{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        authed(json(post("/api/v1/groups"), "{\"name\":\"유령\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String email() {
        return "bad" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
