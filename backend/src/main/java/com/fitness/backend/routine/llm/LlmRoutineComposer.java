package com.fitness.backend.routine.llm;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.PlannedItem;
import com.fitness.backend.routine.domain.RoutineEnums.Author;
import com.fitness.backend.routine.domain.RoutineEnums.FallbackReason;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Failure;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Pick;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Result;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM으로 종목·순서·설명을 고른다.
 *
 * <pre>
 *   부르기 → 검증(L1~L6)
 *     L1~L5 통과        → 받아들임. L6만 걸리면 설명은 규칙 문장
 *     L1~L5 실패        → 무엇이 틀렸는지 알려 주고 다시 (최대 maxRetries번)
 *     끝내 실패         → 규칙 선택 (VALIDATION_FAILED)
 *   호출 자체가 실패    → 바로 규칙 선택 (LLM_ERROR). 같은 장애를 다시 기다리게 하지 않는다
 * </pre>
 *
 * <p>세트는 LLM이 정하지 않는다. 한 슬롯에 두 종목을 고르면 슬롯 세트를 앞 종목부터 고르게 나눈다.
 */
public final class LlmRoutineComposer {

    private final LlmClient client;
    private final ObjectMapper json;
    private final int maxRetries;
    private final double temperature;

    public LlmRoutineComposer(LlmClient client, ObjectMapper json, int maxRetries, double temperature) {
        this.client = client;
        this.json = json;
        this.maxRetries = maxRetries;
        this.temperature = temperature;
    }

    /**
     * 한 번 부른 기록. 운영에서는 횟수만 저장하고, 실험은 전부 남긴다.
     *
     * @param error 호출 실패면 그 사유, 아니면 null
     */
    public record Attempt(int number, long latencyMs, int promptTokens, int outputTokens, String text,
                          List<Failure> failures, String error) {
    }

    /**
     * @param fallbackReason 규칙이 고른 이유. LLM이 골랐거나 고를 슬롯이 없으면 null
     */
    public record Composition(List<PlannedItem> items, String comment, Author generatedBy, Author commentBy,
                              FallbackReason fallbackReason, List<Attempt> attempts) {

        public int llmAttempts() {
            return attempts.size();
        }
    }

    public Composition compose(List<RoutineSlot> slots,
                               Map<MuscleGroup, List<ExerciseOption>> candidates,
                               Map<Long, String> names) {
        LlmRoutineRequest request = LlmRoutineRequest.of(slots, candidates, names);
        if (request.isEmpty()) {
            // 회복 중이거나 충분해서 할 부위가 없다. 물어볼 것이 없다
            return rule(slots, candidates, null, List.of());
        }

        List<Attempt> attempts = new ArrayList<>();
        List<Failure> problems = List.of();
        for (int number = 1; number <= 1 + maxRetries; number++) {
            LlmClient.Response response;
            try {
                response = client.generate(RoutinePrompt.of(request, problems, temperature, json));
            } catch (LlmCallException e) {
                attempts.add(new Attempt(number, 0, 0, 0, null, List.of(), e.kind() + " " + e.getMessage()));
                return rule(slots, candidates, FallbackReason.LLM_ERROR, attempts);
            }

            Result result = LlmRoutineValidator.validate(request, response.text(), json);
            attempts.add(new Attempt(number, response.latencyMs(), response.promptTokens(), response.outputTokens(),
                    response.text(), result.failures(), null));
            if (result.selectionOk()) {
                List<PlannedItem> items = toItems(request, result.picks());
                return result.commentOk()
                        ? new Composition(items, result.comment().strip(), Author.LLM, Author.LLM, null, attempts)
                        : new Composition(items, RuleComposer.comment(items), Author.LLM, Author.RULE, null, attempts);
            }
            problems = result.failures();
        }
        return rule(slots, candidates, FallbackReason.VALIDATION_FAILED, attempts);
    }

    private static Composition rule(List<RoutineSlot> slots, Map<MuscleGroup, List<ExerciseOption>> candidates,
                                    FallbackReason reason, List<Attempt> attempts) {
        List<PlannedItem> items = RuleComposer.compose(slots, candidates);
        return new Composition(items, RuleComposer.comment(items), Author.RULE, Author.RULE, reason, attempts);
    }

    /** 응답 순서가 운동 순서다. 슬롯 세트는 그 슬롯에서 고른 종목 수로 나눈다(앞 종목이 하나 더) */
    static List<PlannedItem> toItems(LlmRoutineRequest request, List<Pick> picks) {
        Map<MuscleGroup, Integer> counts = new EnumMap<>(MuscleGroup.class);
        picks.forEach(p -> counts.merge(p.slot(), 1, Integer::sum));
        Map<MuscleGroup, Integer> used = new EnumMap<>(MuscleGroup.class);

        List<PlannedItem> items = new ArrayList<>();
        for (Pick pick : picks) {
            int sets = request.slot(pick.slot()).orElseThrow().sets();
            int index = used.merge(pick.slot(), 1, Integer::sum) - 1;
            items.add(new PlannedItem(pick.slot(), pick.exerciseId(),
                    RuleComposer.split(sets, counts.get(pick.slot()))[index]));
        }
        return List.copyOf(items);
    }
}
