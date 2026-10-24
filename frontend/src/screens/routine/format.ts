import type { FeedbackRating, FeedbackReason, LastPerformance, Routine, RoutineItem } from '../../api'
import { describeSet } from '../full/setFormat'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** "10월 24일 (토)" */
export function formatRoutineDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${month}월 ${day}일 (${WEEKDAYS[new Date(year, month - 1, day).getDay()]})`
}

/** 반복 범위. 측정 유형으로 고정이다(설계서 9.1) — 시간 종목만 초로 적는다 */
export function repRangeLabel(item: Pick<RoutineItem, 'measureType' | 'targetMin' | 'targetMax'>): string {
  const unit = item.measureType === 'TIME' ? '초' : '회'
  return `${item.targetMin}~${item.targetMax}${unit}`
}

/** 근거 한 줄 — "가슴 · 주 4세트(권장 이하)". 숫자는 규칙이 쓴다(LOG-40) */
export function basisLine(item: RoutineItem): string {
  return `${item.muscleLabel} · 주 ${item.basisWeeklySets}세트(${item.basisVerdictLabel})`
}

/** 빼지 않은 항목 */
export function activeItems(routine: Routine): RoutineItem[] {
  return routine.items.filter((item) => !item.removed)
}

/** "가슴 · 등 · 앞허벅지" — 같은 부위가 두 종목으로 나뉘어도 한 번만 */
export function muscleLine(routine: Routine): string {
  return [...new Set(activeItems(routine).map((item) => item.muscleLabel))].join(' · ')
}

/** 지난 기록 한 줄 — 기록 화면의 "지난 기록"과 같은 말투. 본세트가 없으면 워밍업이라도 */
export function lastPerformanceLine(last: LastPerformance): string {
  const working = last.sets.filter((set) => !set.isWarmup)
  const shown = working.length > 0 ? working : last.sets
  return `${formatRoutineDay(last.performedOn)} · ${shown.map((set) => describeSet(last.measureType, set)).join(', ')}`
}

export const RATING_LABEL: Record<FeedbackRating, string> = {
  GOOD: '좋았어요',
  OKAY: '보통이에요',
  BAD: '별로였어요',
}

export const REASON_LABEL: Record<FeedbackReason, string> = {
  EXERCISE: '종목이 안 맞음',
  TOO_MUCH: '양이 많음',
  TOO_LITTLE: '양이 적음',
  OTHER: '기타',
}
