import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { analysisApi, isApiError } from '../../api'
import type { MuscleGroupKey, Recovery, RecoveryMuscle, RecoveryState } from '../../api'
import { paths } from '../../app/paths'
import { useToast } from '../../components'
import type { BodyMap as BodyMapComponent } from './body/BodyMap'
import { formatMonthDay } from './format'
import shared from './analysis.module.css'
import styles from './recovery.module.css'

/** 범례 순서 — 많이 지친 쪽부터. 문구는 서버의 `stateLabel`과 같다 */
const LEGEND: { state: RecoveryState; label: string }[] = [
  { state: 'HIGH', label: '회복 중 · 많이 함' },
  { state: 'MEDIUM', label: '회복 중' },
  { state: 'LOW', label: '거의 회복' },
  { state: 'RECOVERED', label: '회복됨' },
]

const SWATCH_CLASS: Record<RecoveryState, string> = {
  HIGH: styles.high,
  MEDIUM: styles.medium,
  LOW: styles.low,
  RECOVERED: styles.recovered,
}

const isRecovering = (muscle: RecoveryMuscle) => muscle.state === 'HIGH' || muscle.state === 'MEDIUM'

/** 0 → "오늘", 1 → "어제", 그 뒤로는 "n일 전" */
function agoLabel(daysSince: number): string {
  if (daysSince === 0) return '오늘'
  if (daysSince === 1) return '어제'
  return `${daysSince}일 전`
}

/**
 * 상세 카드의 한 줄 설명. 색이 칠해진 부위에는 늘 한 줄이 있다 — 아래 이름 단추로 부위를 바꿀 때
 * 카드 높이가 달라지면 단추가 손가락 밑에서 움직인다.
 *
 * <p>회복이 끝난 부위에 "오늘 루틴에 들어가요"라고 적지 않는다. 루틴은 같은 날 같이 하는 부위가
 * 회복 중이어도 빼기 때문에(가슴이 회복 중이면 삼두도) 여기서는 알 수 없다.
 */
function noteOf(muscle: RecoveryMuscle): string | null {
  if (muscle.state === 'RECOVERED') return null
  if (muscle.state === 'LOW') return '회복 기간이 막 끝났어요'
  // 종아리·전완은 루틴이 원래 고르지 않는 부위라 "빠진다"고 하면 틀린 말이 된다
  return muscle.judged ? '오늘 루틴에서 빠져요' : '루틴이 따로 고르지 않는 부위예요'
}

/** 처음 열었을 때 보여 줄 부위 — 가장 지친 부위. 색이 칠해진 부위가 없으면 고르지 않는다 */
function mostFatigued(muscles: RecoveryMuscle[]): MuscleGroupKey | null {
  for (const { state } of LEGEND) {
    if (state === 'RECOVERED') break
    const found = muscles.find((m) => m.state === state)
    if (found) return found.key
  }
  return null
}

function Swatch({ state }: { state: RecoveryState }) {
  return <span className={`${styles.swatch} ${SWATCH_CLASS[state]}`} aria-hidden="true" />
}

/**
 * 회복 서브탭 — 부위별 회복 상태를 앞·뒤 그림으로.
 *
 * <p>상태는 서버가 정하고 화면은 칠하기만 한다. 회복 중 판정은 오늘의 루틴이 부위를 빼는
 * 판정과 같아서 "회복 중이면 오늘 루틴에서 빠진다"고 적을 수 있다.
 *
 * <p>그림의 팔 부위는 손가락보다 작다. 그래서 색이 칠해진 부위는 그림 아래에 이름으로도 두고,
 * 이름을 눌러도 같은 부위가 골라진다. 그림의 색을 글로 옮긴 것이기도 하다.
 */
export function RecoveryView() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [loaded, setLoaded] = useState<{ recovery: Recovery; BodyMap: typeof BodyMapComponent } | null>(null)
  const [failed, setFailed] = useState(false)
  /** 직접 고른 부위. 고르기 전에는 가장 지친 부위를 보여 준다 */
  const [picked, setPicked] = useState<MuscleGroupKey | null>(null)
  const detailRef = useRef<HTMLElement>(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      // 그림 경로(60KB)는 이 서브탭에서만 쓴다. 앱 본체에 실으면 모든 화면의 첫 로딩이 그만큼
      // 무거워져서 여기서 따로 받는다. 못 받으면(연결 끊김, 새 버전이 배포된 뒤의 옛 화면) null
      const body = import('./body/BodyMap').catch(() => null)
      try {
        const recovery = await analysisApi.getRecovery()
        const BodyMap = (await body)?.BodyMap
        if (!alive) return
        if (BodyMap) setLoaded({ recovery, BodyMap })
        else setFailed(true)
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

  if (!loaded) {
    return <div className={shared.status}>{failed ? '회복 상태를 불러오지 못했어요' : '불러오는 중…'}</div>
  }

  const { recovery: data, BodyMap } = loaded
  const { muscles, recoveryDays, heavySets } = data

  const header = (
    <div className={shared.periodBlock}>
      <div className={shared.period}>오늘 회복 상태 · {formatMonthDay(data.date)}</div>
      <div className={shared.periodSub}>기록한 날짜와 세트 수로 짐작한 값이에요 · 워밍업 제외</div>
    </div>
  )

  // 최근 기록이 하나도 없으면 전부 "회복됨"이다. 틀린 말은 아니지만 알려 줄 게 없으니
  // 회색 그림과 안내만 둔다
  if (muscles.every((m) => m.lastTrainedOn === null)) {
    return (
      <>
        {header}
        <section className={styles.mapCard}>
          <div className={styles.figures} aria-hidden="true">
            <BodyMap side="front" muscles={muscles} />
            <BodyMap side="back" muscles={muscles} />
          </div>
          <div className={styles.emptyText}>
            <div className={shared.emptyTitle}>운동을 기록하면 회복 상태가 보여요</div>
            <div className={shared.emptyBody}>최근에 운동한 부위를 그림에 색으로 알려드려요</div>
            <button type="button" className={shared.emptyButton} onClick={() => navigate(paths.session)}>
              운동 기록하기
            </button>
          </div>
        </section>
      </>
    )
  }

  const selectedKey = picked ?? mostFatigued(muscles)
  const selected = muscles.find((m) => m.key === selectedKey) ?? null
  const recovering = muscles.filter(isRecovering)
  const almost = muscles.filter((m) => m.state === 'LOW')
  const note = selected && noteOf(selected)

  // 작은 화면에서는 상세가 그림 아래로 밀려 있어, 그림을 눌러도 바뀐 게 안 보인다.
  // 가려져 있을 때만 보이는 데까지 올린다
  const pickOnMap = (key: MuscleGroupKey) => {
    setPicked(key)
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    detailRef.current?.scrollIntoView({ block: 'nearest', behavior: reduceMotion ? 'auto' : 'smooth' })
  }

  const nameButtons = (group: RecoveryMuscle[]) =>
    group.map((m) => (
      <button
        key={m.key}
        type="button"
        className={`${styles.name} ${m.key === selectedKey ? styles.nameOn : ''}`}
        aria-pressed={m.key === selectedKey}
        onClick={() => setPicked(m.key)}
      >
        <Swatch state={m.state} />
        {m.label}
      </button>
    ))

  return (
    <>
      {header}

      <section className={styles.mapCard}>
        <div className={styles.figures}>
          {(['front', 'back'] as const).map((side) => (
            <div key={side} className={styles.figureCol}>
              <div className={styles.sideLabel} aria-hidden="true">
                {side === 'front' ? '앞' : '뒤'}
              </div>
              <BodyMap side={side} muscles={muscles} selected={selectedKey} onSelect={pickOnMap} />
            </div>
          ))}
        </div>

        <ul className={styles.legend} aria-label="색 안내">
          {LEGEND.map(({ state, label }) => (
            <li key={state}>
              <Swatch state={state} />
              {label}
            </li>
          ))}
        </ul>
      </section>

      <section ref={detailRef} className={styles.detail} aria-live="polite">
        {selected ? (
          <>
            <div className={styles.detailHead}>
              <span className={styles.detailName}>{selected.label}</span>
              <span className={styles.detailState}>
                <Swatch state={selected.state} />
                {selected.stateLabel}
              </span>
            </div>
            <dl className={styles.facts}>
              <div className={styles.fact}>
                <dt>마지막 운동</dt>
                <dd>
                  {selected.lastTrainedOn !== null && selected.daysSince !== null
                    ? `${formatMonthDay(selected.lastTrainedOn)} · ${agoLabel(selected.daysSince)}`
                    : '최근 기록이 없어요'}
                </dd>
              </div>
              {recoveryDays > 0 && (
                <div className={styles.fact}>
                  <dt>최근 {recoveryDays}일 세트</dt>
                  <dd>{selected.recentSets}세트</dd>
                </div>
              )}
            </dl>
            {note && <div className={styles.detailNote}>{note}</div>}
          </>
        ) : (
          <div className={styles.hint}>그림에서 부위를 누르면 마지막 운동일과 세트 수를 보여드려요</div>
        )}
      </section>

      {/* 그림의 색을 글로도 적는다. 이름을 누르면 그림에서 누른 것과 같다 */}
      <section className={styles.names} aria-label="회복 중인 부위">
        {recovering.length > 0 ? (
          <div className={styles.nameRow}>
            <span className={styles.nameRowLabel}>회복 중</span>
            <span className={styles.nameList}>{nameButtons(recovering)}</span>
          </div>
        ) : (
          <div className={styles.namesNone}>회복 중인 부위가 없어요</div>
        )}
        {almost.length > 0 && (
          <div className={styles.nameRow}>
            <span className={styles.nameRowLabel}>거의 회복</span>
            <span className={styles.nameList}>{nameButtons(almost)}</span>
          </div>
        )}
      </section>

      {recoveryDays > 0 && (
        <div className={styles.footnote}>
          오늘 포함 {recoveryDays}일 안에 운동한 부위를 회복 중으로 봐요. 그동안 {heavySets}세트 이상 했으면 ‘많이 함’으로 표시해요
        </div>
      )}
    </>
  )
}
