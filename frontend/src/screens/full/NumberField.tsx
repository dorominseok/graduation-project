import { useState } from 'react'

interface NumberFieldProps {
  value: number | null
  /** 중량처럼 소수가 있는 값. 키보드도 소수점 있는 숫자 키보드가 뜬다 */
  allowDecimal?: boolean
  /** 바뀐 값만 넘어온다. 비우거나 숫자가 아니면 원래 값으로 돌아가고 부르지 않는다 */
  onCommit: (value: number) => void
  className?: string
  'aria-label'?: string
}

/**
 * 세트 값 입력칸. 누르면 휴대폰의 기본 키보드(사용자가 쓰는 쿼티·천지인 등의 숫자 화면)가 뜬다.
 *
 * <p>직접 만든 숫자판을 쓰지 않는 이유 — 사람마다 손에 익은 키보드가 있고, 화면 절반을 가리는
 * 시트보다 그 자리에서 고치는 편이 빠르다. 입력 중에는 글자로만 들고 있다가, 키보드의 완료나
 * 칸 밖을 누를 때 숫자로 바꿔 저장한다. 그래야 "12." 같은 쓰는 도중의 값이 저장되지 않는다.
 */
/** 12.50 → "12.5", 60 → "60". 소수 둘째 자리까지(서버 NUMERIC(6,2)와 같다) */
function format(value: number): string {
  return String(Math.round(value * 100) / 100)
}

export function NumberField({ value, allowDecimal = false, onCommit, className, ...rest }: NumberFieldProps) {
  // 입력 중일 때만 글자를 들고 있다. 그 밖에는 바깥 값(−/+ 버튼으로 바뀐 값 포함)을 그대로 보인다
  const [draft, setDraft] = useState<string | null>(null)
  const shown = draft ?? (value == null ? '' : format(value))

  const commit = () => {
    if (draft === null) return
    setDraft(null)
    const text = draft.trim().replace(',', '.')
    if (text === '') return
    const parsed = Number(text)
    if (!Number.isFinite(parsed) || parsed < 0) return
    const next = allowDecimal ? Math.round(parsed * 100) / 100 : Math.round(parsed)
    if (next !== value) onCommit(next)
  }

  return (
    <input
      type="text"
      inputMode={allowDecimal ? 'decimal' : 'numeric'}
      enterKeyHint="done"
      autoComplete="off"
      className={className}
      value={shown}
      placeholder="–"
      onFocus={(e) => {
        setDraft(value == null ? '' : format(value))
        // 누르자마자 새 값을 칠 수 있게 원래 값을 골라 둔다
        e.currentTarget.select()
      }}
      onChange={(e) =>
        setDraft(allowDecimal ? e.target.value.replace(/[^0-9.,]/g, '') : e.target.value.replace(/\D/g, ''))
      }
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') e.currentTarget.blur()
      }}
      {...rest}
    />
  )
}
