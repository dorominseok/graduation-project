package com.fitness.backend.routine.llm;

import com.fitness.backend.routine.llm.LlmCallException.Kind;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gemini {@code generateContent} 어댑터. LLM 실험기록 2.5·6.3.
 *
 * <p>키는 헤더({@code x-goog-api-key})로만 보낸다. URL·로그·예외 메시지에 넣지 않는다.
 * thinking이 없는 {@code gemini-3.5-flash-lite} 기준이라 {@code thinkingConfig}를 보내지 않는다.
 */
public class GeminiClient implements LlmClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient http;
    private final ObjectMapper json;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final int maxOutputTokens;

    public GeminiClient(ObjectMapper json, String model, String apiKey, Duration timeout, int maxOutputTokens) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.json = json;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxOutputTokens = maxOutputTokens;
    }

    public String model() {
        return model;
    }

    @Override
    public Response generate(Prompt prompt) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(model)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(prompt))))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmCallException(Kind.TIMEOUT, "응답 없음 " + timeout.toMillis() + "ms", e);
        } catch (IOException e) {
            throw new LlmCallException(Kind.NETWORK, e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException(Kind.NETWORK, "중단됨", e);
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        if (response.statusCode() != 200) {
            throw new LlmCallException(Kind.HTTP, "HTTP " + response.statusCode());
        }
        try {
            return parse(json.readTree(response.body()), latencyMs);
        } catch (JacksonException e) {
            throw new LlmCallException(Kind.EMPTY, "응답 본문이 JSON이 아님", e);
        }
    }

    private Map<String, Object> body(Prompt prompt) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("responseMimeType", "application/json");
        config.put("responseSchema", prompt.responseSchema());
        config.put("temperature", prompt.temperature());
        config.put("maxOutputTokens", maxOutputTokens);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.system()))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt.user())))));
        body.put("generationConfig", config);
        return body;
    }

    /**
     * 응답에서 글자만 꺼낸다. 막힘·잘림·빈 답은 예외 — 잘린 JSON을 검증기로 넘기면 L1 실패로 재요청하게
     * 되는데, 같은 설정이면 또 잘리므로 재요청할 이유가 없다.
     */
    static Response parse(JsonNode root, long latencyMs) {
        String blocked = root.path("promptFeedback").path("blockReason").asString("");
        if (!blocked.isEmpty()) {
            throw new LlmCallException(Kind.BLOCKED, "blockReason=" + blocked);
        }
        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw new LlmCallException(Kind.EMPTY, "candidates 없음");
        }
        String finish = candidate.path("finishReason").asString("");
        if ("MAX_TOKENS".equals(finish)) {
            throw new LlmCallException(Kind.TRUNCATED, "finishReason=MAX_TOKENS");
        }
        if (!finish.isEmpty() && !"STOP".equals(finish)) {
            throw new LlmCallException(Kind.BLOCKED, "finishReason=" + finish);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asString(""));
        }
        if (text.toString().isBlank()) {
            throw new LlmCallException(Kind.EMPTY, "빈 답");
        }
        JsonNode usage = root.path("usageMetadata");
        return new Response(text.toString().strip(), usage.path("promptTokenCount").asInt(0),
                usage.path("candidatesTokenCount").asInt(0), latencyMs);
    }
}
