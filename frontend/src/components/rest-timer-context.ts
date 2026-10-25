import { createContext, use } from 'react'

export interface RestTimerValue {
  /** 쉬는 중이면 남은 초와 처음 잡은 초. 아니면 null */
  rest: { left: number; total: number } | null
  /** 방금 휴식이 끝났다. 잠깐 "휴식 끝" 띠를 띄운다 */
  done: boolean
  start: (seconds: number) => void
  /** 남은 시간을 늘리거나 줄인다. 줄여도 1초 아래로는 내리지 않는다 */
  adjust: (deltaSeconds: number) => void
  skip: () => void
}

export const RestTimerContext = createContext<RestTimerValue | null>(null)

export function useRestTimer(): RestTimerValue {
  const ctx = use(RestTimerContext)
  if (!ctx) throw new Error('useRestTimer는 RestTimerProvider 안에서만 쓸 수 있다')
  return ctx
}
