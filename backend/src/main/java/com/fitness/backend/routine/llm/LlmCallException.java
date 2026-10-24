package com.fitness.backend.routine.llm;

/** LLM에게서 쓸 수 있는 답을 받지 못했다. 재요청하지 않고 바로 규칙으로 간다(설계서 6.1) */
public class LlmCallException extends RuntimeException {

    public enum Kind {
        /** 연결·응답 타임아웃 */
        TIMEOUT,
        /** 4xx·5xx — 무료 한도(429)·과부하(503) 포함 */
        HTTP,
        NETWORK,
        /** 안전 필터 등으로 막힘 */
        BLOCKED,
        /** 출력 토큰 한도에 걸려 잘림 */
        TRUNCATED,
        /** 답이 비었음 */
        EMPTY
    }

    private final Kind kind;

    public LlmCallException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmCallException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
