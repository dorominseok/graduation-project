import { useLocation, useNavigate } from 'react-router-dom'
import { formatRest } from '../app/dates'
import { useRestTimer } from './rest-timer-context'
import styles from './RestBar.module.css'

const STEP_SEC = 30

/**
 * 휴식 띠. 본문 위에 얹어 스크롤과 무관하게 같은 자리에 둔다.
 *
 * <p>기록 화면 밖(하단 탭이 있는 화면)에서는 누르면 휴식을 시작한 종목으로 돌아간다. 종목 이름은
 * 띠가 좁아 거의 잘려서 적지 않고, 읽어 주는 이름에만 둔다.
 * ⓘ로 종목 상세를 보던 중이면 한 칸 뒤로 간다 — 새로 쌓으면 기록 화면의 ←가 다시 상세로 간다.
 *
 * @param aboveTabBar 하단 탭이 있는 화면이면 탭 위에 띄운다
 */
export function RestBar({ aboveTabBar = false }: { aboveTabBar?: boolean }) {
  const { rest, done, origin, adjust, skip } = useRestTimer()
  const navigate = useNavigate()
  const location = useLocation()
  const place = aboveTabBar ? styles.aboveTabBar : ''
  const canReturn = aboveTabBar && origin !== null

  const goBack = () => {
    if (!origin) return
    if ((location.state as { fromSession?: boolean } | null)?.fromSession) navigate(-1)
    else navigate(origin.path)
  }

  if (rest) {
    const body = (
      <>
        <span className={styles.time}>{formatRest(rest.left)}</span>
        <span className={styles.main}>
          <span className={styles.label}>
            {canReturn ? '휴식 중 ›' : '휴식 중'}
          </span>
          <span className={styles.track}>
            <span className={styles.bar} style={{ width: `${Math.round((rest.left / rest.total) * 100)}%` }} />
          </span>
        </span>
      </>
    )
    return (
      <div className={`${styles.rest} ${place}`}>
        {canReturn ? (
          <button
            type="button"
            className={styles.open}
            onClick={goBack}
            aria-label={`${origin.exerciseName} 기록으로`}
          >
            {body}
          </button>
        ) : (
          <span className={styles.open}>{body}</span>
        )}
        <div className={styles.buttons}>
          <button type="button" className={styles.button} onClick={() => adjust(-STEP_SEC)}>
            −30s
          </button>
          <button type="button" className={styles.button} onClick={() => adjust(STEP_SEC)}>
            +30s
          </button>
          <button type="button" className={styles.skip} onClick={skip}>
            건너뛰기
          </button>
        </div>
      </div>
    )
  }
  if (!done) return null
  return canReturn ? (
    <button
      type="button"
      className={`${styles.done} ${place}`}
      onClick={goBack}
      aria-label={`휴식 끝, ${origin.exerciseName} 기록으로`}
    >
      휴식 끝 · 다음 세트 ›
    </button>
  ) : (
    <div className={`${styles.done} ${place}`}>휴식 끝 · 다음 세트 시작</div>
  )
}
