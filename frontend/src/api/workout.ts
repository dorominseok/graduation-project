import { request } from './client'
import type {
  CalendarResponse,
  CreateSessionRequest,
  CreateSetRequest,
  Page,
  SessionStatus,
  SessionSummary,
  UpdateSessionRequest,
  UpdateSetRequest,
  WorkoutSession,
  WorkoutSet,
} from './types'

/** 히스토리 조회 조건 (명세 6.6). 날짜를 생략하면 서버가 최근 30일을 본다. */
export interface HistoryQuery {
  from?: string
  to?: string
  status?: SessionStatus
  /** 그 종목의 세트가 하나라도 있는 세션만. 세션 단위 필터다 */
  exerciseId?: number
  page?: number
  size?: number
}

function toQueryString(query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') {
      params.set(key, String(value))
    }
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

/**
 * 세션 생성 (명세 6.2).
 *
 * 첫 세트를 완료 체크하는 순간 부른다 — 화면에 들어오자마자 만들면 아무것도
 * 기록하지 않고 나간 빈 세션이 남는다.
 *
 * 진행 중인 세션이 있으면 409(`DRAFT_SESSION_EXISTS`)다. 호출부는 그때
 * `getCurrent()`로 이어쓰기를 안내한다.
 */
export function createSession(body: CreateSessionRequest): Promise<WorkoutSession> {
  return request<WorkoutSession>('/workout-sessions', { method: 'POST', body })
}

/**
 * 세트 저장 (명세 6.4).
 *
 * `clientSetId`가 멱등 키라 같은 값으로 다시 보내도 세트가 두 번 쌓이지 않는다.
 * 완료 체크 시점에 `crypto.randomUUID()`로 만들어 넣는다.
 */
export function addSet(sessionId: number, body: CreateSetRequest): Promise<WorkoutSet> {
  return request<WorkoutSet>(`/workout-sessions/${sessionId}/sets`, { method: 'POST', body })
}

/** 세션 종료 (명세 6.3). 세트가 하나도 없으면 422다. */
export function complete(sessionId: number): Promise<WorkoutSession> {
  return request<WorkoutSession>(`/workout-sessions/${sessionId}/complete`, { method: 'POST', body: {} })
}

/** 세션 상세 (명세 6.5). 세트가 종목별로 묶여 수행 순서대로 온다. */
export function getSession(sessionId: number): Promise<WorkoutSession> {
  return request<WorkoutSession>(`/workout-sessions/${sessionId}`)
}

/**
 * 진행 중인 세션 (명세 6.7).
 *
 * 없으면 서버가 204를 주고 여기서는 `null`이다 — 앱을 다시 열었을 때
 * 이어쓸 세션이 있는지 판단하는 데 쓴다.
 */
export async function getCurrent(): Promise<WorkoutSession | null> {
  const result = await request<WorkoutSession | null>('/workout-sessions/current')
  return result ?? null
}

/** 히스토리 목록 (명세 6.6). */
export function getHistory(query: HistoryQuery = {}): Promise<Page<SessionSummary>> {
  return request<Page<SessionSummary>>(`/workout-sessions${toQueryString(query)}`)
}

/** 캘린더 월별 요약 (명세 6.8). `month`는 1~12다. */
export function getCalendar(year: number, month: number): Promise<CalendarResponse> {
  return request<CalendarResponse>(`/workout-sessions/calendar?year=${year}&month=${month}`)
}

/**
 * 세션 수정 (명세 6.9).
 *
 * 값을 비울 때는 메모에 빈 문자열, 시간 보정에 `0`을 보낸다(LOG-23).
 */
export function updateSession(sessionId: number, body: UpdateSessionRequest): Promise<WorkoutSession> {
  return request<WorkoutSession>(`/workout-sessions/${sessionId}`, { method: 'PATCH', body })
}

/** 세션 삭제 (명세 6.10). 세트도 함께 지워진다. */
export function deleteSession(sessionId: number): Promise<void> {
  return request<void>(`/workout-sessions/${sessionId}`, { method: 'DELETE' })
}

/** 세트 수정 (명세 6.11). */
export function updateSet(
  sessionId: number,
  setId: number,
  body: UpdateSetRequest,
): Promise<WorkoutSet> {
  return request<WorkoutSet>(`/workout-sessions/${sessionId}/sets/${setId}`, { method: 'PATCH', body })
}

/** 세트 삭제 (명세 6.12). 마지막 세트를 지워도 세션은 남는다. */
export function deleteSet(sessionId: number, setId: number): Promise<void> {
  return request<void>(`/workout-sessions/${sessionId}/sets/${setId}`, { method: 'DELETE' })
}

/**
 * 종목 단위 삭제 (LOG-22).
 *
 * 세트를 하나씩 지우는 것보다 이쪽을 쓴다 — 요청이 한 번이라 중간에 끊겨
 * 일부만 지워지는 상태가 생기지 않는다.
 */
export function deleteExercise(sessionId: number, exerciseId: number): Promise<void> {
  return request<void>(`/workout-sessions/${sessionId}/exercises/${exerciseId}`, { method: 'DELETE' })
}
