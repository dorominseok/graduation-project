import type { MeasureType } from '../../api'

/** 중량은 2.5처럼 소수가 나오지만 60.0으로 적지는 않는다 */
export function trimNumber(value: number): string {
  return String(Math.round(value * 10) / 10)
}

/**
 * 세트 한 줄을 "60kg×10" 꼴로. 맨몸은 횟수만, 시간 종목은 초만 적는다.
 */
export function describeSet(
  measureType: MeasureType,
  set: { weightKg: number | null; reps: number | null; durationSec: number | null; targetReps?: number | null },
): string {
  if (measureType === 'TIME') return `${set.durationSec ?? 0}초`
  // 실패 세트는 해낸 횟수/목표 — "9/10"
  const reps = `${set.reps ?? 0}${set.targetReps != null ? `/${set.targetReps}` : ''}`
  if (measureType === 'BODYWEIGHT_REPS') return `${reps}회`
  const sign = measureType === 'WEIGHTED_BODYWEIGHT' ? '+' : ''
  return `${sign}${trimNumber(set.weightKg ?? 0)}kg×${reps}`
}
