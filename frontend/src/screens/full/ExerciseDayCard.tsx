import type { ExerciseHistoryDay, MeasureType } from '../../api'
import { formatDayLabel } from '../../app/dates'
import { SetGrid } from './SetGrid'
import styles from './exerciseDay.module.css'

/** 종목 기록의 하루 — 날짜와 그날 세트 칸. 종목 상세의 최근 기록과 종목 기록 페이지가 같이 쓴다 */
export function ExerciseDayCard({ day, measureType }: { day: ExerciseHistoryDay; measureType: MeasureType }) {
  const working = day.sets.filter((set) => !set.isWarmup).length
  return (
    <div className={styles.day}>
      <div className={styles.head}>
        <span className={styles.date}>{formatDayLabel(day.performedOn)}</span>
        <span className={styles.count}>{working}세트</span>
      </div>
      <SetGrid measureType={measureType} sets={day.sets} />
    </div>
  )
}
