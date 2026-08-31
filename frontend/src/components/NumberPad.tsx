import { useState } from 'react'
import { BottomSheet } from './BottomSheet'
import styles from './NumberPad.module.css'

const BACKSPACE = '⌫'
const DOT = '.'
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', DOT, '0', BACKSPACE]

interface NumberPadProps {
  open: boolean
  onClose: () => void
  /** 확인을 누르면 파싱된 숫자를 넘긴다. */
  onApply: (value: number) => void
  /** 시트 상단에 뜨는 설명. 예: '벤치프레스 · 중량' */
  label?: string
  initialValue?: number
  unit?: string
  /** 소수 입력 허용 여부. 횟수·시간은 정수만 받는다. */
  allowDecimal?: boolean
}

/**
 * 숫자 입력 패드. 세트의 중량·횟수·시간을 큰 타깃으로 입력받는다.
 *
 * 값은 문자열 버퍼로 다룬다 — 입력 중간의 '60.'처럼 숫자로 표현할 수 없는
 * 상태가 존재하기 때문이다. 확정 시점에만 숫자로 바꾼다.
 */
export function NumberPad({ open, onClose, label, ...bodyProps }: NumberPadProps) {
  return (
    <BottomSheet open={open} onClose={onClose} title={label}>
      {/* 시트가 닫히면 아래 본문이 언마운트되므로, 다음에 열 때
          버퍼가 initialValue로 새로 시작한다. 별도 초기화가 필요 없다. */}
      <NumberPadBody {...bodyProps} onClose={onClose} />
    </BottomSheet>
  )
}

type NumberPadBodyProps = Omit<NumberPadProps, 'open' | 'label'>

function NumberPadBody({
  onClose,
  onApply,
  initialValue = 0,
  unit,
  allowDecimal = false,
}: NumberPadBodyProps) {
  const [buffer, setBuffer] = useState(() => String(initialValue))

  const press = (key: string) => {
    setBuffer((prev) => {
      if (key === BACKSPACE) {
        const next = prev.slice(0, -1)
        return next === '' ? '0' : next
      }
      if (key === DOT) {
        return prev.includes(DOT) ? prev : prev + DOT
      }
      // 선행 0은 대체한다. '0' 다음에 5를 누르면 '05'가 아니라 '5'.
      if (prev === '0') return key
      return prev + key
    })
  }

  const parsed = Number(buffer)
  const isValid = Number.isFinite(parsed)

  return (
    <>
      <div className={styles.buffer}>
        {buffer}
        {unit && <span className={styles.unit}>{unit}</span>}
      </div>
      <div className={styles.grid}>
        {KEYS.map((key) => (
          <button
            key={key}
            type="button"
            className={styles.key}
            onClick={() => press(key)}
            disabled={key === DOT && !allowDecimal}
          >
            {key}
          </button>
        ))}
      </div>
      <button
        type="button"
        className={styles.apply}
        disabled={!isValid}
        onClick={() => {
          onApply(parsed)
          onClose()
        }}
      >
        완료
      </button>
    </>
  )
}
