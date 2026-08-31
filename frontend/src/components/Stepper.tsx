import styles from './Stepper.module.css'

interface StepperProps {
  value: number
  onChange: (next: number) => void
  /** 1회 증감 폭. 중량 2.5 / 횟수 1 / 시간 5 (운동기록_방식_설계서 3.4) */
  step?: number
  min?: number
  max?: number
  /** 표시 단위. 예: 'kg', '회', '초' */
  unit?: string
  /** 소수점 자릿수. 중량 2.5 단위는 1자리가 필요하다. */
  precision?: number
  disabled?: boolean
  'aria-label'?: string
}

/**
 * 값 증감 스테퍼.
 *
 * 증감 폭은 호출부가 정한다 — 종목의 measure_type에 따라 중량 ±2.5kg,
 * 횟수 ±1회, 시간 ±5초로 달라지기 때문이다.
 */
export function Stepper({
  value,
  onChange,
  step = 1,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
  unit,
  precision = 0,
  disabled = false,
  'aria-label': ariaLabel,
}: StepperProps) {
  // 부동소수점 누적 오차를 막는다. 2.5를 반복해서 더하면 60.00000000000001이 된다.
  const clamp = (n: number) => Math.min(max, Math.max(min, Number(n.toFixed(precision))))

  const dec = () => onChange(clamp(value - step))
  const inc = () => onChange(clamp(value + step))

  // 60.0이 아니라 60으로 보여준다. 62.5는 그대로 62.5.
  const display = String(Number(value.toFixed(precision)))

  return (
    <div className={styles.root} role="group" aria-label={ariaLabel}>
      <button
        type="button"
        className={styles.button}
        onClick={dec}
        disabled={disabled || value <= min}
        aria-label={`${step} 줄이기`}
      >
        −
      </button>
      <span className={styles.value}>
        {display}
        {unit}
      </span>
      <button
        type="button"
        className={styles.button}
        onClick={inc}
        disabled={disabled || value >= max}
        aria-label={`${step} 늘리기`}
      >
        +
      </button>
    </div>
  )
}
