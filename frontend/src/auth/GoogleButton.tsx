import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { useToast } from '../components'
import { inApp, openInBrowser } from '../app/inAppBrowser'
import type { InAppBrowser } from '../app/inAppBrowser'
import { googleEnabled, renderGoogleButton } from './google'
import type { GoogleButtonText } from './google'
import styles from './GoogleButton.module.css'

interface Props {
  /** 구글 창에서 계정을 고르면 ID 토큰이 온다. 서버로 넘기는 건 부르는 쪽 일이다. */
  onCredential: (credential: string) => void
  text?: GoogleButtonText
}

/**
 * 구글이 그리는 로그인 버튼. 클라이언트 ID가 없으면 아무것도 그리지 않는다.
 * 카톡 같은 앱 안 브라우저에서는 구글이 로그인을 막으므로 버튼 대신 바깥 브라우저로 여는 안내를 띄운다.
 */
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
  if (inApp) return <InAppNotice app={inApp} />

  return (
    <div className={styles.wrap}>
      {/* 구글 버튼이 그려지기 전에도 높이를 잡아 둔다 — 그려지는 순간 아래가 밀리지 않게 */}
      <div ref={slot} className={styles.slot} />
      {failed && <div className={styles.failed}>구글 로그인을 불러오지 못했어요. 네트워크를 확인해주세요.</div>}
    </div>
  )
}

/** 앱 안 브라우저 안내. 넘길 수 있으면 "브라우저로 열기", 어디서든 "주소 복사" */
function InAppNotice({ app }: { app: InAppBrowser }) {
  const { showToast } = useToast()
  const [copyFailed, setCopyFailed] = useState(false)
  const canOpen = app.escapeUrl(window.location.href) !== null

  const copyAddress = async () => {
    if (await copyText(window.location.href)) {
      showToast({ message: '주소를 복사했어요. 브라우저 주소창에 붙여 넣어 주세요' })
    } else {
      setCopyFailed(true)
    }
  }

  return (
    <div className={styles.notice}>
      <p className={styles.noticeTitle}>{app.name} 안에서는 구글 로그인이 안 돼요</p>
      <p className={styles.noticeBody}>
        {canOpen
          ? '휴대폰 브라우저에서 열면 구글로 계속할 수 있어요.'
          : "오른쪽 위 메뉴(⋯)에서 '외부 브라우저로 열기'를 누르거나, 주소를 복사해 Safari에 붙여 넣어 주세요."}
      </p>
      <div className={styles.noticeButtons}>
        {canOpen && (
          <button type="button" className={styles.open} onClick={() => openInBrowser()}>
            브라우저로 열기
          </button>
        )}
        <button type="button" className={styles.copy} onClick={() => void copyAddress()}>
          주소 복사
        </button>
      </div>
      {/* 복사까지 막힌 곳이면 주소를 보여 줘서 길게 눌러 복사하게 한다 */}
      {copyFailed && <p className={styles.address}>{window.location.href}</p>}
    </div>
  )
}

async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 앱 안 브라우저는 클립보드 API를 막아 두기도 한다. 옛 방식으로 한 번 더
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    area.remove()
    return ok
  } catch {
    return false
  }
}
