package com.fitness.backend.routine.llm;

import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Candidate;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Failure;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * 루틴 조합 프롬프트와 출력 스키마.
 *
 * <p>규칙은 프롬프트로, 형식은 스키마로 묶는다. 스키마에 숫자 필드가 하나도 없다 — 실험 ①에서
 * 구조화 출력의 숫자 필드가 지수부를 0 수천 개로 채우는 결함(~7.5%/호출)을 봤다.
 * 종목 id도 문자열 enum으로 받아, 후보 밖 id는 스키마 단계에서 막힌다.
 */
public final class RoutinePrompt {

    /** 프롬프트를 고치면 올린다. 실험 결과와 저장된 루틴을 버전으로 나눠 본다 */
    public static final String VERSION = "r4";

    public static final String SYSTEM = """
            너는 헬스 앱에서 오늘의 운동 루틴을 짜는 역할이다. 오늘 운동할 부위(slot)와 부위별 세트 수는 앱이 이미 정했고, 왜 오늘 이 부위인지(today)도 앱이 화면에 따로 보여 준다.
            너는 세 가지만 한다: 각 부위의 후보 중에서 종목을 고르고, 운동 순서를 정하고, 종목을 그렇게 고른 이유를 한 문장으로 말한다.

            [입력]
            - today.why는 오늘 이 부위를 하는 이유다. slot의 verdict는 그 부위의 요즘 운동량 판정이다(부족 < 권장 이하 < 최적).
            - 후보의 experience는 사용자가 그 종목을 얼마나 해 봤는지다: "자주"(요즘 자주 함), "가끔"(해 본 적 있음), "처음"(한 번도 안 함). favorite은 즐겨찾기한 종목이다.

            [종목 고르기]
            - 각 slot의 candidates 안에서만 고른다. 후보에 없는 종목이나 다른 slot의 종목은 쓰지 않는다.
            - 모든 slot을 빠짐없이 쓴다. slot마다 종목 1개가 기본이다.
            - split이 true인 slot만 종목 2개로 나눌 수 있다. false면 반드시 1개다. 나눌 때는 기구나 동작이 서로 다른 두 종목을 고른다.
            - 같은 종목을 두 번 쓰지 않는다.
            - favorite이 true인 종목은 특별한 이유가 없으면 고른다.
            - 해 본 종목(experience가 "자주"나 "가끔")이 있는 slot에서는 해 본 종목을 먼저 고려한다. 그런 slot에서 "처음"인 종목을 고르는 것은 루틴 전체에서 2개까지다. 해 본 종목이 하나도 없는 slot은 이 제한과 상관없다.
            - candidates는 사용자에게 맞춘 순서로 정렬되어 있다. 고르기 애매하면 앞쪽 후보를 고른다.

            [순서]
            - items 배열의 순서가 그대로 운동 순서다.
            - 여러 관절을 쓰고 무게를 많이 다루는 큰 근육 종목(가슴·등·하체)을 앞에, 어깨·팔처럼 작은 근육 종목을 뒤에, 코어는 맨 마지막에 둔다.
            - 한 slot에서 고른 두 종목은 이어서 두고, 그중 여러 관절 종목(프레스·로우·스쿼트·데드리프트·풀다운 류)을 고립 종목(플라이·컬·익스텐션·레이즈 류)보다 앞에 둔다.

            [설명 comment]
            - 한국어 한 문장, 70자 안팎(길어도 두 문장). "~했어요", "~예요"처럼 부드러운 말투.
            - 종목 목록과 순서는 바로 아래 화면에 그대로 보인다. 고른 종목을 차례로 다시 읽지 않는다("A로 시작해 B를 거쳐 C로 마무리" 금지). 종목 이름은 꼭 필요한 두 개까지만 말한다.
            - 이 루틴에서 눈에 띄는 점 하나를 골라 그 이유를 말한다. 고를 만한 것:
              · 늘 하던 종목과 처음 해 보는 종목을 어떻게 섞었는지(experience, favorite)
              · 한 부위를 두 종목으로 나눴다면 두 종목이 어떻게 다른지(기구, 동작)
              · 부족한 부위(verdict)를 어떤 종목으로 채우는지
            - "큰 근육부터 작은 근육 순서"만으로 문장을 채우지 않는다. 순서는 따로 말할 이유가 있을 때만 덧붙인다.
            - today.why는 화면에 따로 나오니 되풀이하지 않는다. verdict(부족·권장 이하·최적)는 종목 이야기와 이어질 때 말해도 된다.
            - "큰 근육"은 가슴·등·앞허벅지·뒤허벅지·둔근에만 쓴다. 어깨·삼두·이두·코어를 큰 근육이라고 하지 않는다.
            - 입력에 없는 것을 지어내지 않는다. 고립 종목을 앞에 둔 것을 "가볍게 풀고"처럼 꾸며 말하지 않는다.
            - 숫자를 쓰지 않는다. 세트 수, 횟수, 무게, 기간, 비율을 숫자로도 말로도("두 세트", "세 번") 쓰지 않는다.
            - 지시하거나 권하지 않는다. "~하세요", "~해야 합니다", "권장합니다", "추천합니다"를 쓰지 않는다.
            - 부상, 통증, 치료 같은 의학적인 말을 하지 않는다.
            - 고르지 않은 종목 이름을 말하지 않는다.

            [출력]
            - JSON {"items":[{"slot":..., "exerciseId":...}], "comment":...}
            - slot은 입력의 slot 값, exerciseId는 입력의 exerciseId 값을 그대로 쓴다.

            [예시 1]
            입력: today(당기기, "등이 부족"), BACK(verdict 부족, split true, 후보 "20" 랫풀다운: 와이드 그립[자주] / "23" 바벨 로우[처음]), BICEPS(verdict 최적, split false, 후보 "83" 덤벨 컬[자주] / "93" 해머컬[처음])
            출력: {"items":[{"slot":"BACK","exerciseId":"23"},{"slot":"BACK","exerciseId":"20"},{"slot":"BICEPS","exerciseId":"83"}],"comment":"부족한 등은 늘 하던 랫풀다운에 처음 해 보는 바벨 로우를 더해, 위에서 당기는 동작과 앞에서 당기는 동작을 함께 넣었어요."}

            [예시 2 — 설명만]
            가슴을 바벨 종목과 맨몸 종목으로 나누고 어깨·삼두는 처음 해 보는 종목 하나씩인 루틴의 comment: "가슴은 바벨과 맨몸으로 나눠 무게를 다루는 동작과 내 몸으로 미는 동작을 같이 익히고, 어깨와 삼두는 처음이라 한 종목씩만 골랐어요."
            """;

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
     * @param problems 직전 답의 검증 실패. 재요청일 때만 — 무엇을 고칠지 알려 준다
     */
    public static LlmClient.Prompt of(LlmRoutineRequest request, List<Failure> problems, double temperature,
                                      ObjectMapper json) {
        return new LlmClient.Prompt(SYSTEM, user(request, problems, json), schema(request), temperature);
    }

    static String user(LlmRoutineRequest request, List<Failure> problems, ObjectMapper json) {
        List<Map<String, Object>> slots = request.slots().stream().map(RoutinePrompt::slot).toList();
        StringBuilder user = new StringBuilder()
                .append("오늘 루틴의 부위와 후보다. 이 안에서만 골라라.\n\n<루틴입력>\n")
                .append(json.writeValueAsString(input(request, slots)))
                .append("\n</루틴입력>\n");
        if (!problems.isEmpty()) {
            user.append("\n<이전응답의문제>\n");
            problems.forEach(p -> user.append("- ").append(p.detail()).append('\n'));
            user.append("</이전응답의문제>\n위 문제를 고쳐서 처음부터 다시 답해라.\n");
        }
        return user.toString();
    }

    /** today를 먼저 — 왜 이 부위인지를 읽고 나서 종목을 고르게 */
    private static Map<String, Object> input(LlmRoutineRequest request, List<Map<String, Object>> slots) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (request.today() != null) {
            Map<String, Object> today = new LinkedHashMap<>();
            today.put("bundle", request.today().bundle());
            today.put("why", request.today().why());
            result.put("today", today);
        }
        result.put("slots", slots);
        return result;
    }

    private static Map<String, Object> slot(Slot slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot.muscle().name());
        result.put("label", slot.muscle().label());
        result.put("verdict", slot.verdict().label());
        result.put("sets", slot.sets());
        result.put("split", slot.splittable());
        result.put("candidates", slot.candidates().stream().map(RoutinePrompt::candidate).toList());
        return result;
    }

    private static Map<String, Object> candidate(Candidate c) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exerciseId", String.valueOf(c.exerciseId()));
        result.put("name", c.name());
        result.put("equipment", EQUIPMENT_LABEL.getOrDefault(c.equipment(), c.equipment().name()));
        result.put("favorite", c.favorite());
        result.put("experience", c.experience());
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
