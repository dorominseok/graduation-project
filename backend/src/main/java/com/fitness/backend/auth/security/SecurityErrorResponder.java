package com.fitness.backend.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 시큐리티 필터 체인에서 발생한 오류를 명세 1.5의 구조로 직접 쓴다.
 *
 * <p>필터는 {@code @RestControllerAdvice}보다 앞단이라 {@code GlobalExceptionHandler}가
 * 잡지 못한다. 그대로 두면 인증 실패만 스프링 기본 형식으로 나가 {@code code}가 없고,
 * 클라이언트가 "토큰 만료라 refresh해야 하는지 / 로그인 화면으로 가야 하는지"를
 * 구분할 수 없게 된다.
 *
 * <p>Boot 4는 Jackson 3을 쓴다 — {@code ObjectMapper}가
 * {@code com.fasterxml.jackson.databind}가 아니라 {@code tools.jackson.databind}에 있다.
 * 애노테이션({@code @JsonInclude} 등)은 여전히 {@code com.fasterxml.jackson.annotation}이다.
 */
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
            throws IOException {
        write(request, response, code, code.defaultMessage());
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI(), MDC.get("traceId"));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
