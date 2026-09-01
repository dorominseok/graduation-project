package com.fitness.backend.common.error;

/**
 * 애플리케이션이 의도적으로 발생시키는 오류. {@link ErrorCode}를 갖고 있어
 * 예외 핸들러가 상태 코드와 응답 본문을 만들 수 있다.
 *
 * <p>메시지를 따로 주지 않으면 {@link ErrorCode#defaultMessage()}를 쓴다. 상황별
 * 안내가 필요할 때만 덮어쓴다 — 예컨대 {@code DRAFT_SESSION_EXISTS}는 이어쓰기를
 * 유도하는 문구가 필요하다(명세 6.2).
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 자주 쓰는 404를 짧게 만든다. */
    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, what + "을(를) 찾을 수 없습니다.");
    }
}
