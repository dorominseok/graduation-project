import { request } from './client'
import type { Balance, MuscleVolume, WeeklyVolume } from './types'

/**
 * 부위별 볼륨·부족 판정 (명세 8.2).
 *
 * 기간은 서버 설정값(최근 4주, 오늘 포함)을 쓴다. 화면이 기간을 정하면 판정
 * 기준이 화면마다 달라질 수 있어 넘기지 않는다.
 */
export function getMuscleVolume(): Promise<MuscleVolume> {
  return request<MuscleVolume>('/analysis/muscle-volume')
}

/** 밀기/당기기 · 상체/하체 균형 (명세 8.3). 볼륨과 같은 기간을 본다. */
export function getBalance(): Promise<Balance> {
  return request<Balance>('/analysis/balance')
}

/** 주차별 부위 세트 (LOG-30). 월~일 주 단위, 기본 8주(이번 주 포함). */
export function getWeeklyVolume(): Promise<WeeklyVolume> {
  return request<WeeklyVolume>('/analysis/weekly-volume')
}
