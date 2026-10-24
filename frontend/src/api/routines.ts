/**
 * 오늘의 루틴 (명세 11장, LOG-40).
 *
 * 하루에 루틴은 하나다. 다시 만들기가 없어 `createToday`를 두 번 불러도 같은 루틴이 온다 —
 * 마음에 안 들면 후보정(교체·세트·빼기)으로 고친다.
 */

import { request } from './client'
import type {
  Routine,
  RoutineAlternative,
  RoutineFeedbackRequest,
  UpdateRoutineItemRequest,
} from './types'

/** 오늘 루틴을 만든다. 이미 있으면 그 루틴이 온다(201·200 모두 본문은 같다). */
export function createToday(): Promise<Routine> {
  return request<Routine>('/routines/today', { method: 'POST' })
}

/** 오늘 루틴. 아직 받지 않았으면 null. */
export async function getToday(): Promise<Routine | null> {
  return (await request<Routine | undefined>('/routines/today')) ?? null
}

/** 만족도를 물을 지난 루틴. 루틴 부위를 체크했고 아직 답하지 않은 가장 최근 하나. */
export async function getFeedbackPending(): Promise<Routine | null> {
  return (await request<Routine | undefined>('/routines/feedback-pending')) ?? null
}

export function get(routineId: number): Promise<Routine> {
  return request<Routine>(`/routines/${routineId}`)
}

/** 같은 부위 종목으로만 바꿀 수 있다. 지난 루틴은 고칠 수 없다. */
export function updateItem(routineId: number, itemId: number, body: UpdateRoutineItemRequest): Promise<Routine> {
  return request<Routine>(`/routines/${routineId}/items/${itemId}`, { method: 'PATCH', body })
}

/** 빼기. 지우지 않고 `removed`로 남는다. */
export function removeItem(routineId: number, itemId: number): Promise<Routine> {
  return request<Routine>(`/routines/${routineId}/items/${itemId}`, { method: 'DELETE' })
}

export function getAlternatives(routineId: number, itemId: number): Promise<RoutineAlternative[]> {
  return request<RoutineAlternative[]>(`/routines/${routineId}/items/${itemId}/alternatives`)
}

/** 루틴당 한 번. 이미 답했거나 건너뛰었으면 409(ROUTINE_FEEDBACK_EXISTS). */
export function sendFeedback(routineId: number, body: RoutineFeedbackRequest): Promise<Routine> {
  return request<Routine>(`/routines/${routineId}/feedback`, { method: 'PUT', body })
}
