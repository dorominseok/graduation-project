import { formatRest } from '../app/dates'
import { useRestTimer } from './rest-timer-context'
import styles from './RestBar.module.css'

const STEP_SEC = 30

/**
 * 휴식 띠. 본문 위에 얹어 스크롤과 무관하게 같은 자리에 둔다.
 *
 * @param aboveTabBar 하단 탭이 있는 화면이면 탭 위에 띄운다
 */
export function RestBar({ aboveTabBar = false }: { aboveTabBar?: boolean }) {
  const { rest, done, adjust, skip } = useRestTimer()
  const place = aboveTabBar ? styles.aboveTabBar : ''

  if (rest) {
    return (
      <div className={`${styles.rest} ${place}`}>
        <span className={styles.time}>{formatRest(rest.left)}</span>
        <div className={styles.main}>
          <div className={styles.label}>휴식 중</div>
          <div className={styles.track}>
            <div className={styles.bar} style={{ width: `${Math.round((rest.left / rest.total) * 100)}%` }} />
          </div>
        </div>
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
  return done ? <div className={`${styles.done} ${place}`}>휴식 끝 · 다음 세트 시작</div> : null
}
