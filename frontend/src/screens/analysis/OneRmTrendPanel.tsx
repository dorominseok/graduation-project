import { useEffect, useState } from 'react'
import { isApiError, statsApi } from '../../api'
import type { OneRmTrend } from '../../api'
import { useToast } from '../../components'
import { OneRmChart } from './OneRmChart'
import { formatMonthDay } from './format'
import styles from './oneRm.module.css'

interface OneRmTrendPanelProps {
  /** 중량·횟수 종목이어야 한다. 아니면 서버가 400을 준다 */
  exerciseId: number
}

/** 소수 첫째 자리까지. 뺄셈에서 생기는 0.30000000000000004 같은 꼬리를 자른다 */
function round1(value: number): number {
  return Math.round(value * 10) / 10
}

/**
 * 종목 하나의 추정 1RM 추이와 요약 3칸.
 *
 * <p>분석 탭의 통계와 종목 상세가 같이 쓴다. 한쪽에만 두면 "성장"을 보려는
 * 사람이 어디서 찾을지에 따라 못 보게 된다.
 *
 * <p>종목을 바꿔 다시 읽는 동안에는 이전 그림을 흐리게 남겨둔다. 비웠다 채우면
 * 화면 높이가 출렁인다.
 */
export function OneRmTrendPanel({ exerciseId }: OneRmTrendPanelProps) {
  const { showToast } = useToast()
  const [trend, setTrend] = useState<OneRmTrend | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(0)

  useEffect(() => {
    let alive = true
    const load = async () => {
      setLoading(true)
      try {
        const result = await statsApi.getOneRmTrend(exerciseId)
        if (!alive) return
        setTrend(result)
        // 처음엔 가장 최근 날짜를 고른 상태로 둔다 — 아래 설명 줄이 비지 않게
        setSelected(Math.max(0, result.points.length - 1))
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
      } finally {
        if (alive) setLoading(false)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [exerciseId, showToast])

  if (!trend) {
    return <div className={styles.empty}>{loading ? '불러오는 중…' : '추이를 불러오지 못했어요'}</div>
  }

  const points = trend.points
  const busyClass = loading ? styles.stale : ''

  if (points.length === 0) {
    return (
      <div className={`${styles.panel} ${busyClass}`}>
        <div className={styles.empty}>
          최근 12주에 12회 이하로 한 세트가 없어요
          <span className={styles.emptyHint}>1RM은 12회 이하 세트로만 추정해요. 고반복은 오차가 커서 빼요</span>
        </div>
      </div>
    )
  }

  const values = points.map((p) => p.estimatedOneRm)
  const best = Math.max(...values)
  const latest = values[values.length - 1]
  const delta = points.length > 1 ? round1(latest - values[0]) : null
  const current = points[Math.min(selected, points.length - 1)]

  return (
    <div className={`${styles.panel} ${busyClass}`}>
      <div className={styles.tiles}>
        <div className={styles.tile}>
          <div className={styles.tileLabel}>최고 추정 1RM</div>
          <div className={styles.tileValue}>
            {best}
            <span className={styles.tileUnit}>kg</span>
          </div>
        </div>
        <div className={styles.tile}>
          <div className={styles.tileLabel}>최근 추정 1RM</div>
          <div className={styles.tileValue}>
            {latest}
            <span className={styles.tileUnit}>kg</span>
          </div>
        </div>
        <div className={styles.tile}>
          <div className={styles.tileLabel}>처음 대비</div>
          <div
            className={`${styles.tileValue} ${
              delta == null || delta === 0 ? '' : delta > 0 ? styles.up : styles.down
            }`}
          >
            {delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta}`}
            {delta != null && <span className={styles.tileUnit}>kg</span>}
          </div>
        </div>
      </div>

      <div className={styles.chartBox}>
        <OneRmChart points={points} selected={Math.min(selected, points.length - 1)} onSelect={setSelected} />
        <div className={styles.readout} aria-live="polite">
          <span className={styles.readoutDate}>{formatMonthDay(current.date)}</span>
          <span className={styles.readoutValue}>{current.estimatedOneRm}kg</span>
          <span className={styles.readoutBasis}>
            {current.basedOnSet.weightKg}kg × {current.basedOnSet.reps}회에서 추정
          </span>
        </div>
      </div>

      <div className={styles.caption}>
        최근 12주 · 그날 가장 높은 값 · 12회 이하 세트로 추정
        {points.length < 3 && <> · 기록이 더 쌓이면 추세가 보여요</>}
      </div>

      {/* 그림을 못 보는 사람도, 점을 누르기 번거로운 사람도 값을 다 볼 수 있게 */}
      <details className={styles.table}>
        <summary>날짜별 기록 보기</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">날짜</th>
              <th scope="col">추정 1RM</th>
              <th scope="col">근거 세트</th>
            </tr>
          </thead>
          <tbody>
            {[...points].reverse().map((p) => (
              <tr key={p.date}>
                <td>{formatMonthDay(p.date)}</td>
                <td>{p.estimatedOneRm}kg</td>
                <td>
                  {p.basedOnSet.weightKg}kg × {p.basedOnSet.reps}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}
