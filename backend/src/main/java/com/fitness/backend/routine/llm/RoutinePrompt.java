package com.fitness.backend.routine.llm;

import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Candidate;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Failure;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * 루틴 조합 프롬프트와 출력 스키마. 설계서 5.1·5.2.
 *
 * <p>규칙은 프롬프트로, 형식은 스키마로 묶는다. 스키마에 숫자 필드가 하나도 없다 — 실험 ①에서
 * 구조화 출력의 숫자 필드가 지수부를 0 수천 개로 채우는 결함(~7.5%/호출)을 봤다(LLM 실험기록 7.4).
 * 종목 id도 문자열 enum으로 받아, 후보 밖 id는 스키마 단계에서 막힌다.
 */
public final class RoutinePrompt {

    /** 프롬프트를 고치면 올린다. 실험 결과와 저장된 루틴을 버전으로 나눠 본다 */
    public static final String VERSION = "r2";

    public static final String SYSTEM = """
            너는 헬스 앱에서 오늘의 운동 루틴을 짜는 역할이다. 오늘 운동할 부위(slot)와 부위별 세트 수는 앱이 이미 정했다.
            너는 세 가지만 한다: 각 부위의 후보 중에서 종목을 고르고, 운동 순서를 정하고, 그렇게 짠 이유를 짧게 설명한다.

            [종목 고르기]
            - 각 slot의 candidates 안에서만 고른다. 후보에 없는 종목이나 다른 slot의 종목은 쓰지 않는다.
            - 모든 slot을 빠짐없이 쓴다. slot마다 종목 1개가 기본이다.
            - sets가 %d 이상인 slot만 종목 2개로 나눌 수 있다. 그보다 적으면 반드시 1개다. 나눌 때는 기구나 동작이 서로 다른 두 종목을 고른다.
            - 같은 종목을 두 번 쓰지 않는다.
            - favorite이 true인 종목(사용자가 즐겨찾기한 종목)은 특별한 이유가 없으면 고른다.
            - 해 본 종목(firstTime이 false)이 있는 slot에서는 해 본 종목을 먼저 고려한다. 그런 slot에서 처음 해 보는 종목(firstTime이 true)을 고르는 것은 루틴 전체에서 2개까지다. 해 본 종목이 하나도 없는 slot은 이 제한과 상관없다.
            - candidates는 사용자에게 맞춘 순서로 정렬되어 있다. 고르기 애매하면 앞쪽 후보를 고른다.

            [순서]
            - items 배열의 순서가 그대로 운동 순서다.
            - 여러 관절을 쓰고 무게를 많이 다루는 큰 근육 종목(가슴·등·하체)을 앞에, 어깨·팔처럼 작은 근육 종목을 뒤에, 코어는 맨 마지막에 둔다.
            - 한 slot에서 고른 두 종목은 이어서 두고, 그중 여러 관절 종목(프레스·로우·스쿼트·데드리프트·풀다운 류)을 고립 종목(플라이·컬·익스텐션·레이즈 류)보다 앞에 둔다.

            [설명 comment]
            - 한국어 한두 문장, 120자 이내. "~했어요", "~예요"처럼 부드러운 말투.
            - 종목을 고른 이유와 순서의 이유만 말한다. 이 부위가 부족한지 같은 판정은 앱이 따로 보여 주므로 말하지 않는다.
            - 숫자를 쓰지 않는다. 세트 수, 횟수, 무게, 기간, 비율을 숫자로도 말로도("두 세트", "세 번") 쓰지 않는다.
            - 지시하거나 권하지 않는다. "~하세요", "~해야 합니다", "권장합니다", "추천합니다"를 쓰지 않는다.
            - 부상, 통증, 치료 같은 의학적인 말을 하지 않는다.
            - 고르지 않은 종목 이름을 말하지 않는다.

            [출력]
            - JSON {"items":[{"slot":..., "exerciseId":...}], "comment":...}
            - slot은 입력의 slot 값, exerciseId는 입력의 exerciseId 값을 그대로 쓴다.

            [예시]
            입력: BACK(sets 5, 후보 "20" 랫풀다운: 와이드 그립 / "23" 바벨 로우), BICEPS(sets 3, 후보 "83" 덤벨 컬 / "93" 해머컬)
            출력: {"items":[{"slot":"BACK","exerciseId":"23"},{"slot":"BACK","exerciseId":"20"},{"slot":"BICEPS","exerciseId":"83"}],"comment":"등은 바벨 로우로 무겁게 시작해 랫풀다운으로 당기는 방향을 바꾸고, 덤벨 컬로 팔을 마무리하는 순서예요."}
            """.formatted(RuleComposer.SPLIT_FROM_SETS);

    private static final Map<Equipment, String> EQUIPMENT_LABEL = Map.of(
            Equipment.BARBELL, "바벨",
            Equipment.DUMBBELL, "덤벨",
            Equipment.MACHINE, "머신",
            Equipment.CABLE, "케이블",
            Equipment.BODYWEIGHT, "맨몸",
            Equipment.PULLUP_BAR, "철봉");

    private RoutinePrompt() {
    }

    /**
     * @param problems 직전 답의 검증 실패. 재요청일 때만 — 무엇을 고칠지 알려 준다(LLM 실험기록 4절)
     */
    public static LlmClient.Prompt of(LlmRoutineRequest request, List<Failure> problems, double temperature,
                                      ObjectMapper json) {
        return new LlmClient.Prompt(SYSTEM, user(request, problems, json), schema(request), temperature);
    }

    static String user(LlmRoutineRequest request, List<Failure> problems, ObjectMapper json) {
        List<Map<String, Object>> slots = request.slots().stream().map(RoutinePrompt::slot).toList();
        StringBuilder user = new StringBuilder()
                .append("오늘 루틴의 부위와 후보다. 이 안에서만 골라라.\n\n<루틴입력>\n")
                .append(json.writeValueAsString(Map.of("slots", slots)))
                .append("\n</루틴입력>\n");
        if (!problems.isEmpty()) {
            user.append("\n<이전응답의문제>\n");
            problems.forEach(p -> user.append("- ").append(p.detail()).append('\n'));
            user.append("</이전응답의문제>\n위 문제를 고쳐서 처음부터 다시 답해라.\n");
        }
        return user.toString();
    }

    private static Map<String, Object> slot(Slot slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot.muscle().name());
        result.put("label", slot.muscle().label());
        result.put("sets", slot.sets());
        result.put("candidates", slot.candidates().stream().map(RoutinePrompt::candidate).toList());
        return result;
    }

    private static Map<String, Object> candidate(Candidate c) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exerciseId", String.valueOf(c.exerciseId()));
        result.put("name", c.name());
        result.put("equipment", EQUIPMENT_LABEL.getOrDefault(c.equipment(), c.equipment().name()));
        result.put("favorite", c.favorite());
        result.put("firstTime", c.firstTime());
        return result;
    }

    /** slot·exerciseId는 이번 요청의 값만 enum으로. 숫자 필드는 없다 */
    static Map<String, Object> schema(LlmRoutineRequest request) {
        List<String> slotKeys = request.slots().stream().map(s -> s.muscle().name()).toList();
        List<String> exerciseIds = request.slots().stream()
                .flatMap(s -> s.candidates().stream())
                .map(c -> String.valueOf(c.exerciseId()))
                .distinct()
                .toList();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", Map.of(
                "slot", Map.of("type", "string", "enum", slotKeys),
                "exerciseId", Map.of("type", "string", "enum", exerciseIds)));
        item.put("required", List.of("slot", "exerciseId"));
        item.put("propertyOrdering", List.of("slot", "exerciseId"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", Map.of(
                "items", Map.of("type", "array", "items", item),
                "comment", Map.of("type", "string")));
        root.put("required", List.of("items", "comment"));
        // 고른 다음에 설명하게 — 설명을 먼저 쓰면 설명에 맞춰 고른다
        root.put("propertyOrdering", List.of("items", "comment"));
        return root;
    }
}
