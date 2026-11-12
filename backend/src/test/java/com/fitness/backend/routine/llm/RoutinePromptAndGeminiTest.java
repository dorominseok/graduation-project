package com.fitness.backend.routine.llm;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.analysis.domain.MuscleGroup.QUADS;
import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static com.fitness.backend.exercise.domain.Equipment.DUMBBELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.llm.LlmCallException.Kind;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Candidate;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 프롬프트·스키마와 Gemini 응답 해석. 실제 호출은 하지 않는다(CI에서 실호출 금지). */
class RoutinePromptAndGeminiTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final LlmRoutineRequest REQUEST = new LlmRoutineRequest(List.of(
            new Slot(BACK, 4, false, List.of(
                    new Candidate(20, "랫풀다운: 와이드 그립", CABLE, true, false, true),
                    new Candidate(23, "바벨 로우", BARBELL, false, true, false))),
            new Slot(BICEPS, 3, false, List.of(new Candidate(83, "덤벨 컬", DUMBBELL, false, true, true)))));

    @Test
    @DisplayName("스키마는 이번 요청의 슬롯·종목만 enum으로 받고, 숫자 필드가 하나도 없다")
    void schemaHasOnlyEnumsAndNoNumbers() {
        String schema = JSON.writeValueAsString(RoutinePrompt.schema(REQUEST));

        assertTrue(schema.contains("\"enum\":[\"BACK\",\"BICEPS\"]"), schema);
        assertTrue(schema.contains("\"enum\":[\"20\",\"23\",\"83\"]"), schema);
        // 실험 ①의 숫자 직렬화 결함을 구조로 피한다
        assertFalse(schema.contains("\"number\"") || schema.contains("\"integer\""), schema);
    }

    @Test
    @DisplayName("입력에는 세트는 참고로, 종목 id는 문자열로, 기구는 한국어로 들어간다")
    void userMessage() {
        String user = RoutinePrompt.user(REQUEST, List.of(), JSON);

        assertTrue(user.contains("\"slot\":\"BACK\",\"label\":\"등\",\"sets\":4,\"split\":false"), user);
        assertTrue(user.contains("\"exerciseId\":\"20\",\"name\":\"랫풀다운: 와이드 그립\",\"equipment\":\"케이블\""), user);
        assertTrue(user.contains("\"favorite\":true"), user);
        assertFalse(user.contains("<이전응답의문제>"));
    }

    @Test
    @DisplayName("해 본 종목이 없는 큰 근육은 입문 대표 종목·즐겨찾기만 후보로 준다 — 그 밖의 종목으로는 나눌 수 없게")
    void untriedBigMuscleOffersStartersOnly() {
        List<ExerciseOption> quads = List.of(
                new ExerciseOption(47L, "스쿼트", BARBELL, false, 0, true, 1),
                new ExerciseOption(42L, "런지", DUMBBELL, false, 0, true, 2),
                new ExerciseOption(46L, "레그 익스텐션", CABLE, false, 0, true, null),
                new ExerciseOption(40L, "레그프레스", CABLE, true, 0, true, null));
        List<ExerciseOption> biceps = List.of(
                new ExerciseOption(83L, "컬", DUMBBELL, false, 0, true, 1),
                new ExerciseOption(93L, "해머컬", DUMBBELL, false, 0, true, null));
        List<RoutineSlot> slots = List.of(
                new RoutineSlot(QUADS, 6, BigDecimal.ZERO, VolumeVerdict.INSUFFICIENT, false),
                new RoutineSlot(BICEPS, 6, BigDecimal.ZERO, VolumeVerdict.INSUFFICIENT, false));
        Map<Long, String> names = Map.of(47L, "바벨 스쿼트", 42L, "덤벨 런지", 46L, "레그 익스텐션", 40L, "레그프레스",
                83L, "덤벨 컬", 93L, "해머컬");

        LlmRoutineRequest request = LlmRoutineRequest.of(slots, Map.of(QUADS, quads, BICEPS, biceps), names);

        Slot quadsSlot = request.slot(QUADS).orElseThrow();
        assertTrue(quadsSlot.splittable());
        assertEquals(List.of(47L, 42L, 40L), quadsSlot.candidates().stream().map(Candidate::exerciseId).toList());
        // 작은 근육은 나누지 않으므로 후보를 줄이지 않는다
        Slot bicepsSlot = request.slot(BICEPS).orElseThrow();
        assertFalse(bicepsSlot.splittable());
        assertEquals(2, bicepsSlot.candidates().size());
        // 대표 종목 표시는 서버만 쓴다
        assertFalse(RoutinePrompt.user(request, List.of(), JSON).contains("starter"));
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
