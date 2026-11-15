import styles from './settings.module.css'

/** 설정 화면의 켜고 끄는 줄. 행 전체가 스위치다 */
export function SwitchRow({
  label,
  on,
  disabled,
  onToggle,
}: {
  label: string
  on: boolean
  disabled?: boolean
  onToggle: () => void
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      className={`${styles.row} ${styles.switchRow}`}
      disabled={disabled}
      onClick={onToggle}
    >
      <span className={styles.rowLabel}>{label}</span>
      <span className={`${styles.switch} ${on ? styles.switchOn : ''}`} aria-hidden="true" />
    </button>
  )
}
