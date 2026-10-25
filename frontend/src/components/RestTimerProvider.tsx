import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { RestTimerContext } from './rest-timer-context'
import type { RestOrigin } from './rest-timer-context'

interface Running {
  /** 끝나는 시각(ms). 남은 시간은 볼 때마다 지금과의 차로 계산한다 */
  endsAt: number
  total: number
  /** 시작한 사용자. 로그아웃하고 다른 계정이 들어오면 보이지 않는다 */
  userId: number | null
  origin: RestOrigin
}

const STORAGE_KEY = 'rest-timer'
/** "휴식 끝" 띠를 띄워두는 시간 */
const DONE_MS = 3000

function load(): Running | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const saved = JSON.parse(raw) as Running
    return saved.endsAt > Date.now() ? saved : null
  } catch {
    return null
  }
}

function save(running: Running | null) {
  try {
    if (running) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(running))
    else sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 저장하지 못해도 이 화면에서는 타이머가 돈다. 새로고침하면 사라질 뿐이다
  }
}

/**
 * 휴식 타이머. 기록 화면 밖에 두어 종목 상세를 보러 가도 계속 돈다.
 *
 * <p>남은 초를 1초마다 빼지 않고 끝나는 시각을 들고 있다. 휴대폰은 화면이 꺼지거나
 * 다른 앱으로 가면 1초 반복을 멈추는데, 돌아와서 시각으로 다시 계산하니 어긋나지 않는다.
 * 새로고침에도 이어지게 이 탭에만 끝나는 시각을 남긴다.
 */
export function RestTimerProvider({ userId, children }: { userId: number | null; children: ReactNode }) {
  const [running, setRunning] = useState<Running | null>(load)
  const [now, setNow] = useState(() => Date.now())
  const [done, setDone] = useState(false)
  // "휴식 끝" 띠를 눌러도 돌아갈 수 있게 끝난 휴식의 출발점을 잠깐 남긴다
  const [doneOrigin, setDoneOrigin] = useState<RestOrigin | null>(null)

  useEffect(() => {
    save(running)
    if (!running) return
    const tick = () => {
      const current = Date.now()
      if (current >= running.endsAt) {
        setRunning(null)
        setDone(running.userId === userId)
        setDoneOrigin(running.origin)
      } else {
        setNow(current)
      }
    }
    const timer = window.setInterval(tick, 1000)
    // 화면을 다시 켰을 때 1초를 기다리지 않고 바로 맞춘다
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [running, userId])

  useEffect(() => {
    if (!done) return
    const timer = window.setTimeout(() => setDone(false), DONE_MS)
    return () => window.clearTimeout(timer)
  }, [done])

  const start = useCallback(
    (seconds: number, origin: RestOrigin) => {
      const current = Date.now()
      setNow(current)
      setDone(false)
      setRunning({ endsAt: current + seconds * 1000, total: seconds, userId, origin })
    },
    [userId],
  )

  const adjust = useCallback((deltaSeconds: number) => {
    const current = Date.now()
    setNow(current)
    setRunning((prev) => {
      if (!prev) return prev
      const left = Math.max(1000, prev.endsAt - current + deltaSeconds * 1000)
      return { ...prev, endsAt: current + left, total: Math.max(prev.total, Math.ceil(left / 1000)) }
    })
  }, [])

  const skip = useCallback(() => setRunning(null), [])

  const mine = running !== null && running.userId === userId ? running : null
  const left = mine ? Math.max(1, Math.ceil((mine.endsAt - now) / 1000)) : 0
  // 이전 판이 남긴 저장값에는 출발점이 없다
  const origin = (mine ? mine.origin : done ? doneOrigin : null) ?? null
  const value = useMemo(
    () => ({ rest: mine ? { left, total: mine.total } : null, done, origin, start, adjust, skip }),
    [mine, left, done, origin, start, adjust, skip],
  )

  return <RestTimerContext value={value}>{children}</RestTimerContext>
}
