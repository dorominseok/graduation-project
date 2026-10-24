package com.fitness.backend.routine.llm;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static com.fitness.backend.exercise.domain.Equipment.DUMBBELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.routine.llm.LlmCallException.Kind;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Candidate;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 프롬프트·스키마와 Gemini 응답 해석. 실제 호출은 하지 않는다(LLM 실험기록 6.2 — CI에서 실호출 금지). */
class RoutinePromptAndGeminiTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final LlmRoutineRequest REQUEST = new LlmRoutineRequest(List.of(
            new Slot(BACK, 4, List.of(
                    new Candidate(20, "랫풀다운: 와이드 그립", CABLE, true, false),
                    new Candidate(23, "바벨 로우", BARBELL, false, true))),
            new Slot(BICEPS, 3, List.of(new Candidate(83, "덤벨 컬", DUMBBELL, false, true)))));

    @Test
    @DisplayName("스키마는 이번 요청의 슬롯·종목만 enum으로 받고, 숫자 필드가 하나도 없다")
    void schemaHasOnlyEnumsAndNoNumbers() {
        String schema = JSON.writeValueAsString(RoutinePrompt.schema(REQUEST));

        assertTrue(schema.contains("\"enum\":[\"BACK\",\"BICEPS\"]"), schema);
        assertTrue(schema.contains("\"enum\":[\"20\",\"23\",\"83\"]"), schema);
        // 실험 ①의 숫자 직렬화 결함을 구조로 피한다(LLM 실험기록 7.4)
        assertFalse(schema.contains("\"number\"") || schema.contains("\"integer\""), schema);
    }

    @Test
    @DisplayName("입력에는 세트는 참고로, 종목 id는 문자열로, 기구는 한국어로 들어간다")
    void userMessage() {
        String user = RoutinePrompt.user(REQUEST, List.of(), JSON);

        assertTrue(user.contains("\"slot\":\"BACK\",\"label\":\"등\",\"sets\":4"), user);
        assertTrue(user.contains("\"exerciseId\":\"20\",\"name\":\"랫풀다운: 와이드 그립\",\"equipment\":\"케이블\""), user);
        assertTrue(user.contains("\"favorite\":true"), user);
        assertFalse(user.contains("<이전응답의문제>"));
    }

    @Test
    @DisplayName("응답에서 글자와 토큰 수를 꺼낸다")
    void parsesResponse() {
        LlmClient.Response response = GeminiClient.parse(JSON.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"items\\":"},{"text":"[]}"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":80}}"""), 950);

        assertEquals("{\"items\":[]}", response.text());
        assertEquals(1200, response.promptTokens());
        assertEquals(80, response.outputTokens());
        assertEquals(950, response.latencyMs());
    }

    @Test
    @DisplayName("잘림·차단·빈 답은 예외 — 재요청하지 않고 규칙으로 간다")
    void rejectsUnusableResponses() {
        assertEquals(Kind.TRUNCATED, assertThrows(LlmCallException.class, () -> GeminiClient.parse(JSON.readTree(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\"}]},\"finishReason\":\"MAX_TOKENS\"}]}"), 1))
                .kind());
        assertEquals(Kind.BLOCKED, assertThrows(LlmCallException.class, () -> GeminiClient.parse(JSON.readTree(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"), 1)).kind());
        assertEquals(Kind.EMPTY, assertThrows(LlmCallException.class, () -> GeminiClient.parse(JSON.readTree(
                "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}"), 1)).kind());
    }
}
