import { request } from './client'
import type {
  BodyPart,
  BrowseCategory,
  CategoryCount,
  Equipment,
  Exercise,
  GroupCount,
  LastPerformance,
  MeasureType,
  Page,
} from './types'

/** 종목 목록 조회 조건 (명세 5.2). 지정하지 않은 항목은 조건을 걸지 않는다. */
export interface ExerciseQuery {
  /** 한글·영문 부분 일치 */
  q?: string
  bodyPart?: BodyPart
  /** 탐색 분류 12종 (LOG-24). 부위 그리드에서 고른 값 */
  category?: BrowseCategory
  /** 계열. 같은 동작의 변형만 본다 */
  group?: string
  equipment?: Equipment
  measureType?: MeasureType
  /** 즐겨찾기한 종목만. 비인증 요청에서는 무시된다 */
  favorite?: boolean
  page?: number
  size?: number
}

function toQueryString(query: ExerciseQuery): string {
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
 * 종목 목록 (명세 5.2).
 *
 * 토큰이 없어도 조회된다. 다만 그때는 각 항목에 `isFavorite`가 담기지 않는다.
 */
export function search(query: ExerciseQuery = {}): Promise<Page<Exercise>> {
  return request<Page<Exercise>>(`/exercises${toQueryString(query)}`)
}

/**
 * 부위 그리드 (LOG-24). 종목 선택 1단계.
 *
 * 109종을 한 목록에 늘어놓으면 검색 말고는 찾을 길이 없어서, 부위에서 계열로,
 * 계열에서 변형으로 좁혀 들어간다.
 */
export function categories(): Promise<CategoryCount[]> {
  return request<CategoryCount[]>('/exercises/categories')
}

/** 한 분류의 계열 목록 (LOG-24). 종목 선택 2단계. */
export function groups(category: BrowseCategory): Promise<GroupCount[]> {
  return request<GroupCount[]>(`/exercises/groups?category=${category}`)
}

/** 종목 상세 (명세 5.3). */
export function get(exerciseId: number): Promise<Exercise> {
  return request<Exercise>(`/exercises/${exerciseId}`)
}

/** 즐겨찾기 등록 (명세 5.4). 이미 등록돼 있어도 오류가 아니다. */
export function addFavorite(exerciseId: number): Promise<void> {
  return request<void>(`/users/me/favorite-exercises/${exerciseId}`, { method: 'PUT' })
}

/** 즐겨찾기 해제 (명세 5.4). */
export function removeFavorite(exerciseId: number): Promise<void> {
  return request<void>(`/users/me/favorite-exercises/${exerciseId}`, { method: 'DELETE' })
}

/**
 * 직전 수행 기록 (명세 5.5).
 *
 * 처음 하는 종목이면 서버가 204를 주고 여기서는 `null`이 된다 — 오류가 아니라
 * "채울 기록이 없다"는 정상 상태이므로 호출부는 빈 행으로 시작하면 된다.
 */
export async function getLastPerformance(exerciseId: number): Promise<LastPerformance | null> {
  const result = await request<LastPerformance | null>(`/exercises/${exerciseId}/last-performance`)
  return result ?? null
}
