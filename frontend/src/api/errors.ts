/**
 * 오류 응답 파싱. API 명세서 1.5의 단일 envelope를 따른다.
 *
 * 화면 분기는 `message`가 아니라 **`code`로** 한다 — 문구는 바뀔 수 있지만
 * 코드는 계약이기 때문이다(명세 1.5).
 */

/** 필드 단위 검증 오류. 400 VALIDATION_ERROR에만 딸려온다. */
export interface FieldError {
  field: string
  reason: string
}

/** 서버가 내려주는 오류 코드 중 클라이언트가 분기에 쓰는 것들. */
export const ErrorCodes = {
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  AUTHENTICATION_REQUIRED: 'AUTHENTICATION_REQUIRED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  TOKEN_EXPIRED: 'TOKEN_EXPIRED',
  TOKEN_INVALID: 'TOKEN_INVALID',
  EMAIL_ALREADY_EXISTS: 'EMAIL_ALREADY_EXISTS',
  /** 통신 자체가 실패한 경우. 서버가 준 코드가 아니라 클라이언트가 붙인다. */
  NETWORK_ERROR: 'NETWORK_ERROR',
} as const

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes] | (string & {})

export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode
  readonly errors: FieldError[]

  constructor(status: number, code: ErrorCode, message: string, errors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
  }

  /** 필드 오류를 `{ [field]: reason }` 형태로. 폼에 그대로 꽂아 쓰려고. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.reason]))
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError
}

/**
 * 응답 본문에서 ApiError를 만든다.
 *
 * 서버를 거치지 않고 프록시나 네트워크 단에서 끊기면 본문이 JSON이 아닐 수 있어,
 * 파싱 실패를 정상 경로로 취급하고 상태 코드만으로 오류를 구성한다.
 */
export async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json()
    return new ApiError(
      typeof body.status === 'number' ? body.status : response.status,
      typeof body.code === 'string' ? body.code : 'UNKNOWN',
      typeof body.message === 'string' ? body.message : '요청을 처리하지 못했어요',
      Array.isArray(body.errors) ? body.errors : [],
    )
  } catch {
    return new ApiError(response.status, 'UNKNOWN', '요청을 처리하지 못했어요')
  }
}
