package com.fitness.backend.routine.llm;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 루틴 LLM 조립. {@code app.llm.enabled=true}일 때만 만든다 — 없으면 루틴 서비스가 규칙으로 고른다.
 *
 * <p>켜 놓고 키가 없으면 기동에서 멈춘다. 켜진 줄 알았는데 매번 조용히 규칙으로 가는 것보다 낫다.
 */
@Configuration(proxyBeanMethods = false)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
    LlmRoutineComposer llmRoutineComposer(LlmProperties properties, ObjectMapper json) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("app.llm.enabled=true인데 GEMINI_API_KEY가 없다");
        }
        GeminiClient client = new GeminiClient(json, properties.model(), properties.apiKey(),
                Duration.ofMillis(properties.timeoutMs()), properties.maxOutputTokens());
        return new LlmRoutineComposer(client, json, properties.maxRetries(), properties.temperature());
    }
}
