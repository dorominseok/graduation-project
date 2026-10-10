import type { MeasureType } from '../../api'

/** 중량은 2.5처럼 소수가 나오지만 60.0으로 적지는 않는다 */
export function trimNumber(value: number): string {
  return String(Math.round(value * 10) / 10)
}

/**
 * 세트 한 줄을 "60kg×10" 꼴로. 기록 화면의 카드 요약·직전 기록 줄과 그룹의 멤버 기록이
 * 같은 말투를 쓴다.
 */
export function describeSet(
  measureType: MeasureType,
  set: { weightKg: number | null; reps: number | null; durationSec: number | null },
): string {
  if (measureType === 'TIME') return `${set.durationSec ?? 0}초`
  if (measureType === 'BODYWEIGHT_REPS') return `${set.reps ?? 0}회`
  const sign = measureType === 'WEIGHTED_BODYWEIGHT' ? '+' : ''
  return `${sign}${trimNumber(set.weightKg ?? 0)}kg×${set.reps ?? 0}`
}
