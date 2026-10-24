package com.fitness.backend.routine.llm;

import java.util.Map;

/**
 * LLM 호출 포트. 「루틴생성_로직_설계서」 5.3 — provider를 바꿔도 이 경계 밖은 모른다.
 *
 * <p>한 번 부르고 한 번 답을 받는다. 재요청·검증·폴백은 {@link LlmRoutineComposer}가 한다 —
 * 어댑터는 "답이 왔는가"만 책임진다. 답의 내용이 맞는지는 모른다.
 */
public interface LlmClient {

    /**
     * @throws LlmCallException 답을 받지 못함(네트워크·한도·타임아웃·차단·잘림)
     */
    Response generate(Prompt prompt);

    /**
     * @param responseSchema 구조화 출력 스키마(OpenAPI 부분집합). 형식을 프롬프트가 아니라 스키마로 묶는다(LLM 실험기록 2.5)
     */
    record Prompt(String system, String user, Map<String, Object> responseSchema, double temperature) {
    }

    /** @param text 모델이 낸 JSON 문자열. 파싱은 검증기가 한다 — 깨진 JSON도 L1 실패로 세야 해서 */
    record Response(String text, int promptTokens, int outputTokens, long latencyMs) {
    }
}
