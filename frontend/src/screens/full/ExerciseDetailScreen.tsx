import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { exerciseApi, isApiError } from '../../api'
import type { Exercise, LastPerformance } from '../../api'
import {
  BODY_PART_LABEL,
  EQUIPMENT_LABEL,
  MEASURE_LABEL,
  PUSH_PULL_LABEL,
} from './exerciseLabels'
import { exerciseImage, hideOnError } from './exerciseImage'
import { OneRmTrendPanel } from '../analysis/OneRmTrendPanel'
import styles from './exerciseDetail.module.css'

/**
 * 종목 상세 (명세 5.3).
 *
 * 종목 정보, 직전 수행 기록, 그리고 중량·횟수 종목이면 추정 1RM 추이(7.1)를
 * 보여준다. 추이 패널은 분석 탭 통계와 같은 것이다 — 성장을 보려는 사람이
 * 어느 쪽으로 들어오든 같은 그림을 보게 한다.
 */
export function ExerciseDetailScreen() {
  const { exerciseId } = useParams()
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [exercise, setExercise] = useState<Exercise | null>(null)
  const [last, setLast] = useState<LastPerformance | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!exerciseId) return
    let alive = true
    const load = async () => {
      try {
        const [detail, lastPerformance] = await Promise.all([
          exerciseApi.get(Number(exerciseId)),
          exerciseApi.getLastPerformance(Number(exerciseId)),
        ])
        if (!alive) return
        setExercise(detail)
        setLast(lastPerformance)
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        navigate(-1)
      } finally {
        if (alive) setLoading(false)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [exerciseId, navigate, showToast])

  const toggleFavorite = async () => {
    if (!exercise) return
    const next = !exercise.isFavorite
    setExercise({ ...exercise, isFavorite: next })
    try {
      if (next) {
        await exerciseApi.addFavorite(exercise.id)
      } else {
        await exerciseApi.removeFavorite(exercise.id)
      }
    } catch (err) {
      setExercise({ ...exercise, isFavorite: !next })
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    }
  }

  if (loading || !exercise) {
    return (
      <>
        <ScreenHeader title="종목" />
        <div className={styles.empty}>불러오는 중…</div>
      </>
    )
  }

  return (
    <>
      <ScreenHeader
        title={exercise.nameKo}
        trailing={
          <button
            type="button"
            className={`${styles.star} ${exercise.isFavorite ? styles.starOn : ''}`}
            onClick={() => void toggleFavorite()}
            aria-label={exercise.isFavorite ? '즐겨찾기 해제' : '즐겨찾기'}
          >
            {exercise.isFavorite ? '★' : '☆'}
          </button>
        }
      />

      <div className={styles.page}>
        {/* 시작 자세와 끝 자세를 나란히 둔다. 한 장만으로는 어느 쪽으로 움직이는지 모른다 */}
        <div className={styles.hero}>
          {([1, 2] as const).map((shot) => {
            const src = exerciseImage(exercise.nameEn, shot)
            return src ? (
              <img
                key={shot}
                className={styles.heroShot}
                src={src}
                alt=""
                loading="lazy"
                onError={hideOnError}
              />
            ) : null
          })}
        </div>

        {exercise.nameEn && <div className={styles.nameEn}>{exercise.nameEn}</div>}

        <dl className={styles.specs}>
          <div className={styles.spec}>
            <dt>부위</dt>
            <dd>{BODY_PART_LABEL[exercise.bodyPart] ?? exercise.bodyPart}</dd>
          </div>
          <div className={styles.spec}>
            <dt>계열</dt>
            <dd>{exercise.groupName ?? '-'}</dd>
          </div>
          <div className={styles.spec}>
            <dt>기구</dt>
            <dd>{EQUIPMENT_LABEL[exercise.equipment] ?? exercise.equipment}</dd>
          </div>
          <div className={styles.spec}>
            <dt>기록 방식</dt>
            <dd>{MEASURE_LABEL[exercise.measureType] ?? exercise.measureType}</dd>
          </div>
          <div className={styles.spec}>
            <dt>주동근</dt>
            <dd>{exercise.primaryMuscle ?? '-'}</dd>
          </div>
          <div className={styles.spec}>
            <dt>동작</dt>
            <dd>{PUSH_PULL_LABEL[exercise.pushPull] ?? exercise.pushPull}</dd>
          </div>
        </dl>

        <div className={styles.sectionTitle}>최근 기록</div>
        {last ? (
          <div className={styles.lastBox}>
            <div className={styles.lastDate}>{last.performedOn}</div>
            {last.sets.map((set) => (
              <div key={set.setNo} className={styles.lastSet}>
                <span>{set.setNo}세트</span>
                <span>
                  {set.durationSec != null
                    ? `${set.durationSec}초`
                    : `${set.weightKg ?? '-'}kg × ${set.reps ?? '-'}회`}
                  {set.isWarmup && ' (워밍업)'}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <div className={styles.empty}>아직 기록이 없어요</div>
        )}

        {/* 맨몸·시간 종목은 Epley 공식의 전제인 중량이 없어 서버가 추정하지 않는다 */}
        {exercise.measureType === 'WEIGHT_REPS' && (
          <>
            <div className={styles.sectionTitle}>추정 1RM 추이</div>
            <OneRmTrendPanel exerciseId={exercise.id} />
          </>
        )}
      </div>
    </>
  )
}
