import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { analysisApi, isApiError } from '../../api'
import type { MuscleTrend, MuscleTrendRow, TrendWeek } from '../../api'
import { paths } from '../../app/paths'
import { useToast } from '../../components'
import { BAR_TICKS, VERDICT_TONE, barPercent, formatMonthDay } from './format'
import type { Tone } from './format'
import shared from './analysis.module.css'
import styles from './trend.module.css'

const TONE_CLASS: Record<Tone, string> = {
  danger: shared.toneDanger,
  warn: shared.toneWarn,
  good: shared.toneGood,
  neutral: shared.toneNeutral,
}

/** "2026-11-10" → "11/10". 막대 아래 줄은 자리가 좁아 짧게 쓴다 */
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}/${day}`
}

function weekText(week: TrendWeek, latest: boolean): string {
  return latest ? '최근 7일' : `${shortDate(week.from)}~${shortDate(week.to)}`
}

/**
 * 추이 서브탭 — 부위마다 최근 8주의 주별 세트.
 *
 * <p>약점 탭은 최근 4주 평균 하나만 보여 줘서, 그 평균이 늘고 있는 중인지 줄고 있는 중인지, 한 주에 몰아서
 * 한 건지 알 수 없다. 여기서는 그 4주를 주별로 펼치고 그 앞 4주와 나란히 둔다. 마지막 네 막대의 평균이
 * 약점 탭의 숫자와 같다(서버가 같은 구간·같은 분모로 낸다).
 *
 * <p>막대는 약점 탭의 막대를 주마다 세로로 세운 것이다 — 같은 트랙·같은 눈금(4·10·20)·같은 눈금 비율
 * (`BAR_SCALE`)·같은 판정 색. 막대마다 그 주 세트가 닿은 구간 색이고(서버가 정한다), 부위 이름 옆 알약이
 * 4주 평균 판정이다. 부위 전체 판정으로 막대를 다 칠하면 12세트를 한 주까지 빨갛게 보인다.
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
      <div className={shared.periodSub}>부위별로 주마다 한 세트 수 · 오른쪽 숫자는 최근 4주 평균</div>
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

  const { thresholds } = trend
  return (
    <>
      {period}

      <ul className={shared.tiers}>
        {trend.groups.map((row) => (
          <TrendRow key={row.key} row={row} periods={trend.periods} />
        ))}
      </ul>

      <div className={shared.scaleNote}>
        눈금은 주 {thresholds.insufficientBelow} · {thresholds.optimalFrom} · {thresholds.optimalThrough}세트 —{' '}
        {thresholds.optimalFrom}~{thresholds.optimalThrough}세트가 권장 구간이에요 (ACSM). 막대를 누르면 그 주의
        세트 수가 나와요
      </div>
    </>
  )
}

function TrendRow({ row, periods }: { row: MuscleTrendRow; periods: TrendWeek[] }) {
  const last = periods.length - 1
  const [selected, setSelected] = useState(last)
  const week = periods[selected]
  const tone: Tone = row.verdict ? VERDICT_TONE[row.verdict] : 'neutral'
  const weekTone = (i: number): Tone => {
    const verdict = row.weeklyVerdicts[i]
    return verdict ? VERDICT_TONE[verdict] : 'neutral'
  }

  return (
    <li className={shared.tier}>
      <div className={shared.tierHead}>
        <span className={shared.tierLeft}>
          <span className={shared.tierLabel}>{row.label}</span>
          {row.recentAverage !== null && <span className={shared.sets}>{row.recentAverage}세트</span>}
        </span>
        <span className={shared.tierRight}>
          {row.verdictLabel && <span className={`${shared.pill} ${TONE_CLASS[tone]}`}>{row.verdictLabel}</span>}
        </span>
      </div>
      {row.previousAverage !== null && <div className={styles.previous}>그 전 4주 {row.previousAverage}세트</div>}

      <div
        className={styles.weeks}
        style={{ gridTemplateColumns: `repeat(${periods.length}, 1fr)` }}
        role="group"
        aria-label={`${row.label} 주별 세트`}
      >
        {periods.map((p, i) => {
          const sets = row.weeklySets[i]
          return (
            <button
              key={p.from}
              type="button"
              className={`${styles.week} ${p.beforeFirstRecord ? styles.before : ''}`}
              aria-pressed={i === selected}
              aria-label={`${weekText(p, i === last)} ${p.beforeFirstRecord ? '기록 전' : `${sets}세트`}`}
              onClick={() => setSelected(i)}
            >
              {/* 약점 탭의 가로 막대를 그대로 세운다 — 트랙·눈금·채움이 같은 규칙이다 */}
              <span className={styles.upright} aria-hidden="true">
                <span className={`${shared.track} ${styles.track}`}>
                  {BAR_TICKS.map((tick) => (
                    <span
                      key={tick}
                      className={`${shared.tick} ${tick === 20 ? shared.tickStrong : ''}`}
                      style={{ left: `${barPercent(tick)}%` }}
                    />
                  ))}
                  {!p.beforeFirstRecord && sets > 0 && (
                    <span
                      className={`${shared.fill} ${TONE_CLASS[weekTone(i)]}`}
                      style={{ width: `${barPercent(sets)}%` }}
                    />
                  )}
                </span>
              </span>
              <span className={`${styles.mark} ${i === selected ? styles.markOn : ''}`} aria-hidden="true" />
            </button>
          )
        })}
      </div>

      <div className={styles.foot}>
        <span className={styles.footStart} aria-hidden="true">
          {shortDate(periods[0].from)}
        </span>
        <span aria-live="polite">
          {weekText(week, selected === last)}{' '}
          <span className={styles.footValue}>{week.beforeFirstRecord ? '기록 전' : `${row.weeklySets[selected]}세트`}</span>
        </span>
      </div>
    </li>
  )
}
