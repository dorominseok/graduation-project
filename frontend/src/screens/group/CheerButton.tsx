import { useState } from 'react'
import { groupApi, isApiError } from '../../api'
import type { CheerState } from '../../api'
import { useToast } from '../../components'
import styles from './group.module.css'

interface CheerButtonProps {
  groupId: number
  sessionId: number
  initial: CheerState
  /** 내 운동이면 누를 수 없고 받은 수만 보인다 */
  mine?: boolean
}

/** 따봉. 눌렀으면 채운다 */
function ThumbsUp({ filled }: { filled: boolean }) {
  return (
    <svg className={styles.cheerIcon} viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M7 10v11"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      />
      <path
        d="M15 5.9 14 10h5.8a2 2 0 0 1 1.9 2.6l-2.3 8a2 2 0 0 1-1.9 1.4H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.8a2 2 0 0 0 1.8-1.1L12 2a3.1 3.1 0 0 1 3 3.9Z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * 응원 버튼. 한 사람이 한 번이고, 다시 누르면 취소한다(LOG-38).
 *
 * <p>누르는 즉시 숫자를 바꾸고 서버 응답으로 맞춘다. 응답을 기다렸다 바꾸면 눌렀는지
 * 헷갈려 두 번 누르게 된다. 실패하면 되돌린다.
 */
export function CheerButton({ groupId, sessionId, initial, mine = false }: CheerButtonProps) {
  const { showToast } = useToast()
  const [state, setState] = useState(initial)
  const [busy, setBusy] = useState(false)

  if (mine) {
    // 받은 응원이 없으면 아무것도 보이지 않는다 — "0"은 아무 말도 하지 않는다
    if (state.cheerCount === 0) return null
    return (
      <span className={`${styles.cheer} ${styles.cheerStatic}`} aria-label={`응원 ${state.cheerCount}개`}>
        <ThumbsUp filled />
        {state.cheerCount}
      </span>
    )
  }

  const toggle = async () => {
    if (busy) return
    const before = state
    const next = before.cheeredByMe
      ? { cheerCount: Math.max(0, before.cheerCount - 1), cheeredByMe: false }
      : { cheerCount: before.cheerCount + 1, cheeredByMe: true }
    setState(next)
    setBusy(true)
    try {
      setState(
        before.cheeredByMe
          ? await groupApi.cancelCheer(groupId, sessionId)
          : await groupApi.cheer(groupId, sessionId),
      )
    } catch (err) {
      setState(before)
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      type="button"
      className={`${styles.cheer} ${state.cheeredByMe ? styles.cheerOn : ''}`}
      onClick={() => void toggle()}
      aria-pressed={state.cheeredByMe}
      aria-label={state.cheeredByMe ? '응원 취소' : '응원하기'}
    >
      <ThumbsUp filled={state.cheeredByMe} />
      {/* 아직 아무도 누르지 않았으면 따봉만 둔다 */}
      {state.cheerCount > 0 && state.cheerCount}
    </button>
  )
}
