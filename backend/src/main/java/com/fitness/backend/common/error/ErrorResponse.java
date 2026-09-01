package com.fitness.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 모든 4xx·5xx 응답의 단일 구조. API 명세서 1.5.
 *
 * <p>성공 응답에는 이 envelope를 쓰지 않는다 — 리소스를 그대로 반환한다.
 *
 * @param timestamp 오류 발생 시각 (ISO 8601 오프셋 포함)
 * @param path      요청 경로
 * @param status    HTTP 상태 코드
 * @param code      애플리케이션 오류 코드. <b>클라이언트 분기는 이 값으로 한다</b>
 * @param message   사용자에게 노출 가능한 한국어 메시지
 * @param errors    필드 단위 검증 오류. 필드 오류가 아니면 키를 생략한다
 * @param traceId   서버 로그 상관 추적용 ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String message,
        List<FieldError> errors,
        String traceId) {

    /** @param field 오류가 난 필드명 @param reason 사유 */
    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, String traceId) {
        return new ErrorResponse(OffsetDateTime.now(), path, errorCode.status().value(),
                errorCode.name(), message, null, traceId);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, String traceId,
                                   List<FieldError> errors) {
        return new ErrorResponse(OffsetDateTime.now(), path, errorCode.status().value(),
                errorCode.name(), message, errors == null || errors.isEmpty() ? null : errors, traceId);
    }
}
