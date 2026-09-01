package com.fitness.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 오류 코드. API 명세서 1.5의 표를 그대로 옮긴 것이다.
 *
 * <p>클라이언트의 분기는 HTTP 상태가 아니라 이 {@code code} 값으로 한다. 같은 상태에
 * 여러 사유가 있기 때문이다 — 예컨대 {@code 401}만으로는 "헤더가 없다"와 "토큰이
 * 만료됐다"를 구분할 수 없는데, 전자는 로그인 화면으로 보내야 하고 후자는
 * {@code /auth/refresh}를 시도해야 한다.
 *
 * <p>기본 메시지는 사용자에게 그대로 노출될 수 있으므로 한국어로 두고, 서버 내부
 * 사정을 드러내지 않는다.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    INVALID_MEASURE_INPUT(HttpStatus.BAD_REQUEST, "이 종목에 맞지 않는 입력입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "날짜 범위가 올바르지 않습니다."),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DRAFT_SESSION_EXISTS(HttpStatus.CONFLICT, "진행 중인 운동이 있습니다."),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 종료된 운동입니다."),

    EMPTY_SESSION(HttpStatus.UNPROCESSABLE_CONTENT, "기록된 세트가 없습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
