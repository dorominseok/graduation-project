import { Fragment, useState } from 'react'
import type { ReactNode } from 'react'
import type { MeasureType, RoundTimes } from '../../api'
import { SegmentedTabs } from '../../components'
import { formatDuration } from '../../app/dates'
import { exerciseImage, hideOnError } from '../full/exerciseImage'
import { SetGrid } from '../full/SetGrid'
import { GTG_ROUND, roundLabel } from '../full/sessionPlan'
import type { GridSet } from '../full/SetGrid'
import styles from './group.module.css'

/** 하루 기록에서 무엇을 보는지 — 하루 전체, 또는 회차 하나(GTG는 0) */
export type DayView = 'all' | number

/** 하루 기록의 종목 하나 — 그룹 멤버의 하루 기록과 내 "운동 일지 보기"가 같은 모양으로 넘긴다 */
export interface DayExercise {
  exerciseId: number
  /** 하루 기록 안의 회차(결정 기록 D-22). 없으면 1, 0은 GTG(D-23) */
  round?: number
  exerciseName: string
  /** 동작 그림 파일 이름(exerciseImage)을 만든다. 없으면 그림 없이 */
  exerciseNameEn: string | null
  measureType: MeasureType
  sets: GridSet[]
}

/** "2026-10-11T19:12:03+09:00" → "오후 7:12" */
function formatClock(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

/**
 * 운동 머리 한 줄 — "오후 7:12 시작 · 1시간 10분". 하루에 두 번 운동했으면 시간은 둘을 더한 것이다.
 *
 * <p>나중에 입력한 기록인지는 표시하지 않는다 — 기록을 의심하는 표시처럼 읽힌다. 시작 시각은
 * 운동 시간이 있을 때만 적는다. 없으면 나중에 입력한 기록이라 저장 시각이 운동한 시각이 아니다.
 */
function metaOf(startedAt: string | null, durationSec: number | null): string[] {
  const parts: string[] = []
  if (durationSec != null && startedAt) parts.push(`${formatClock(startedAt)} 시작`)
  if (durationSec != null) parts.push(formatDuration(durationSec))
  return parts
}

/**
 * 그날 기록 — 머리 줄(시각·시간·세트 수, 오른쪽에 {@code trailing}) 아래에 종목마다 카드 하나.
 * 세트를 칸으로 늘어놓아, 줄글보다 한눈에 들어오게 한다. 그룹에서는 {@code trailing}에 따봉을 둔다.
 *
 * <p>회차가 둘 이상이거나 GTG가 있으면 맨 위에 탭([전체][1회차][2회차][GTG], 결정 기록 D-24). 전체는 회차 머리를 달아 이어
 * 보이고, 회차를 고르면 그 회차만 — 머리 줄도 "2회차 · 오후 7:12 시작 · 41분"으로 그 회차의 것이다. 회차 하나만 캡처·저장할 수
 * 있게(친구 피드백). {@code view}를 넘기면 부모가 고른 탭을 쥔다(운동 일지의 이미지 저장).
 */
export function DaySessionView({
  startedAt,
  durationSec,
  exercises,
  rounds,
  trailing,
  view,
  onViewChange,
  hideTabs = false,
}: {
  startedAt: string | null
  /** 보정값 우선. 시간 없는 사후 입력은 null */
  durationSec: number | null
  exercises: DayExercise[]
  /** 회차마다 첫 세트 시각과 시간. 회차가 둘 이상이면 카드 사이에 회차 머리를 단다 */
  rounds?: RoundTimes[]
  trailing?: ReactNode
  /** 고른 탭. 넘기지 않으면 이 안에서 쥐고 전체로 시작한다 */
  view?: DayView
  onViewChange?: (view: DayView) => void
  /** 탭을 숨긴다 — 저장할 이미지에는 탭이 없다 */
  hideTabs?: boolean
}) {
  const roundOf = (exercise: DayExercise) => exercise.round ?? 1
  // 서버가 회차 순(GTG는 맨 뒤)으로 준다
  const roundsInDay = [...new Set(exercises.map(roundOf))]
  // 회차가 둘 이상이거나 GTG가 있으면 나눈다 — GTG만 한 날도 GTG인 줄 보이게
  const split = roundsInDay.length > 1 || roundsInDay.includes(GTG_ROUND)
  const [ownView, setOwnView] = useState<DayView>('all')
  const requested = view ?? ownView
  const current: DayView = requested !== 'all' && split && roundsInDay.includes(requested) ? requested : 'all'
  const changeView = onViewChange ?? setOwnView
  const shown = current === 'all' ? exercises : exercises.filter((exercise) => roundOf(exercise) === current)
  const setCount = shown.reduce((sum, e) => sum + e.sets.filter((s) => !s.isWarmup).length, 0)
  const currentTimes = current === 'all' ? null : rounds?.find((it) => it.round === current)
  const headMeta =
    current === 'all'
      ? metaOf(startedAt, durationSec)
      : [roundLabel(current), ...(currentTimes ? metaOf(currentTimes.startedAt, currentTimes.durationSec) : [])]
  const tabs = [
    { value: 'all', label: '전체' },
    ...roundsInDay.map((round) => ({ value: String(round), label: roundLabel(round) })),
  ]
  const roundHead = (round: number) => {
    const times = rounds?.find((it) => it.round === round)
    const meta = times ? metaOf(times.startedAt, times.durationSec) : []
    return <div className={styles.roundHead}>{[roundLabel(round), ...meta].join(' · ')}</div>
  }

  return (
    <section className={styles.session}>
      {split && !hideTabs && (
        <SegmentedTabs
          items={tabs}
          value={current === 'all' ? 'all' : String(current)}
          onChange={(value) => changeView(value === 'all' ? 'all' : Number(value))}
          aria-label="회차"
        />
      )}
      <div className={styles.sessionHead}>
        <span className={styles.sessionMeta}>{headMeta.join(' · ')}</span>
        <span className={styles.sessionRight}>
          <span className={styles.sessionSets}>{setCount}세트</span>
          {trailing}
        </span>
      </div>

      {shown.map((exercise, index) => (
        <Fragment key={`${exercise.exerciseId}-${roundOf(exercise)}-${index}`}>
          {/* 전체에서만 회차 머리 — 회차 하나를 고르면 머리 줄에 회차 이름이 있다 */}
          {split && current === 'all' && (index === 0 || roundOf(shown[index - 1]) !== roundOf(exercise)) &&
            roundHead(roundOf(exercise))}
          <ExerciseCard exercise={exercise} />
        </Fragment>
      ))}
    </section>
  )
}

function ExerciseCard({ exercise }: { exercise: DayExercise }) {
  const shots = ([1, 2] as const)
    .map((shot) => exerciseImage(exercise.exerciseNameEn, shot))
    .filter((src): src is string => src != null)

  return (
    <div className={`${styles.card} ${styles.exerciseCard}`}>
      <div className={styles.exerciseHead}>
        <span className={styles.exerciseName}>{exercise.exerciseName}</span>
        {shots.length > 0 && (
          <span className={styles.exerciseShots} aria-hidden="true">
            {shots.map((src) => (
              <img key={src} src={src} alt="" className={styles.exerciseShot} onError={hideOnError} loading="lazy" />
            ))}
          </span>
        )}
      </div>
      <SetGrid measureType={exercise.measureType} sets={exercise.sets} />
    </div>
  )
}
