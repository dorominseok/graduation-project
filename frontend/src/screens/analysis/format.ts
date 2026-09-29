import type { BalanceVerdict, SummaryBadge, VolumeVerdict } from '../../api'

/**
 * 판정을 어떤 색 계열로 칠할지. 라벨 문구는 서버가 주고, 여기서는 색만 고른다.
 *
 * <p>`neutral`은 판정이 없다는 뜻이다. 하위가 둘인 상위 부위(어깨·팔·하체)의
 * 막대를 초록으로 칠하면 라벨을 떼어도 "최적"이라고 말하는 셈이 된다.
 */
export type Tone = 'danger' | 'warn' | 'good' | 'neutral'

export const VERDICT_TONE: Record<VolumeVerdict, Tone> = {
  INSUFFICIENT: 'danger',
  BELOW_RECOMMENDED: 'warn',
  OPTIMAL: 'good',
  EXCESSIVE: 'danger',
}

/** 배지는 하위 중 가장 나쁜 상태를 따른다. 부족·과다가 섞이면 둘 다 위험 쪽이다 */
export const BADGE_TONE: Record<SummaryBadge, Tone> = {
  ALL_OPTIMAL: 'good',
  PARTIAL_BELOW: 'warn',
  PARTIAL_INSUFFICIENT: 'danger',
  PARTIAL_EXCESSIVE: 'danger',
  MIXED: 'danger',
}

export const BALANCE_TONE: Record<BalanceVerdict, Tone> = {
  BALANCED: 'good',
  IMBALANCED: 'danger',
  INSUFFICIENT_DATA: 'neutral',
}

/**
 * 막대의 오른쪽 끝이 뜻하는 주당 세트.
 *
 * <p>28로 두면 기준선 4·10·20이 각각 14%·36%·71% 자리에 온다 — 목업의 눈금 위치
 * 그대로다. 20을 넘는 과다 구간도 막대 안에서 보이도록 여유를 남긴 값이다.
 */
export const BAR_SCALE = 28
export const BAR_TICKS = [4, 10, 20] as const

export function barPercent(weeklySets: number): number {
  return Math.min(weeklySets, BAR_SCALE) / BAR_SCALE * 100
}

/** "2026-09-03" → "9월 3일" */
export function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}월 ${day}일`
}

/** "2026-09-03" → "9/3". 차트 축처럼 자리가 좁은 곳에 쓴다 */
export function formatShortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}/${day}`
}
