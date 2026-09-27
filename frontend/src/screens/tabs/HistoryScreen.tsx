import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { isApiError, workoutApi } from '../../api'
import type { CalendarResponse } from '../../api'
import { paths } from '../../app/paths'
import styles from './history.module.css'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 로컬 날짜를 YYYY-MM-DD로. toISOString은 UTC로 바꿔 하루가 밀린다 */
function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

/**
 * 기록 탭 — 월간 달력 (명세 6.8).
 *
 * <p>날짜를 누르면 그날 기록으로 바로 들어간다. 기록이 없는 날이면 그 날짜로
 * 사후 입력을 시작한다 — 달력에서 빠진 날을 보고 채우는 것이 이 화면의 쓰임이다.
 *
 * <p>목록을 함께 두지 않는 것은 달력이 이미 "언제 운동했는지"를 보여주기 때문이다.
 * 아래에 목록을 붙이면 달력이 반으로 줄어 한 달이 한눈에 안 들어온다.
 */
export function HistoryScreen() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [calendar, setCalendar] = useState<CalendarResponse | null>(null)
  const [opening, setOpening] = useState(false)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  // 달이 바뀌면 그 달의 기록 있는 날짜를 다시 읽는다.
  useEffect(() => {
    const run = async () => {
      try {
        setCalendar(await workoutApi.getCalendar(year, month))
      } catch (err) {
        fail(err)
      }
    }
    void run()
  }, [year, month, fail])

  const moveMonth = (delta: number) => {
    const next = new Date(year, month - 1 + delta, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
  }

  /**
   * 그날 기록으로 들어간다.
   *
   * <p>세션 id는 달력 응답에 없으므로 그 날짜로 한 번 조회한다. 하루에 여러 번
   * 운동했으면 가장 먼저 한 것으로 가고, 나머지는 기록 화면의 날짜 이동으로 본다.
   */
  const openDay = async (key: string) => {
    if (opening) return
    setOpening(true)
    try {
      const page = await workoutApi.getHistory({ from: key, to: key, size: 5 })
      if (page.content.length > 0) {
        navigate(paths.sessionDetail(page.content[0].id))
      } else {
        // 기록이 없는 날 — 그 날짜로 사후 입력을 시작한다(명세 6.2의 BACKFILL).
        navigate(`${paths.session}?date=${key}`)
      }
    } catch (err) {
      fail(err)
    } finally {
      setOpening(false)
    }
  }

  const todayKey = toDateString(today)
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const daysInMonth = new Date(year, month, 0).getDate()
  const dayInfo = (key: string) => calendar?.days.find((d) => d.date === key)

  return (
    <>
      <ScreenHeader title="기록" hideBack />

      <div className={styles.page}>
        <div className={styles.monthBar}>
          <button type="button" className={styles.monthNav} onClick={() => moveMonth(-1)} aria-label="지난 달">
            ‹
          </button>
          <span className={styles.monthLabel}>
            {year}년 {month}월
          </span>
          <button type="button" className={styles.monthNav} onClick={() => moveMonth(1)} aria-label="다음 달">
            ›
          </button>
        </div>

        <div className={styles.weekdays}>
          {WEEKDAYS.map((day) => (
            <span key={day} className={styles.weekday}>
              {day}
            </span>
          ))}
        </div>

        <div className={styles.grid}>
          {/* 1일이 시작하는 요일까지는 빈 자리로 둔다 */}
          {Array.from({ length: firstWeekday }, (_, i) => (
            <span key={`blank-${i}`} className={styles.blank} />
          ))}

          {Array.from({ length: daysInMonth }, (_, i) => {
            const day = i + 1
            const key = toDateString(new Date(year, month - 1, day))
            const info = dayInfo(key)
            return (
              <button
                key={key}
                type="button"
                className={[
                  styles.cell,
                  key === todayKey ? styles.cellToday : '',
                  info?.hasDraft ? styles.cellDraft : '',
                ].join(' ')}
                onClick={() => void openDay(key)}
              >
                <span className={styles.dayNumber}>{day}</span>
                {info && (
                  <span
                    className={[
                      styles.dot,
                      info.sessionCount > 1 ? styles.dotMany : '',
                      info.hasDraft ? styles.dotDraft : '',
                    ].join(' ')}
                  />
                )}
              </button>
            )
          })}
        </div>
      </div>
    </>
  )
}
