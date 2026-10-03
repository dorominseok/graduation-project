import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { exerciseApi, isApiError, workoutApi } from '../../api'
import type { Exercise } from '../../api'
import { paths } from '../../app/paths'
import { useToast } from '../../components'
import { OneRmTrendPanel } from './OneRmTrendPanel'
import { WeeklyVolume } from './WeeklyVolume'
import styles from './analysis.module.css'

/** 칩으로 띄울 종목 수. 가로로 굴려야 보이는 칩은 사실상 없는 칩이다 */
const CHIP_LIMIT = 8
/** 종목 상세를 몇 개까지 읽어 거를지. 중량·횟수가 아닌 종목이 섞여 있어 칩 수보다 넉넉히 */
const LOOKUP_LIMIT = 15
/** 1RM 추이의 기본 구간과 같게 둔다 — 칩을 눌렀는데 빈 그래프가 나오지 않게 */
const LOOKBACK_DAYS = 12 * 7 - 1

function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

interface StatsViewProps {
  selectedId: number | null
  onSelect: (exerciseId: number) => void
}

/**
 * 통계 서브탭 — 주차별 부위 세트(LOG-30)와 종목별 추정 1RM 추이(명세 7.1).
 *
 * <p>목업의 총 볼륨(톤)은 뺐다. 톤 방식 자체를 쓰지 않기로 했다(분석 설계서 1.1 —
 * 맨몸 종목에서 무너지고 종목 간 무게 차이로 왜곡된다).
 */
export function StatsView(props: StatsViewProps) {
  return (
    <>
      <WeeklyVolume />
      <OneRmSection {...props} />
    </>
  )
}

/**
 * 종목별 추정 1RM 추이.
 *
 * <p>칩은 최근 12주에 완료한 기록에서 중량·횟수 종목만 고른다. 전용 API를 두지
 * 않은 것은 기록 선택 화면의 "최근 사용"과 같은 이유다 — 기록이 이미 답을 갖고 있다.
 */
function OneRmSection({ selectedId, onSelect }: StatsViewProps) {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [chips, setChips] = useState<Exercise[] | null>(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const from = new Date()
        from.setDate(from.getDate() - LOOKBACK_DAYS)
        const history = await workoutApi.getHistory({
          from: toDateString(from),
          status: 'DONE',
          size: 30,
        })
        const ids: number[] = []
        for (const session of history.content) {
          for (const exercise of session.exercises) {
            if (!ids.includes(exercise.id)) ids.push(exercise.id)
          }
        }
        const found = await Promise.all(ids.slice(0, LOOKUP_LIMIT).map((id) => exerciseApi.get(id)))
        if (!alive) return
        setChips(found.filter((e) => e.measureType === 'WEIGHT_REPS').slice(0, CHIP_LIMIT))
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        if (alive) setChips([])
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [showToast])

  if (chips === null) {
    return <div className={styles.status}>불러오는 중…</div>
  }

  const allExercises = (
    <button type="button" className={styles.linkButton} onClick={() => navigate(paths.exerciseList)}>
      다른 종목은 종목 목록에서 보기 →
    </button>
  )

  if (chips.length === 0) {
    return (
      <>
        <div className={styles.emptyCard}>
          <div className={styles.emptyTitle}>최근 12주에 중량·횟수로 기록한 종목이 없어요</div>
          <div className={styles.emptyBody}>
            추정 1RM은 무게와 횟수를 함께 적는 종목만 계산해요. 맨몸·시간 종목은 제외돼요
          </div>
        </div>
        {allExercises}
      </>
    )
  }

  // 고른 종목이 칩에 없으면(주소를 직접 고쳤거나 기록을 지웠으면) 첫 칩으로
  const current = chips.find((c) => c.id === selectedId) ?? chips[0]

  return (
    <>
      <div className={styles.periodBlock}>
        <div className={styles.period}>종목별 성장 추이</div>
        <div className={styles.periodSub}>추정 1RM(kg) · 최근 12주에 기록한 종목</div>
      </div>

      <div className={styles.chips} role="tablist" aria-label="종목">
        {chips.map((chip) => (
          <button
            key={chip.id}
            type="button"
            role="tab"
            aria-selected={chip.id === current.id}
            className={`${styles.chip} ${chip.id === current.id ? styles.chipOn : ''}`}
            onClick={() => onSelect(chip.id)}
          >
            {chip.nameKo}
          </button>
        ))}
      </div>

      <OneRmTrendPanel exerciseId={current.id} />

      {allExercises}
    </>
  )
}
