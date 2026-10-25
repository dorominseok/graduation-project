package com.fitness.backend.routine.llm;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.analysis.domain.VolumeVerdict.INSUFFICIENT;
import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static com.fitness.backend.exercise.domain.Equipment.DUMBBELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.PlannedItem;
import com.fitness.backend.routine.domain.RoutineEnums.Author;
import com.fitness.backend.routine.domain.RoutineEnums.FallbackReason;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.llm.LlmCallException.Kind;
import com.fitness.backend.routine.llm.LlmRoutineComposer.Composition;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 재요청·폴백·세트 나누기. 실제 LLM 대신 정해 둔 답을 차례로 내는 가짜를 쓴다. */
class LlmRoutineComposerTest {

    private static final List<RoutineSlot> SLOTS = List.of(
            new RoutineSlot(BACK, 6, BigDecimal.ZERO, INSUFFICIENT, false),
            new RoutineSlot(BICEPS, 3, BigDecimal.ZERO, INSUFFICIENT, false));
    private static final Map<MuscleGroup, List<ExerciseOption>> CANDIDATES = Map.of(
            BACK, List.of(new ExerciseOption(20L, "랫풀다운", CABLE, false, 2, false, 1),
                    new ExerciseOption(23L, "로우", BARBELL, false, 0, true, null)),
            BICEPS, List.of(new ExerciseOption(83L, "컬", DUMBBELL, false, 0, true, 1)));
    private static final Map<Long, String> NAMES = Map.of(20L, "랫풀다운: 와이드 그립", 23L, "바벨 로우", 83L, "덤벨 컬");

    private static final String GOOD = """
            {"items":[{"slot":"BACK","exerciseId":"23"},{"slot":"BACK","exerciseId":"20"},{"slot":"BICEPS","exerciseId":"83"}],
             "comment":"등은 바벨로 먼저 무겁게 당기고 케이블로 이어 간 뒤, 덤벨 컬로 마무리하는 순서예요."}""";
    private static final String MISSING_BICEPS = """
            {"items":[{"slot":"BACK","exerciseId":"23"}],"comment":"등을 바벨로 당기는 순서예요."}""";

    /** 정해 둔 답(문자열) 또는 예외를 차례로 낸다. 받은 프롬프트를 남긴다 */
    private static final class ScriptedClient implements LlmClient {
        private final Deque<Object> script;
        private final List<Prompt> prompts = new ArrayList<>();

        ScriptedClient(Object... script) {
            this.script = new ArrayDeque<>(List.of(script));
        }

        @Override
        public Response generate(Prompt prompt) {
            prompts.add(prompt);
            Object next = script.pop();
            if (next instanceof LlmCallException e) {
                throw e;
            }
            return new Response((String) next, 100, 50, 900);
        }
    }

    private static Composition compose(ScriptedClient client) {
        return new LlmRoutineComposer(client, JsonMapper.shared(), 2, 0.4).compose(SLOTS, CANDIDATES, NAMES);
    }

    @Test
    @DisplayName("통과하면 LLM의 순서를 쓰고, 두 종목을 고른 슬롯은 세트를 앞 종목부터 나눈다")
    void acceptsLlmChoice() {
        Composition result = compose(new ScriptedClient(GOOD));

        assertEquals(List.of(new PlannedItem(BACK, 23L, 3), new PlannedItem(BACK, 20L, 3),
                new PlannedItem(BICEPS, 83L, 3)), result.items());
        assertEquals(Author.LLM, result.generatedBy());
        assertEquals(Author.LLM, result.commentBy());
        assertNull(result.fallbackReason());
        assertEquals(1, result.llmAttempts());
    }

    @Test
    @DisplayName("설명만 걸리면(L6) 선택은 받고 설명은 규칙 문장 — 재요청하지 않는다")
    void commentOnlyFailureKeepsSelection() {
        String withNumber = GOOD.replace("등은 바벨로", "등은 4세트를 바벨로");

        Composition result = compose(new ScriptedClient(withNumber));

        assertEquals(Author.LLM, result.generatedBy());
        assertEquals(Author.RULE, result.commentBy());
        assertEquals(RuleComposer.comment(result.items()), result.comment());
        assertEquals(1, result.llmAttempts());
    }

    @Test
    @DisplayName("선택이 틀리면 무엇이 틀렸는지 알려 주고 다시 묻는다")
    void retriesWithProblems() {
        ScriptedClient client = new ScriptedClient(MISSING_BICEPS, GOOD);

        Composition result = compose(client);

        assertEquals(Author.LLM, result.generatedBy());
        assertEquals(2, result.llmAttempts());
        assertTrue(client.prompts.get(1).user().contains("BICEPS slot의 종목이 0개다"), client.prompts.get(1).user());
        assertTrue(client.prompts.get(0).user().contains("<루틴입력>"));
    }

    @Test
    @DisplayName("세 번(재요청 두 번) 다 틀리면 규칙이 고른다 — VALIDATION_FAILED")
    void fallsBackAfterRetries() {
        Composition result = compose(new ScriptedClient(MISSING_BICEPS, MISSING_BICEPS, MISSING_BICEPS));

        assertEquals(Author.RULE, result.generatedBy());
        assertEquals(FallbackReason.VALIDATION_FAILED, result.fallbackReason());
        assertEquals(3, result.llmAttempts());
        assertEquals(RuleComposer.compose(SLOTS, CANDIDATES), result.items());
    }

    @Test
    @DisplayName("호출이 실패하면 기다리지 않고 바로 규칙 — LLM_ERROR")
    void callFailureFallsBackImmediately() {
        Composition result = compose(new ScriptedClient(new LlmCallException(Kind.TIMEOUT, "응답 없음"), GOOD));

        assertEquals(Author.RULE, result.generatedBy());
        assertEquals(FallbackReason.LLM_ERROR, result.fallbackReason());
        assertEquals(1, result.llmAttempts());
    }

    @Test
    @DisplayName("할 부위가 없으면 묻지 않는다")
    void nothingToAsk() {
        ScriptedClient client = new ScriptedClient();

        Composition result = new LlmRoutineComposer(client, JsonMapper.shared(), 2, 0.4)
                .compose(List.of(), Map.of(), Map.of());

        assertTrue(result.items().isEmpty());
        assertEquals(RuleComposer.REST_COMMENT, result.comment());
        assertTrue(client.prompts.isEmpty());
    }
}
