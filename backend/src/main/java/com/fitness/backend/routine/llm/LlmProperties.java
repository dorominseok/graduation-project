package com.fitness.backend.routine.llm;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 루틴 LLM 설정(`app.llm.*`).
 *
 * <p>기본은 꺼짐이다. 꺼져 있으면 LLM을 부르지 않고 규칙이 고른다({@code LLM_DISABLED}) —
 * 개발·CI와, 키를 넣기 전의 운영이 그렇다. 키는 환경변수로만 받는다.
 *
 * @param maxRetries 검증 실패 시 다시 묻는 횟수. 저장 칸(llm_attempts)이 0~3이라 최대 2
 */
@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        boolean enabled,
        @NotBlank String model,
        @Min(1000) int timeoutMs,
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
        @Min(0) @Max(2) int maxRetries,
        @Min(64) int maxOutputTokens,
        String apiKey) {

    /** 키가 로그·오류 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "LlmProperties[enabled=" + enabled + ", model=" + model + ", timeoutMs=" + timeoutMs
                + ", temperature=" + temperature + ", maxRetries=" + maxRetries + ", maxOutputTokens="
                + maxOutputTokens + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "(없음)" : "(설정됨)") + "]";
    }
}
