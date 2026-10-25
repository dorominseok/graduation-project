import { useLocation, useNavigate } from 'react-router-dom'
import { formatRest } from '../app/dates'
import { useRestTimer } from './rest-timer-context'
import styles from './RestBar.module.css'

const STEP_SEC = 30

/**
 * 휴식 띠. 본문 위에 얹어 스크롤과 무관하게 같은 자리에 둔다.
 *
 * <p>누르면 쉬고 나서 할 세트로 간다. 쉬는 중에는 기록 화면 밖(하단 탭이 있는 화면)에서만 누를 수
 * 있고, 종목 이름은 띠가 좁아 거의 잘려서 읽어 주는 이름에만 둔다. "휴식 끝" 띠는 자리가 넉넉해
 * 다음 세트를 전체 이름으로 적고, 기록 화면 안에서도 누를 수 있다.
 *
 * <p>ⓘ로 그 세트의 종목 상세를 보던 중이면 한 칸 뒤로 간다 — 새로 쌓으면 기록 화면의 ←가 다시
 * 상세로 간다. 상세나 기록 화면 안에서 다른 종목으로 갈 때는 지금 자리를 바꿔 끼운다.
 *
 * @param aboveTabBar 하단 탭이 있는 화면이면 탭 위에 띄운다
 */
export function RestBar({ aboveTabBar = false }: { aboveTabBar?: boolean }) {
  const { rest, done, origin, adjust, skip, dismiss } = useRestTimer()
  const navigate = useNavigate()
  const location = useLocation()
  const place = aboveTabBar ? styles.aboveTabBar : ''
  const canReturn = aboveTabBar && origin !== null

  const goNext = () => {
    if (!origin) return
    dismiss()
    // ⓘ가 상세로 올 때 자기 주소를 실어 보낸다
    const from = (location.state as { fromSession?: string } | null)?.fromSession
    if (aboveTabBar && from === origin.path) navigate(-1)
    else navigate(origin.path, { replace: !aboveTabBar || from !== undefined })
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
            onClick={goNext}
            aria-label={`${origin.exerciseName} ${origin.setNumber}세트로`}
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
  return (
    <div className={`${styles.done} ${place}`}>
      {origin ? (
        <button type="button" className={styles.doneOpen} onClick={goNext}>
          휴식 끝 · {origin.exerciseName} {origin.setNumber}세트 ›
        </button>
      ) : (
        <span className={styles.doneOpen}>휴식 끝 · 다음 세트 시작</span>
      )}
      <button type="button" className={styles.doneClose} onClick={dismiss} aria-label="휴식 끝 알림 닫기">
        ✕
      </button>
    </div>
  )
}
