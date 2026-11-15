import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { analysisApi, isApiError } from '../../api'
import type { MuscleTrend, MuscleTrendRow, TrendWeek } from '../../api'
import { paths } from '../../app/paths'
import { useToast } from '../../components'
import { formatMonthDay } from './format'
import shared from './analysis.module.css'
import styles from './trend.module.css'

/** "2026-11-10" → "11/10". 막대 아래와 읽기 줄은 자리가 좁아 짧게 쓴다 */
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}/${day}`
}

function weekText(week: TrendWeek, latest: boolean): string {
  const range = `${shortDate(week.from)}~${shortDate(week.to)}`
  return latest ? `최근 7일 (${range})` : range
}

/**
 * 아홉 부위가 한 눈금을 쓴다 — 부위끼리 막대 높이를 비교할 수 있어야 해서다. 권장 상한(20) 위로
 * 조금 여유를 둔다. 그보다 많이 한 주가 있으면 그 주에 맞춘다.
 */
function scaleMax(trend: MuscleTrend): number {
  const top = Math.max(trend.thresholds.optimalThrough, ...trend.groups.flatMap((g) => g.weeklySets))
  return Math.ceil(top * 1.15)
}

/**
 * 추이 서브탭 — 부위마다 최근 8주의 주별 세트를 막대로.
 *
 * <p>약점 탭은 최근 4주 평균 하나만 보여 줘서, 그 평균이 늘고 있는 중인지 줄고 있는 중인지, 한 주에 몰아서
 * 한 건지 알 수 없다. 여기서는 그 4주를 주별로 펼치고 그 앞 4주와 나란히 둔다. 마지막 네 막대의 평균이
 * 약점 탭의 숫자와 같다(서버가 같은 구간·같은 분모로 낸다).
 *
 * <p>판정은 색이 아니라 위치로 읽게 한다 — 권장 구간(주 10~20세트)을 옅은 띠로, 부족 경계(4세트)를 가는
 * 선으로 깔고, 막대는 한 색이다. 고른 주(처음에는 최근 주)를 가장 진하게 칠한다.
 */
export function TrendView() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [trend, setTrend] = useState<MuscleTrend | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const data = await analysisApi.getMuscleTrend()
        if (alive) setTrend(data)
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        if (alive) setFailed(true)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [showToast])

  if (!trend) {
    return <div className={shared.status}>{failed ? '추이를 불러오지 못했어요' : '불러오는 중…'}</div>
  }

  const period = (
    <div className={shared.periodBlock}>
      <div className={shared.period}>
        최근 {trend.weeks}주 · {formatMonthDay(trend.periods[0].from)} ~ {formatMonthDay(trend.referenceDate)}
      </div>
      <div className={shared.periodSub}>부위별로 주마다 한 세트 수 · 워밍업 제외</div>
    </div>
  )

  if (trend.firstRecordedOn === null) {
    return (
      <>
        {period}
        <div className={shared.emptyCard}>
          <div className={shared.emptyTitle}>아직 기록한 운동이 없어요</div>
          <div className={shared.emptyBody}>운동을 기록하면 부위별로 주마다 얼마나 했는지 보여 드려요</div>
          <button type="button" className={shared.emptyButton} onClick={() => navigate(paths.session)}>
            운동 기록하기
          </button>
        </div>
      </>
    )
  }

  const max = scaleMax(trend)
  const { thresholds } = trend
  return (
    <>
      {period}

      <div className={styles.legend}>
        <span className={styles.legendItem}>
          <span className={styles.legendBand} aria-hidden="true" />
          권장 구간 주 {thresholds.optimalFrom}~{thresholds.optimalThrough}세트
        </span>
        <span className={styles.legendItem}>
          <span className={styles.legendLine} aria-hidden="true" />주 {thresholds.insufficientBelow}세트 아래는 부족
        </span>
      </div>

      <ul className={styles.list}>
        {trend.groups.map((row) => (
          <TrendCard key={row.key} row={row} periods={trend.periods} max={max} thresholds={thresholds} />
        ))}
      </ul>

      <div className={shared.scaleNote}>
        막대를 누르면 그 주의 세트 수가 나와요. 회색 칸은 기록을 시작하기 전이에요
      </div>
    </>
  )
}

function TrendCard({
  row,
  periods,
  max,
  thresholds,
}: {
  row: MuscleTrendRow
  periods: TrendWeek[]
  max: number
  thresholds: MuscleTrend['thresholds']
}) {
  const last = periods.length - 1
  const [selected, setSelected] = useState(last)
  const week = periods[selected]
  const percent = (sets: number) => `${(Math.min(sets, max) / max) * 100}%`

  return (
    <li className={styles.card}>
      <div className={styles.head}>
        <span className={styles.label}>{row.label}</span>
        {row.recentAverage !== null && <span className={styles.average}>주 {row.recentAverage}세트</span>}
      </div>
      <div className={styles.sub}>
        최근 4주 평균
        {row.previousAverage !== null && ` · 그 전 4주 ${row.previousAverage}세트`}
      </div>

      <div className={styles.plot} role="group" aria-label={`${row.label} 주별 세트`}>
        <span
          className={styles.band}
          style={{
            bottom: percent(thresholds.optimalFrom),
            height: `${((thresholds.optimalThrough - thresholds.optimalFrom) / max) * 100}%`,
          }}
          aria-hidden="true"
        />
        <span className={styles.line} style={{ bottom: percent(thresholds.insufficientBelow) }} aria-hidden="true" />
        <div className={styles.columns} style={{ gridTemplateColumns: `repeat(${periods.length}, 1fr)` }}>
          {periods.map((p, i) => {
            const sets = row.weeklySets[i]
            return (
              <button
                key={p.from}
                type="button"
                className={[
                  styles.column,
                  p.beforeFirstRecord ? styles.before : '',
                  i === selected ? styles.selected : '',
                ].join(' ')}
                aria-pressed={i === selected}
                aria-label={`${weekText(p, i === last)} ${p.beforeFirstRecord ? '기록 전' : `${sets}세트`}`}
                onClick={() => setSelected(i)}
              >
                {!p.beforeFirstRecord && sets > 0 && (
                  <span
                    className={`${styles.bar} ${i === last ? styles.barLatest : ''}`}
                    style={{ height: percent(sets) }}
                  />
                )}
              </button>
            )
          })}
        </div>
      </div>

      <div className={styles.axis} aria-hidden="true">
        <span>{shortDate(periods[0].from)}</span>
        <span>최근 7일</span>
      </div>

      <div className={styles.readout} aria-live="polite">
        <span>{weekText(week, selected === last)}</span>
        <span className={styles.readoutValue}>
          {week.beforeFirstRecord ? '기록 전' : `${row.weeklySets[selected]}세트`}
        </span>
      </div>
    </li>
  )
}
