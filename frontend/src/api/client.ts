/**
 * API 클라이언트. 모든 백엔드 호출이 여기를 지난다.
 *
 * 여기 한 곳에 모아두는 이유는 **토큰 부착·만료 시 재발급·재시도**가 화면마다
 * 흩어지면 새 화면을 붙일 때 같은 실수를 반복하기 때문이다. 운동 중에는 세트마다
 * 요청이 나가므로 만료가 걸리는 지점도 잦다(명세 2.2).
 *
 * 개발 중에는 Vite 프록시가 `/api`를 `localhost:8080`으로 넘긴다. 같은 오리진이라
 * 리프레시 쿠키(`HttpOnly`, `SameSite=Strict`)가 CORS 없이 그대로 실린다.
 */

import { ApiError, ErrorCodes, toApiError } from './errors'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'
import type { RefreshResponse } from './types'

const BASE_URL = '/api/v1'

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  /** JSON으로 직렬화해 보낼 본문. 없으면 Content-Type도 붙이지 않는다. */
  body?: unknown
  /**
   * 인증 헤더를 붙이지 않고, 401을 받아도 재발급을 시도하지 않는다.
   * 가입·로그인·재발급처럼 액세스 토큰 없이 부르는 경로에 쓴다.
   */
  anonymous?: boolean
  signal?: AbortSignal
}

/**
 * 재발급이 끝내 실패했을 때 불린다. AuthProvider가 등록해 로그인 화면으로 보낸다.
 *
 * API 계층이 리액트를 모르게 두려고 콜백으로 뺐다.
 */
type AuthExpiredListener = () => void

let authExpiredListener: AuthExpiredListener | null = null

export function setAuthExpiredListener(listener: AuthExpiredListener | null): void {
  authExpiredListener = listener
}

/**
 * 진행 중인 재발급 요청. **한 번에 하나만** 보낸다 (명세 4.3 클라이언트 요구사항).
 *
 * 액세스가 만료되는 순간 여러 요청이 함께 401을 받는데, 각자 재발급을 쏘면 회전
 * 때문에 뒤의 것들이 이미 폐기된 토큰을 들고 가게 된다. 서버의 30초 유예 창
 * (LOG-16)이 그 상황을 구제하지만, 애초에 하나로 묶는 게 설계가 의도한 동작이다.
 */
let inflightRefresh: Promise<string> | null = null

/** 재발급 실패로 보고 세션을 접을 오류 코드. */
const SESSION_DEAD = new Set<string>([ErrorCodes.TOKEN_INVALID, ErrorCodes.AUTHENTICATION_REQUIRED])

/** 액세스 토큰을 새로 받는다. 동시에 불려도 요청은 하나만 나간다. */
export function refreshAccessToken(): Promise<string> {
  if (!inflightRefresh) {
    inflightRefresh = performRefresh().finally(() => {
      inflightRefresh = null
    })
  }
  return inflightRefresh
}

async function performRefresh(): Promise<string> {
  try {
    const res = await request<RefreshResponse>('/auth/refresh', {
      method: 'POST',
      anonymous: true,
    })
    setAccessToken(res.accessToken)
    return res.accessToken
  } catch (e) {
    // 쿠키가 없거나 죽었다. 서버가 쿠키까지 만료시켜 보내므로 여기서는 상태만 정리한다.
    clearAccessToken()
    if (e instanceof ApiError && SESSION_DEAD.has(e.code)) {
      authExpiredListener?.()
    }
    throw e
  }
}

/** 401을 받았을 때 재발급을 시도해볼 만한 코드인지. */
function isRetryableAuthError(error: ApiError): boolean {
  // TOKEN_EXPIRED  — 만료. 재발급이 정확히 이 경우를 위한 것이다.
  // AUTHENTICATION_REQUIRED — 헤더를 못 붙였다. 새로고침 직후 메모리가 빈 상태가 이에 해당한다.
  //
  // TOKEN_INVALID는 뺀다. 서명이 안 맞는 토큰을 들고 있었다는 뜻이라 재발급으로
  // 수습할 상황이 아니고, 반복하면 서버의 재사용 감지를 자극한다.
  return (
    error.status === 401 &&
    (error.code === ErrorCodes.TOKEN_EXPIRED || error.code === ErrorCodes.AUTHENTICATION_REQUIRED)
  )
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, anonymous = false, signal } = options

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = {}
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    const token = anonymous ? null : getAccessToken()
    if (token) headers.Authorization = `Bearer ${token}`

    try {
      return await fetch(`${BASE_URL}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        // 리프레시 쿠키를 싣기 위해. 프록시 덕에 같은 오리진이라 기본값과 같지만
        // 의도를 드러내려고 명시한다.
        credentials: 'same-origin',
        signal,
      })
    } catch (e) {
      // 요청을 취소한 경우는 오류가 아니라 흐름이므로 그대로 올린다.
      if (e instanceof DOMException && e.name === 'AbortError') throw e
      throw new ApiError(0, ErrorCodes.NETWORK_ERROR, '서버에 연결하지 못했어요')
    }
  }

  let response = await send()

  if (!response.ok) {
    const error = await toApiError(response)

    if (anonymous || !isRetryableAuthError(error)) throw error

    // 재발급은 한 번만 시도한다. 그래도 실패하면 세션이 끝난 것이다.
    await refreshAccessToken()
    response = await send()

    if (!response.ok) throw await toApiError(response)
  }

  return parseBody<T>(response)
}

/** 204와 빈 본문을 정상으로 처리한다. 로그아웃·탈퇴가 204로 온다(명세 4.4·4.7). */
async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
