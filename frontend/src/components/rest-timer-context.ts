import { createContext, use } from 'react'

/** 쉬고 나서 할 세트 — 휴식 띠를 누르면 여기로 간다 */
export interface RestOrigin {
  /** 그 종목을 펼친 기록 화면 주소 */
  path: string
  exerciseName: string
  /** 그 종목에서 몇 번째 본세트인지 */
  setNumber: number
}

export interface RestTimerValue {
  /** 쉬는 중이면 남은 초와 처음 잡은 초. 아니면 null */
  rest: { left: number; total: number } | null
  /** 휴식이 끝났다. 다음 세트를 하거나 닫을 때까지 "휴식 끝" 띠를 띄운다 */
  done: boolean
  /** 쉬는 중이거나 방금 끝난 휴식을 시작한 곳 */
  origin: RestOrigin | null
  start: (seconds: number, origin: RestOrigin) => void
  /** 남은 시간을 늘리거나 줄인다. 줄여도 1초 아래로는 내리지 않는다 */
  adjust: (deltaSeconds: number) => void
  skip: () => void
  /** "휴식 끝" 띠를 닫는다 */
  dismiss: () => void
}

export const RestTimerContext = createContext<RestTimerValue | null>(null)

export function useRestTimer(): RestTimerValue {
  const ctx = use(RestTimerContext)
  if (!ctx) throw new Error('useRestTimer는 RestTimerProvider 안에서만 쓸 수 있다')
  return ctx
}
