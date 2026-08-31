import styles from './Placeholder.module.css'

/**
 * 아직 만들지 않은 화면의 자리표시자.
 * 홈·기록·분석·그룹·프로필 본문은 9월에 백엔드 기능과 짝지어 채운다.
 */
export function Placeholder({ title, note }: { title: string; note?: string }) {
  return (
    <div className={styles.root}>
      <div className={styles.title}>{title}</div>
      <div className={styles.note}>{note ?? '9월에 백엔드 기능과 함께 구현한다.'}</div>
    </div>
  )
}
