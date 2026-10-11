import { request } from './client'
import type {
  CalendarResponse,
  CreateSetRequest,
  OpenDayRequest,
  Page,
  SessionSummary,
  UpdateSessionRequest,
  UpdateSetRequest,
  WorkoutSession,
  WorkoutSet,
} from './types'

/** 히스토리 조회 조건 (명세 6.6). 날짜를 생략하면 서버가 최근 30일을 본다. 운동한 날만 온다. */
export interface HistoryQuery {
  from?: string
  to?: string
  /** 그 종목의 세트가 하나라도 있는 날만. 날 단위 필터다 */
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
 * 그날 기록 열기 (명세 6.2, LOG-39). 없으면 만들고 있으면 그 기록을 준다 — 하루에 기록은 하나다.
 *
 * 날짜를 생략하면 오늘이다. 어젯밤 운동이 자정을 넘겨 이어지는 중이면 서버가 어제 기록을 준다.
 * 두 번 불러도 같은 기록이 온다.
 */
export function openDay(body: OpenDayRequest = {}): Promise<WorkoutSession> {
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

/** 하루 기록 상세 (명세 6.5). 세트가 종목별로 묶여 수행 순서대로 온다. */
export function getSession(sessionId: number): Promise<WorkoutSession> {
  return request<WorkoutSession>(`/workout-sessions/${sessionId}`)
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
 * 하루 기록 수정 (명세 6.9). 기록이 이미 있는 날짜로는 옮길 수 없다(409 DAY_ALREADY_RECORDED).
 *
 * 값을 비울 때는 메모에 빈 문자열, 시간 보정에 `0`을 보낸다(LOG-23).
 */
export function updateSession(sessionId: number, body: UpdateSessionRequest): Promise<WorkoutSession> {
  return request<WorkoutSession>(`/workout-sessions/${sessionId}`, { method: 'PATCH', body })
}

/** 하루 기록 삭제 (명세 6.10). 세트도 함께 지워진다. */
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

/** 세트 삭제 (명세 6.12). 마지막 세트를 지워도 기록은 남는다. */
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
