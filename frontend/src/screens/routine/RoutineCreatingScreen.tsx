import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { isApiError, routineApi } from '../../api'
import { paths } from '../../app/paths'
import styles from './routineCreating.module.css'

/**
 * 너무 빨리 끝나면 화면이 번쩍하고 지나간다. 규칙만으로 짜면 1초도 안 걸리니 이만큼은 보여 준다.
 * LLM을 부르면 2~10초라 이 값은 거의 영향이 없다.
 */
const MIN_SHOW_MS = 600

/**
 * 오늘의 루틴을 만드는 동안 보여 주는 화면. 홈의 "오늘 루틴 추천받기"가 여기로 온다.
 *
 * <p>진행률은 보여 주지 않는다. 걸리는 시간이 LLM 응답에 달려 있어(2~10초, 실패하면 규칙으로) 막대나 단계
 * 표시를 두면 약속을 지키지 못한다. 대신 지금 하는 일을 한 줄로 적는다.
 *
 * <p>다 만들면 루틴 화면으로 <b>바꿔 간다</b>(replace) — 루틴 화면에서 뒤로 가면 이 화면이 아니라 홈이다.
 * 기다리다 뒤로 가도 서버는 루틴을 끝까지 만들고, 홈이 다시 읽을 때 오늘 루틴으로 보인다.
 */
export function RoutineCreatingScreen() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  // 개발 모드(StrictMode)는 이펙트를 두 번 돌린다. 생성 요청이 두 번 가지 않게 시도마다 한 번만 보낸다
  const sentFor = useRef<number | null>(null)
  // 이펙트 정리 함수로 "살아 있음"을 끄면, 두 번째 실행을 건너뛴 탓에 첫 요청의 결과까지 버려진다.
  // 마운트 여부를 따로 들고 결과가 왔을 때 본다
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    if (sentFor.current === attempt) return
    sentFor.current = attempt
    const startedAt = performance.now()
    const wait = () => new Promise((r) => setTimeout(r, Math.max(0, MIN_SHOW_MS - (performance.now() - startedAt))))

    const create = async () => {
      try {
        const routine = await routineApi.createToday()
        await wait()
        if (mounted.current) navigate(paths.routineDetail(routine.id), { replace: true })
      } catch (err) {
        if (!isApiError(err)) throw err
        await wait()
        if (mounted.current) setError(err.message)
      }
    }
    void create()
  }, [attempt, navigate])

  if (error) {
    return (
      <div className={styles.page}>
        <div className={styles.body} role="alert">
          <div className={styles.title}>루틴을 만들지 못했어요</div>
          <div className={styles.sub}>{error}</div>
        </div>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.primary}
            onClick={() => {
              setError(null)
              setAttempt((a) => a + 1)
            }}
          >
            다시 시도
          </button>
          <button type="button" className={styles.secondary} onClick={() => navigate(paths.home, { replace: true })}>
            홈으로
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <div className={styles.body} role="status" aria-live="polite">
        <div className={styles.spinner} aria-hidden="true" />
        <div className={styles.title}>오늘의 루틴 만드는 중</div>
        <div className={styles.sub}>최근 기록을 보고 부위와 세트를 정하고 있어요</div>
      </div>
    </div>
  )
}
