import { request } from './client'
import type { OneRmTrend, SessionIntensity } from './types'

/**
 * 추정 1RM 추이 (명세 7.1).
 *
 * 날짜를 생략하면 서버가 오늘을 포함해 최근 12주를 본다. 중량·횟수 종목이
 * 아니면 400이므로, 부르기 전에 `measureType`을 확인한다.
 */
export function getOneRmTrend(exerciseId: number): Promise<OneRmTrend> {
  return request<OneRmTrend>(`/stats/one-rm-trend?exerciseId=${exerciseId}`)
}

/** 세션 하나의 세트별 강도 (명세 7.2). 그날 추정 1RM도 여기서 온다. */
export function getSessionIntensity(sessionId: number): Promise<SessionIntensity> {
  return request<SessionIntensity>(`/stats/session-intensity/${sessionId}`)
}
