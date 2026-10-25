import type { MeasureType } from '../../api'
import { trimNumber } from './setFormat'
import styles from './setGrid.module.css'

export interface GridSet {
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  isWarmup: boolean
}

/**
 * 세트 칸 — "1세트 / 40kg / 10회"를 한 줄에 다섯 칸씩. 그룹 멤버 기록과 종목 기록이 같이 쓴다.
 *
 * <p>워밍업은 번호를 매기지 않는다. 본세트만 볼륨에 들어가는 것과 같은 기준이다.
 */
export function SetGrid({ measureType, sets }: { measureType: MeasureType; sets: GridSet[] }) {
  const labels = sets.reduce<string[]>((acc, set) => {
    const done = acc.filter((label) => label !== '워밍업').length
    return [...acc, set.isWarmup ? '워밍업' : `${done + 1}세트`]
  }, [])
  return (
    <div className={styles.grid}>
      {sets.map((set, index) => (
        <SetCell key={index} label={labels[index]} measureType={measureType} set={set} />
      ))}
    </div>
  )
}

/** 세트 한 칸. 종목의 측정 방식이 쓰지 않는 줄은 그리지 않는다 */
function SetCell({ label, measureType, set }: { label: string; measureType: MeasureType; set: GridSet }) {
  const weighted = measureType === 'WEIGHT_REPS' || measureType === 'WEIGHTED_BODYWEIGHT'
  return (
    <div className={`${styles.cell} ${set.isWarmup ? styles.warmup : ''}`}>
      <span className={styles.label}>{label}</span>
      {weighted && (
        <span className={styles.value}>
          {measureType === 'WEIGHTED_BODYWEIGHT' ? '+' : ''}
          {trimNumber(set.weightKg ?? 0)}
          <span className={styles.unit}>kg</span>
        </span>
      )}
      {measureType === 'TIME' ? (
        <span className={styles.value}>
          {set.durationSec ?? 0}
          <span className={styles.unit}>초</span>
        </span>
      ) : (
        <span className={styles.value}>
          {set.reps ?? 0}
          <span className={styles.unit}>회</span>
        </span>
      )}
    </div>
  )
}
