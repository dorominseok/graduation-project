import { useState } from 'react'

interface RestFieldProps {
  /** 휴식 시간(초) */
  value: number
  onCommit: (seconds: number) => void
  className?: string
  'aria-label'?: string
}

/** 휴식은 30분이면 충분하다. 잘못 친 큰 숫자가 몇 시간짜리 타이머가 되지 않게 막는다 */
const MAX_REST_SEC = 30 * 60

/** 90 → "1:30" */
function format(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** 90 → "130". 입력칸을 눌렀을 때 보이는 숫자만의 모양 */
function toDigits(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const rest = String(seconds % 60).padStart(2, '0')
  return minutes > 0 ? `${minutes}${rest}` : String(seconds % 60)
}

/**
 * 친 숫자를 초로. 전자레인지 타이머처럼 읽는다 — 휴대폰 숫자 키보드에는 ":"가 없는 경우가 많다.
 * 두 자리 이하는 초(90 → 90초), 세 자리 이상은 끝 두 자리가 초(130 → 1분 30초, 200 → 2분).
 */
function parse(digits: string): number | null {
  if (digits === '') return null
  const seconds =
    digits.length <= 2
      ? Number(digits)
      : Number(digits.slice(0, -2)) * 60 + Number(digits.slice(-2))
  return Number.isFinite(seconds) ? Math.min(seconds, MAX_REST_SEC) : null
}

/**
 * 휴식 시간 입력칸. 누르면 휴대폰의 기본 숫자 키보드가 뜬다. 보일 때는 "1:30",
 * 고칠 때는 숫자만("130")이다. 완료나 칸 밖을 누를 때 저장하고, 비우면 원래 값으로 돌아간다.
 */
export function RestField({ value, onCommit, className, ...rest }: RestFieldProps) {
  const [draft, setDraft] = useState<string | null>(null)

  const commit = () => {
    if (draft === null) return
    setDraft(null)
    const next = parse(draft)
    if (next !== null && next !== value) onCommit(next)
  }

  return (
    <input
      type="text"
      inputMode="numeric"
      enterKeyHint="done"
      autoComplete="off"
      className={className}
      value={draft ?? format(value)}
      onFocus={(e) => {
        setDraft(toDigits(value))
        e.currentTarget.select()
      }}
      onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 4))}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') e.currentTarget.blur()
      }}
      {...rest}
    />
  )
}
