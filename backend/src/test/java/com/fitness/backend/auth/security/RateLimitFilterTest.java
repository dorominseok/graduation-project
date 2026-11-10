package com.fitness.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 인증 API 요청 횟수 제한. 한도를 작게 줄여 켠다(기본은 꺼짐 — 운영에서만 켠다).
 *
 * <p>제한기는 컨텍스트에 하나라 테스트끼리 기록을 공유한다. 테스트마다 다른 IP를 쓴다.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.login.period=1m",
        "app.rate-limit.signup.capacity=2",
        "app.rate-limit.signup.period=1h",
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    private static final String WRONG_LOGIN = "{\"email\":\"nobody@example.com\",\"password\":\"wrongpassword1\"}";

    @Autowired MockMvc mvc;

    private MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return from(ip, post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(WRONG_LOGIN));
    }

    /** 빈 본문이라 검증에서 400 — 한도를 세는 데는 충분하고 계정을 만들지 않는다. */
    private MockHttpServletRequestBuilder emptySignup(String ip) {
        return from(ip, post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    @DisplayName("로그인은 한도까지 컨트롤러로 가고, 넘으면 429와 다시 해도 되는 초를 준다")
    void loginLimit() throws Exception {
        String ip = "203.0.113.1";
        for (int i = 0; i < 3; i++) {
            mvc.perform(login(ip)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    @DisplayName("IP가 다르면 따로 센다")
    void countsPerIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(login("203.0.113.2"));
        }
        mvc.perform(login("203.0.113.3")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("가입은 로그인과 따로 세고 자기 한도로 막힌다")
    void signupLimit() throws Exception {
        String ip = "203.0.113.4";
        for (int i = 0; i < 4; i++) {
            mvc.perform(login(ip));
        }
        mvc.perform(emptySignup(ip)).andExpect(status().isBadRequest());
        mvc.perform(emptySignup(ip)).andExpect(status().isBadRequest());
        mvc.perform(emptySignup(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1800"));
    }

    @Test
    @DisplayName("토큰 재발급은 세지 않는다 — 앱이 알아서 부르는 요청이다")
    void refreshIsNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            int status = mvc.perform(from("203.0.113.5", post("/api/v1/auth/refresh")))
                    .andReturn().getResponse().getStatus();
            assertNotEquals(429, status);
        }
    }
}
