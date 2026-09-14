import styles from './Chevron.module.css'

/** 목록 행 오른쪽의 ">" 표시. 눌러서 들어가는 행이라는 신호. */
export function Chevron() {
  return (
    <svg
      className={styles.root}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M9 5l7 7-7 7"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
