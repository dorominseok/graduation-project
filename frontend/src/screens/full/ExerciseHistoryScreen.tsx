import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { exerciseApi, isApiError } from '../../api'
import type { Exercise, ExerciseHistoryDay } from '../../api'
import { ExerciseDayCard } from './ExerciseDayCard'
import styles from './exerciseDetail.module.css'

/** 한 번에 불러오는 날 수 */
const PAGE_SIZE = 10

/** 종목 기록 — 그 종목을 한 날을 최신순으로. 끝까지 내리면 열흘씩 이어서 불러온다 */
export function ExerciseHistoryScreen() {
  const { exerciseId } = useParams()
  const id = Number(exerciseId)
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [exercise, setExercise] = useState<Exercise | null>(null)
  const [days, setDays] = useState<ExerciseHistoryDay[]>([])
  /** 다음에 읽을 페이지. 다 읽었으면 null */
  const [nextPage, setNextPage] = useState<number | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const [detail, first] = await Promise.all([exerciseApi.get(id), exerciseApi.getHistory(id, 0, PAGE_SIZE)])
        if (!alive) return
        setExercise(detail)
        setDays(first.content)
        setNextPage(first.page.last ? null : 1)
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        navigate(-1)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [id, navigate, showToast])

  const loadMore = async () => {
    if (nextPage === null || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await exerciseApi.getHistory(id, nextPage, PAGE_SIZE)
      setDays((prev) => [...prev, ...page.content])
      setNextPage(page.page.last ? null : nextPage + 1)
    } catch (err) {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <>
      <ScreenHeader title={exercise ? `${exercise.nameKo} 기록` : '종목 기록'} />
      <div className={styles.page}>
        {!exercise ? (
          <div className={styles.empty}>불러오는 중…</div>
        ) : days.length === 0 ? (
          <div className={styles.empty}>아직 기록이 없어요</div>
        ) : (
          <>
            {days.map((day) => (
              <ExerciseDayCard key={day.sessionId} day={day} measureType={exercise.measureType} />
            ))}
            {nextPage !== null && (
              <button type="button" className={styles.more} onClick={() => void loadMore()} disabled={loadingMore}>
                {loadingMore ? '불러오는 중…' : '더 보기'}
              </button>
            )}
          </>
        )}
      </div>
    </>
  )
}
