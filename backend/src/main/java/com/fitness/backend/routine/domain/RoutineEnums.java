package com.fitness.backend.routine.domain;

/** 루틴의 상태 값들. 스키마 CHECK(V10)와 같은 문자열로 저장한다. */
public final class RoutineEnums {

    private RoutineEnums() {
    }

    /** 종목을 누가 골랐나 / 설명을 누가 썼나 */
    public enum Author {
        LLM,
        RULE
    }

    /** 규칙이 고른 이유 */
    public enum FallbackReason {
        /** LLM을 켜지 않음({@code app.llm.enabled=false}) — 개발·CI, 키를 넣기 전의 운영 */
        LLM_DISABLED,
        /** 호출 실패(네트워크·한도·타임아웃) */
        LLM_ERROR,
        /** 재요청까지 검증 실패 */
        VALIDATION_FAILED
    }

    /** 만족도 3단계(설계서 11.4) */
    public enum FeedbackRating {
        GOOD,
        OKAY,
        BAD
    }

    /** 보통·별로일 때 고르는 이유 */
    public enum FeedbackReason {
        /** 종목이 안 맞음 */
        EXERCISE,
        /** 양이 많음 */
        TOO_MUCH,
        /** 양이 적음 */
        TOO_LITTLE,
        OTHER
    }
}
