import { useEffect, useState } from 'react'
import { analysisApi, isApiError } from '../../api'
import type { WeeklyVolume as WeeklyVolumeData } from '../../api'
import { formatShortDate } from './format'
import styles from './weekly.module.css'

// 한 줄의 그림 좌표. 줄 폭에 맞춰 가로로 늘린다
const W = 200
const H = 30
const PAD_TOP = 2
/** 세로축 최소 높이. 기록이 적어도 10 선이 줄 한가운데쯤 오도록 */
const MIN_DOMAIN = 20
/** 권장 구간의 시작(분석 설계서 2.2). 줄마다 이 높이에 선을 긋는다 */
const RECOMMENDED = 10

/** 위쪽 두 모서리만 둥근 막대. 바닥까지 둥글면 0에서 시작한다는 기준이 흐려진다 */
function columnPath(x: number, y: number, w: number, h: number): string {
  const r = Math.min(2.5, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/**
 * 주차별 부위 세트 (LOG-30).
 *
 * <p>약점 판정(최근 28일 평균)과 역할이 다르다. 이건 "주마다 얼마나 했나"를 보는
 * 기록이라 막대를 판정색으로 칠하지 않는다. 주 단위로 판정하면 월요일마다 0이 되고
 * 수요일엔 대부분 부족이 된다 — 분석 설계서 2.1이 버린 방식이다.
 *
 * <p>부위마다 한 줄이고 <b>세로축을 모든 줄이 같이 쓴다.</b> 위아래로 바로 비교되게
 * 하려는 것이다. 처음엔 2열 카드로 칸마다 따로 그렸는데 부위끼리 맞대 볼 수 없었다.
 *
 * <p>줄마다 주 10세트(권장 구간 시작)에 선을 하나 긋는다. 기준이 없으면 막대가
 * 많은지 적은지 읽을 수 없다.
 */
export function WeeklyVolume() {
  const [data, setData] = useState<WeeklyVolumeData | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const result = await analysisApi.getWeeklyVolume()
        if (alive) setData(result)
      } catch (err) {
        if (!isApiError(err)) throw err
        if (alive) setFailed(true)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [])

  if (!data) {
    return <div className={styles.status}>{failed ? '주차별 기록을 불러오지 못했어요' : '불러오는 중…'}</div>
  }

  const weeks = data.weeks
  const groups = data.groups.filter((g) => g.judged)
  const lastIndex = weeks.length - 1
  // "지난주"는 끝난 주 중 마지막. 이번 주가 진행 중이면 그 앞 주다
  const lastDone = weeks[lastIndex].inProgress ? lastIndex - 1 : lastIndex
  const domain = Math.max(MIN_DOMAIN, ...groups.flatMap((g) => g.sets))
  const yOf = (v: number) => PAD_TOP + (1 - v / domain) * (H - PAD_TOP)
  const slot = W / weeks.length
  const barW = Math.min(16, slot - 4)

  return (
    <section className={styles.section}>
      <div className={styles.head}>
        <div className={styles.title}>주차별 부위 세트</div>
        <div className={styles.legend}>
          <span className={styles.legendItem}>
            <span className={styles.swatchLine} />주 10세트 (권장 시작)
          </span>
          <span className={styles.legendItem}>
            <span className={styles.swatchNow} />진행 중인 이번 주
          </span>
        </div>
      </div>

      <div className={styles.rows}>
        <div className={styles.axisRow}>
          <span />
          <span className={styles.axisWeeks}>
            <span>{formatShortDate(weeks[0].start)}</span>
            <span>이번 주</span>
          </span>
          <span className={styles.axisValue}>지난주</span>
        </div>

        {groups.map((group) => {
          const last = lastDone >= 0 ? group.sets[lastDone] : null
          return (
            <div key={group.key} className={styles.row}>
              <span className={styles.rowLabel}>{group.label}</span>
              <svg className={styles.chart} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-hidden="true">
                <line className={styles.base} x1={0} x2={W} y1={H} y2={H} />
                {group.sets.map((v, i) => {
                  if (v === 0) return null
                  const x = i * slot + (slot - barW) / 2
                  const y = yOf(v)
                  return (
                    <path
                      key={weeks[i].start}
                      className={weeks[i].inProgress ? styles.barNow : styles.bar}
                      d={columnPath(x, y, barW, H - y)}
                    />
                  )
                })}
                {/* 막대 위에 그려야 막대에 가려지지 않는다 */}
                <line className={styles.target} x1={0} x2={W} y1={yOf(RECOMMENDED)} y2={yOf(RECOMMENDED)} />
              </svg>
              <span className={`${styles.rowValue} ${last === 0 ? styles.rowValueZero : ''}`}>
                {last ?? '-'}
              </span>
            </div>
          )
        })}
      </div>

      <div className={styles.note}>월~일 기준 · 워밍업 제외. 부족 판정은 약점 탭의 최근 4주 평균으로 해요</div>

      {/* 막대만으로는 정확한 수를 못 읽는다. 표로 전부 볼 수 있게 둔다 */}
      <details className={styles.table}>
        <summary>주차별 표 보기</summary>
        <div className={styles.tableScroll}>
          <table>
            <thead>
              <tr>
                <th scope="col">부위</th>
                {weeks.map((w) => (
                  <th key={w.start} scope="col" className={w.inProgress ? styles.colNow : ''}>
                    {formatShortDate(w.start)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groups.map((g) => (
                <tr key={g.key}>
                  <th scope="row">{g.label}</th>
                  {g.sets.map((v, i) => (
                    <td key={weeks[i].start} className={weeks[i].inProgress ? styles.colNow : ''}>
                      {v}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  )
}
