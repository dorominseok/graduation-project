package com.fitness.backend.common.error;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.hibernate.query.SemanticException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 하나의 오류 구조({@link ErrorResponse})로 직렬화하는 단일 핸들러.
 *
 * <p>핸들러를 한 곳에 모으는 이유는, 예외 종류마다 응답 형태가 달라지면 클라이언트가
 * {@code code}로 분기할 수 없기 때문이다. 스프링이 기본 제공하는 오류 본문
 * ({@code timestamp/status/error/path})은 {@code code}가 없어 그대로 쓸 수 없다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final com.fitness.backend.auth.web.RefreshCookies refreshCookies;
    private final UserRepository userRepository;

    public GlobalExceptionHandler(com.fitness.backend.auth.web.RefreshCookies refreshCookies,
                                  UserRepository userRepository) {
        this.refreshCookies = refreshCookies;
        this.userRepository = userRepository;
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
     *
     * <p><b>컨트롤러에 {@code @Validated}를 붙이면 이 핸들러를 타지 않는다.</b> 그러면
     * 프록시가 {@code ConstraintViolationException}을 던져 500으로 떨어진다 — 잘못된
     * 쿼리 파라미터가 서버 오류로 보이므로, 파라미터 제약은 애노테이션만 달고
     * 스프링의 기본 검증에 맡긴다.
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

    /** 존재하지 않는 경로. {@code RESOURCE_NOT_FOUND}로 통일한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(), request, null);
    }

    /** 그 주소에 없는 메서드(예: 목록 주소에 {@code DELETE}). 쓸 수 있는 메서드를 {@code Allow}로 알려 준다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response =
                build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), request, null);
        String[] allowed = e.getSupportedMethods();
        if (allowed == null || allowed.length == 0) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.ALLOW, String.join(", ", allowed))
                .body(response.getBody());
    }

    /** 본문이 JSON이 아닌 경우(예: {@code text/plain}). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage(), request, null);
    }

    /**
     * 없는 정렬 기준({@code sort=nope}). 정렬 이름은 쿼리를 만들 때에야 엔티티와 맞춰 보므로 DB 계층 예외로 올라온다.
     * {@code sort}를 보낸 요청에서 "그런 속성이 없다"일 때만 400으로 보고, 나머지는 서버 잘못으로 둔다.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(
            InvalidDataAccessApiUsageException e, HttpServletRequest request) {
        if (request.getParameter("sort") != null && causedBy(e, SemanticException.class)) {
            return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request,
                    List.of(new ErrorResponse.FieldError("sort", "정렬할 수 없는 항목입니다.")));
        }
        return handleUnexpected(e, request);
    }

    /**
     * DB가 값을 거절한 경우 가운데 요청 탓인 둘을 가려낸다. 나머지는 서버 잘못(500)이다.
     *
     * <ul>
     *   <li>값이 DB가 담을 수 있는 범위 밖(예: 9억 년의 날짜) → 400
     *   <li>탈퇴한 사용자의 남은 액세스 토큰으로 쓰기 → 401. 액세스 토큰은 요청마다 DB를 보지 않아(명세 2.3)
     *       탈퇴 뒤에도 만료까지 통과하고, 쓰기는 사용자 외래키에서 걸린다. 다시 로그인하게 한다
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleRejectedByDatabase(
            DataIntegrityViolationException e, HttpServletRequest request) {
        if (causedBy(e, DataException.class)) {
            log.warn("DB가 담지 못하는 값: {} {}", request.getMethod(), request.getRequestURI());
            return build(ErrorCode.VALIDATION_ERROR, "값이 허용 범위를 벗어났습니다.", request, null);
        }
        if (causedBy(e, ConstraintViolationException.class) && callerNoLongerExists()) {
            return build(ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.defaultMessage(), request, null);
        }
        return handleUnexpected(e, request);
    }

    private boolean callerNoLongerExists() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof JwtProvider.AuthenticatedUser caller
                && !userRepository.existsById(caller.userId());
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
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
