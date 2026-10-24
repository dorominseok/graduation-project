import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MonthCalendar, ScreenHeader, useToast } from '../../components'
import { isApiError, workoutApi } from '../../api'
import type { CalendarResponse } from '../../api'
import { paths } from '../../app/paths'
import styles from './history.module.css'

/**
 * 기록 탭 — 월간 달력.
 *
 * <p>날짜를 누르면 그날 기록으로 들어가고, 기록이 없는 날이면 그 날짜로 사후 입력을 시작한다.
 * 목록을 함께 두지 않는다 — 달력이 반으로 줄어 한 달이 한눈에 안 들어온다.
 */
export function HistoryScreen() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [calendar, setCalendar] = useState<CalendarResponse | null>(null)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  // 달이 바뀌면 그 달의 기록 있는 날짜를 다시 읽는다.
  // 달을 빠르게 넘기면 응답이 순서대로 오지 않는다. 지나간 달의 응답은 버린다.
  useEffect(() => {
    let stale = false
    const run = async () => {
      try {
        const next = await workoutApi.getCalendar(year, month)
        if (!stale) setCalendar(next)
      } catch (err) {
        if (!stale) fail(err)
      }
    }
    void run()
    return () => {
      stale = true
    }
  }, [year, month, fail])

  const moveMonth = (delta: number) => {
    const next = new Date(year, month - 1 + delta, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
  }

  /**
   * 그날 기록으로 들어간다. 기록 화면이 날짜로 그날 기록을 연다 — 하루에 기록은 하나라
   * 운동한 날이면 그 기록이, 아니면 빠뜨린 운동을 채울 빈 기록이 열린다.
   */
  const openDay = (key: string) => navigate(`${paths.session}?date=${key}`)

  return (
    <>
      <ScreenHeader title="기록" hideBack />

      <div className={styles.page}>
        <MonthCalendar
          year={year}
          month={month}
          days={calendar?.days}
          onMoveMonth={moveMonth}
          onSelectDay={openDay}
        />
      </div>
    </>
  )
}
