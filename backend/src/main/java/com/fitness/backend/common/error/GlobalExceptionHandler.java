package com.fitness.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 명세 1.5의 단일 구조로 직렬화하는 단일 핸들러.
 *
 * <p>핸들러를 한 곳에 모으는 이유는, 예외 종류마다 응답 형태가 달라지면 클라이언트가
 * {@code code}로 분기할 수 없기 때문이다. 스프링이 기본 제공하는 오류 본문
 * ({@code timestamp/status/error/path})은 {@code code}가 없어 그대로 쓸 수 없다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final com.fitness.backend.auth.web.RefreshCookies refreshCookies;

    public GlobalExceptionHandler(com.fitness.backend.auth.web.RefreshCookies refreshCookies) {
        this.refreshCookies = refreshCookies;
    }

    /** 애플리케이션이 의도적으로 던진 오류. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
        ErrorCode code = e.errorCode();
        if (code.status().is5xxServerError()) {
            log.error("[{}] {}", code, e.getMessage(), e);
        } else {
            log.debug("[{}] {}", code, e.getMessage());
        }
        return build(code, e.getMessage(), request, null);
    }

    /**
     * 재발급 실패 — 오류 응답과 함께 리프레시 쿠키를 지운다.
     *
     * <p>쿠키를 남겨두면 클라이언트가 죽은 토큰으로 재시도를 반복하고, 그 재시도가
     * 재사용 감지에 걸려 상황을 악화시킨다.
     */
    @ExceptionHandler(com.fitness.backend.auth.web.AuthController.CookieClearingException.class)
    public ResponseEntity<ErrorResponse> handleRefreshFailure(
            com.fitness.backend.auth.web.AuthController.CookieClearingException e,
            HttpServletRequest request) {
        ApiException cause = e.apiException();
        ErrorResponse body = ErrorResponse.of(cause.errorCode(), cause.getMessage(),
                request.getRequestURI(), MDC.get("traceId"));
        return ResponseEntity.status(cause.errorCode().status())
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .body(body);
    }

    /** {@code @Valid} 본문 검증 실패 — 필드별 사유를 {@code errors}에 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request, errors);
    }

    /**
     * 쿼리 파라미터·경로 변수 검증 실패.
     *
     * <p>Spring Framework 7에서 {@code getAllValidationResults()}가
     * {@code getParameterValidationResults()}로 바뀌었다. Boot 3 예제를 그대로
     * 옮기면 컴파일되지 않는다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(
            HandlerMethodValidationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new ErrorResponse.FieldError(
                                r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request, errors);
    }

    /** 필수 쿼리 파라미터 누락 (예: 캘린더의 {@code year}·{@code month}). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request,
                List.of(new ErrorResponse.FieldError(e.getParameterName(), "필수 항목입니다.")));
    }

    /** 타입 불일치 (예: {@code exerciseId=abc}). 어떤 값이 잘못됐는지는 알려주되 내부 타입명은 노출하지 않는다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request,
                List.of(new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다.")));
    }

    /** 본문이 없거나 JSON이 깨진 경우. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "요청 본문을 읽을 수 없습니다.", request, null);
    }

    /** 존재하지 않는 경로. 명세의 {@code RESOURCE_NOT_FOUND}로 통일한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(), request, null);
    }

    /**
     * 그 밖의 모든 예외. <b>원인은 로그에만 남기고 응답에는 담지 않는다</b> —
     * 스택 트레이스나 SQL이 클라이언트로 새어 나가면 안 된다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
                                                HttpServletRequest request,
                                                List<ErrorResponse.FieldError> errors) {
        ErrorResponse body = ErrorResponse.of(
                code, message, request.getRequestURI(), MDC.get("traceId"), errors);
        return ResponseEntity.status(code.status()).body(body);
    }
}
