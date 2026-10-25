import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { exerciseApi, isApiError } from '../../api'
import type { Exercise, ExerciseHistoryDay, Page } from '../../api'
import {
  BODY_PART_LABEL,
  EQUIPMENT_LABEL,
  MEASURE_LABEL,
  PRIMARY_MUSCLE_LABEL,
  PUSH_PULL_LABEL,
} from './exerciseLabels'
import { exerciseImage, hideOnError } from './exerciseImage'
import { ExerciseDayCard } from './ExerciseDayCard'
import { OneRmTrendPanel } from '../analysis/OneRmTrendPanel'
import { paths } from '../../app/paths'
import styles from './exerciseDetail.module.css'

/** 상세에 보여 줄 최근 기록 날 수. 더 있으면 "기록 더보기"로 종목 기록 페이지에 간다 */
const RECENT_DAYS = 3

/**
 * 종목 상세 — 종목 정보, 최근 기록(날짜별 세트 칸), 중량·횟수 종목이면 추정 1RM 추이.
 * 추이 패널은 분석 탭 통계와 같은 것이다.
 */
export function ExerciseDetailScreen() {
  const { exerciseId } = useParams()
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [exercise, setExercise] = useState<Exercise | null>(null)
  const [recent, setRecent] = useState<Page<ExerciseHistoryDay> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!exerciseId) return
    let alive = true
    const load = async () => {
      try {
        const [detail, history] = await Promise.all([
          exerciseApi.get(Number(exerciseId)),
          exerciseApi.getHistory(Number(exerciseId), 0, RECENT_DAYS),
        ])
        if (!alive) return
        setExercise(detail)
        setRecent(history)
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
            <dd>
              {exercise.primaryMuscle ? (PRIMARY_MUSCLE_LABEL[exercise.primaryMuscle] ?? exercise.primaryMuscle) : '-'}
            </dd>
          </div>
          <div className={styles.spec}>
            <dt>동작</dt>
            <dd>{PUSH_PULL_LABEL[exercise.pushPull] ?? exercise.pushPull}</dd>
          </div>
        </dl>

        <div className={styles.sectionTitle}>최근 기록</div>
        {recent && recent.content.length > 0 ? (
          <>
            {recent.content.map((day) => (
              <ExerciseDayCard key={day.sessionId} day={day} measureType={exercise.measureType} />
            ))}
            {recent.page.totalElements > RECENT_DAYS && (
              <button
                type="button"
                className={styles.more}
                onClick={() => navigate(paths.exerciseHistory(exercise.id))}
              >
                기록 더보기 ›
              </button>
            )}
          </>
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
