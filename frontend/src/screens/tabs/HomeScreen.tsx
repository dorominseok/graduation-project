import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScreenHeader } from '../../components'
import { isApiError, workoutApi } from '../../api'
import type { WorkoutSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import styles from './home.module.css'

/**
 * 홈 — 운동 시작 진입점.
 *
 * 오늘의 루틴 추천과 주간 요약은 10월에 붙는다. 지금은 기록으로 들어가는 길과
 * 이어쓸 세션이 있는지만 보여준다.
 */
export function HomeScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [current, setCurrent] = useState<WorkoutSession | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const session = await workoutApi.getCurrent()
        if (alive) setCurrent(session)
      } catch (err) {
        if (!isApiError(err)) throw err
      } finally {
        if (alive) setLoading(false)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [])

  const setCount = current?.exercises.reduce((sum, group) => sum + group.sets.length, 0) ?? 0

  return (
    <>
      <ScreenHeader title="홈" hideBack />

      <div className={styles.page}>
        <div className={styles.greeting}>
          {user ? `${user.nickname}님, 오늘도 운동해요` : '오늘도 운동해요'}
        </div>

        {!loading && current && (
          <button type="button" className={styles.resume} onClick={() => navigate(paths.session)}>
            <span className={styles.resumeLabel}>진행 중인 운동이 있어요</span>
            <span className={styles.resumeMeta}>
              {current.exercises.length}종목 · {setCount}세트 기록됨
            </span>
          </button>
        )}

        {!loading && !current && (
          <button type="button" className={styles.start} onClick={() => navigate(paths.session)}>
            운동 시작
          </button>
        )}

        {/* 기록하지 않고 종목만 둘러보는 길. 기록 화면을 거치지 않아도 되게 둔다. */}
        <button type="button" className={styles.browse} onClick={() => navigate(paths.exerciseList)}>
          운동 종목 둘러보기
        </button>
      </div>
    </>
  )
}
