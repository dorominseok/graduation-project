import type { CalendarResponse } from '../api'
import { toDateKey } from '../app/dates'
import styles from './MonthCalendar.module.css'

// 주는 월요일에 시작한다. 홈의 "이번 주"와 같은 기준이어야 두 화면이 안 어긋난다
const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일']

interface MonthCalendarProps {
  year: number
  month: number
  /** 운동한 날짜만 온다. 아직 읽는 중이면 비워 둔다 */
  days?: CalendarResponse['days']
  onMoveMonth: (delta: number) => void
  onSelectDay: (dateKey: string) => void
  /**
   * 기록이 없는 날도 누를 수 있는가. 내 기록 탭은 그 날짜로 사후 입력을 시작하지만,
   * 그룹 멤버의 달력은 볼 기록이 없는 날을 누를 이유가 없다.
   */
  emptyDaysSelectable?: boolean
}

/**
 * 월간 달력 — 달 이동, 요일 머리, 날짜 칸. 기록 탭과 그룹 멤버의 기록이 같이 쓴다.
 *
 * <p>세 덩어리를 감싸지 않고 그대로 내놓는다. 부르는 쪽의 세로 배치(flex) 안에서
 * 날짜 칸이 남은 높이를 전부 차지해야 한 달이 한눈에 들어온다.
 */
export function MonthCalendar({
  year,
  month,
  days,
  onMoveMonth,
  onSelectDay,
  emptyDaysSelectable = true,
}: MonthCalendarProps) {
  const todayKey = toDateKey(new Date())
  // getDay()는 일요일이 0이라 월요일이 0이 되게 돌린다
  const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month, 0).getDate()
  const recorded = new Set(days?.map((d) => d.date))

  return (
    <>
      <div className={styles.monthBar}>
        <button type="button" className={styles.monthNav} onClick={() => onMoveMonth(-1)} aria-label="지난 달">
          ‹
        </button>
        <span className={styles.monthLabel}>
          {year}년 {month}월
        </span>
        <button type="button" className={styles.monthNav} onClick={() => onMoveMonth(1)} aria-label="다음 달">
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
          const key = toDateKey(new Date(year, month - 1, day))
          const worked = recorded.has(key)
          return (
            <button
              key={key}
              type="button"
              className={`${styles.cell} ${key === todayKey ? styles.cellToday : ''}`}
              onClick={() => onSelectDay(key)}
              disabled={!worked && !emptyDaysSelectable}
            >
              <span className={styles.dayNumber}>{day}</span>
              {worked && <span className={styles.dot} />}
            </button>
          )
        })}
      </div>
    </>
  )
}
