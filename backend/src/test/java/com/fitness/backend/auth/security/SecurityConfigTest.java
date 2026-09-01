package com.fitness.backend.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.jwt.JwtProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 부록 B의 비인증 목록과 토큰 오류 구분을 확인한다. 명세 1.3 / 1.5 / 2.3.
 *
 * <p>인증 실패의 종류를 구분하는 것이 핵심이다. 클라이언트는 {@code TOKEN_EXPIRED}면
 * {@code /auth/refresh}를 시도하고 {@code TOKEN_INVALID}면 로그인 화면으로 가야 하는데,
 * 둘 다 {@code 401}이라 HTTP 상태만으로는 갈라낼 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    @DisplayName("종목 조회는 토큰 없이도 열려 있다 (부록 B)")
    void exercisesArePublic() throws Exception {
        // 컨트롤러가 아직 없으므로 401이 아닌 것(=시큐리티를 통과했다는 것)만 본다.
        mvc.perform(get("/api/v1/exercises"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("비인증 허용 경로인데 차단됨: " + s);
                    }
                });
    }

    @Test
    @DisplayName("헬스 체크는 열려 있고 /api/v1 접두사도 붙지 않는다")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("API 문서는 계약 확인용으로 열어둔다")
    void docsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 없으면 AUTHENTICATION_REQUIRED — 로그인 화면으로 보내야 한다")
    void missingTokenIsAuthenticationRequired() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("만료된 토큰은 TOKEN_EXPIRED — 클라이언트는 refresh를 시도한다")
    void expiredTokenIsDistinguished() throws Exception {
        String expired = jwtProvider.issueAccessToken(
                1L, "a@b.c", Instant.now().minus(Duration.ofDays(1)));
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("변조·형식 오류 토큰은 TOKEN_INVALID — refresh가 아니라 재로그인이다")
    void invalidTokenIsDistinguished() throws Exception {
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("잘못된 토큰은 비인증 허용 경로에서도 즉시 실패시킨다 — 재발급 시점을 놓치지 않게")
    void badTokenFailsEvenOnPublicPath() throws Exception {
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("인증 오류도 명세 1.5의 단일 구조를 지킨다 — 필터 단계라 놓치기 쉬운 지점")
    void authErrorFollowsSpecEnvelope() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("로그아웃은 인증이 필요하다 (부록 B에 없다)")
    void logoutRequiresAuth() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
