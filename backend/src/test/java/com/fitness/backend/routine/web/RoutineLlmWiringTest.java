package com.fitness.backend.routine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.routine.llm.LlmCallException;
import com.fitness.backend.routine.llm.LlmClient;
import com.fitness.backend.routine.llm.LlmRoutineComposer;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 루틴 서비스에 LLM을 꽂았을 때. 실제 Gemini 대신 받은 입력으로 답을 지어 내는
 * 가짜를 쓴다 — 여기서 보는 것은 LLM의 판단이 아니라 <b>배선</b>이다: LLM이 고른 순서·설명이 그대로
 * 저장되는지, 실패하면 규칙 루틴이 나가고 사유가 남는지.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RoutineLlmWiringTest.FakeLlm.class)
class RoutineLlmWiringTest {

    /** 테스트마다 바꿔 끼우는 가짜 LLM의 동작 */
    static final AtomicReference<Function<LlmClient.Prompt, String>> BEHAVIOR = new AtomicReference<>();

    @TestConfiguration
    static class FakeLlm {
        @Bean
        LlmRoutineComposer llmRoutineComposer(ObjectMapper json) {
            LlmClient client = prompt -> new LlmClient.Response(BEHAVIOR.get().apply(prompt), 1500, 100, 900);
            return new LlmRoutineComposer(client, json, 2, 0.4);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;

    private String bearer;

    @BeforeEach
    void setUp() {
        String email = "l" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    @Test
    @DisplayName("LLM이 고른 순서와 설명이 그대로 저장된다 — 세트는 규칙 값")
    void storesLlmChoice() throws Exception {
        // 받은 부위를 거꾸로, 부위마다 첫 후보
        BEHAVIOR.set(prompt -> answer(prompt, true, "하체부터 시작해서 가슴으로 마무리하는 순서로 바꿔 봤어요."));

        long id = idOf(createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedBy").value("LLM"))
                .andExpect(jsonPath("$.comment").value("하체부터 시작해서 가슴으로 마무리하는 순서로 바꿔 봤어요."))
                .andExpect(jsonPath("$.items[0].muscleGroup").value("POSTERIOR"))
                .andExpect(jsonPath("$.items[3].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.items[0].plannedSets").value(4)));

        Map<String, Object> row = row(id);
        assertEquals(1, ((Number) row.get("llm_attempts")).intValue());
        assertNull(row.get("fallback_reason"));
        assertEquals("LLM", row.get("comment_by"));
        assertEquals(true, row.get("final_check_passed"));
    }

    @Test
    @DisplayName("LLM 호출이 실패해도 루틴은 나온다 — 규칙이 고르고 사유를 남긴다")
    void fallsBackWhenCallFails() throws Exception {
        BEHAVIOR.set(prompt -> {
            throw new LlmCallException(LlmCallException.Kind.TIMEOUT, "응답 없음");
        });

        long id = idOf(createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedBy").value("RULE"))
                .andExpect(jsonPath("$.items[0].muscleGroup").value("CHEST")));

        Map<String, Object> row = row(id);
        assertEquals("LLM_ERROR", row.get("fallback_reason"));
        assertEquals(1, ((Number) row.get("llm_attempts")).intValue());
    }

    @Test
    @DisplayName("설명에 숫자가 있으면 선택은 LLM 것, 설명은 규칙 문장")
    void replacesBadComment() throws Exception {
        BEHAVIOR.set(prompt -> answer(prompt, false, "가슴을 4세트로 시작하는 순서예요."));

        long id = idOf(createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedBy").value("LLM"))
                .andExpect(jsonPath("$.comment").value("큰 근육 위주로, 큰 부위부터 하는 순서예요.")));

        assertEquals("RULE", row(id).get("comment_by"));
    }

    // ── 도우미

    /** 프롬프트의 입력에서 부위마다 첫 후보를 골라 답을 짓는다 */
    private static String answer(LlmClient.Prompt prompt, boolean reverse, String comment) {
        String user = prompt.user();
        String json = user.substring(user.indexOf("<루틴입력>") + "<루틴입력>".length(), user.indexOf("</루틴입력>"));
        JsonNode slots = JsonMapper.shared().readTree(json).path("slots");
        List<String> items = new ArrayList<>();
        for (JsonNode slot : slots) {
            items.add("{\"slot\":\"" + slot.path("slot").asString() + "\",\"exerciseId\":\""
                    + slot.path("candidates").path(0).path("exerciseId").asString() + "\"}");
        }
        if (reverse) {
            items = new ArrayList<>(items.reversed());
        }
        return "{\"items\":[" + String.join(",", items) + "],\"comment\":\"" + comment + "\"}";
    }

    private ResultActions createToday() throws Exception {
        return mvc.perform(post("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap(
                "SELECT llm_attempts, fallback_reason, comment_by, final_check_passed FROM routines WHERE id = ?", id);
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
