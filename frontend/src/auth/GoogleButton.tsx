import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { googleEnabled, renderGoogleButton } from './google'
import type { GoogleButtonText } from './google'
import styles from './GoogleButton.module.css'

interface Props {
  /** 구글 창에서 계정을 고르면 ID 토큰이 온다. 서버로 넘기는 건 부르는 쪽 일이다. */
  onCredential: (credential: string) => void
  text?: GoogleButtonText
}

/** 구글이 그리는 로그인 버튼. 클라이언트 ID가 없으면 아무것도 그리지 않는다. */
export function GoogleButton({ onCredential, text = 'continue_with' }: Props) {
  const slot = useRef<HTMLDivElement>(null)
  const [failed, setFailed] = useState(false)
  const handle = useEffectEvent((credential: string) => onCredential(credential))

  useEffect(() => {
    if (!slot.current) return
    renderGoogleButton(slot.current, (credential) => handle(credential), text).catch(() =>
      setFailed(true),
    )
  }, [text])

  if (!googleEnabled) return null

  return (
    <div className={styles.wrap}>
      {/* 구글 버튼이 그려지기 전에도 높이를 잡아 둔다 — 그려지는 순간 아래가 밀리지 않게 */}
      <div ref={slot} className={styles.slot} />
      {failed && <div className={styles.failed}>구글 로그인을 불러오지 못했어요. 네트워크를 확인해주세요.</div>}
    </div>
  )
}
