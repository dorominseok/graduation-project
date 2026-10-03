import { useEffect, useState } from 'react'
import { analysisApi, isApiError } from '../../api'
import type { WeeklyVolume as WeeklyVolumeData } from '../../api'
import { formatShortDate } from './format'
import styles from './weekly.module.css'

// 그림 좌표. 비율을 고정하고 칸 폭에 맞춰 통째로 늘린다
const W = 160
const H = 64
const PAD_TOP = 4
const PAD_BOTTOM = 2
/** 세로축 최소 높이. 20 기준선이 늘 보이도록 24까지는 잡아둔다 */
const MIN_DOMAIN = 24
const REFERENCE_LINES = [10, 20] as const

/** 위쪽 두 모서리만 둥근 막대. 바닥까지 둥글면 0에서 시작한다는 기준이 흐려진다 */
function columnPath(x: number, y: number, w: number, h: number): string {
  const r = Math.min(3, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/**
 * 주차별 부위 세트 (LOG-30).
 *
 * <p>약점 판정(최근 28일 평균)과 역할이 다르다. 이건 "주마다 얼마나 했나"를 보는
 * 기록이라 판정색을 칠하지 않고 10·20 기준선만 긋는다. 주 단위로 판정하면 월요일마다
 * 0이 되고 수요일엔 대부분 부족이 된다 — 분석 설계서 2.1이 버린 방식이다.
 *
 * <p>이번 주는 아직 끝나지 않았으므로 흐리게 그린다. 다른 주와 같은 진하기면
 * 수요일의 낮은 막대가 "이번 주에 덜 했다"로 읽힌다.
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
  const slot = W / weeks.length
  const barW = Math.min(14, slot - 4)

  return (
    <section className={styles.section}>
      <div className={styles.head}>
        <div className={styles.title}>주차별 부위 세트</div>
        <div className={styles.sub}>
          월~일 기준 · 워밍업 제외 · 선은 주 10·20세트. 판정은 약점 탭의 최근 4주 평균으로 해요
        </div>
      </div>

      <div className={styles.grid}>
        {groups.map((group) => {
          const domain = Math.max(MIN_DOMAIN, ...group.sets)
          const yOf = (v: number) => PAD_TOP + (1 - v / domain) * (H - PAD_TOP - PAD_BOTTOM)
          return (
            <div key={group.key} className={styles.cell}>
              <div className={styles.cellHead}>
                <span className={styles.cellLabel}>{group.label}</span>
                {lastDone >= 0 && <span className={styles.cellValue}>지난주 {group.sets[lastDone]}</span>}
              </div>
              <svg className={styles.chart} viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
                {REFERENCE_LINES.map((v) => (
                  <line key={v} className={styles.ref} x1={0} x2={W} y1={yOf(v)} y2={yOf(v)} />
                ))}
                <line className={styles.base} x1={0} x2={W} y1={H - PAD_BOTTOM} y2={H - PAD_BOTTOM} />
                {group.sets.map((v, i) => {
                  if (v === 0) return null
                  const x = i * slot + (slot - barW) / 2
                  const y = yOf(v)
                  return (
                    <path
                      key={weeks[i].start}
                      className={weeks[i].inProgress ? styles.barNow : styles.bar}
                      d={columnPath(x, y, barW, H - PAD_BOTTOM - y)}
                    />
                  )
                })}
              </svg>
              <div className={styles.axis}>
                <span>{lastIndex}주 전</span>
                <span>{weeks[lastIndex].inProgress ? '이번 주(진행 중)' : '이번 주'}</span>
              </div>
            </div>
          )
        })}
      </div>

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
