import styles from './Placeholder.module.css'

/**
 * 아직 만들지 않은 화면의 자리표시자. 화면 이름은 헤더가 보여주므로 본문은 비워 둔다.
 * 구현이 끝나면 이 컴포넌트 호출을 지운다 — 남은 개수가 곧 미구현 화면 수다.
 */
export function Placeholder() {
  return <div className={styles.root} />
}
