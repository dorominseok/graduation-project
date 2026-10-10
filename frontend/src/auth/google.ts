/**
 * 구글 로그인(Google Identity Services, LOG-37).
 *
 * 버튼은 구글이 그린다. 구글 로그인 브랜드 규정상 모양을 직접 만들지 않고, 그 버튼이 띄운
 * 창에서 받은 ID 토큰(`credential`)을 서버로 넘기기만 한다. 검증과 가입·로그인 판단은 서버 일이다.
 *
 * 클라이언트 ID는 공개값이라 `.env.development`·`.env.production`에 둔다. 비어 있으면 버튼을 그리지 않는다.
 */

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined

export const googleEnabled = Boolean(CLIENT_ID)

/** 버튼 문구. `continue_with` = "Google 계정으로 계속하기". */
export type GoogleButtonText = 'continue_with' | 'signup_with' | 'signin_with'

interface GsiId {
  initialize(config: {
    client_id: string
    callback: (response: { credential: string }) => void
    ux_mode?: 'popup' | 'redirect'
    auto_select?: boolean
  }): void
  renderButton(
    parent: HTMLElement,
    options: {
      type?: 'standard' | 'icon'
      theme?: 'outline' | 'filled_blue' | 'filled_black'
      size?: 'large' | 'medium' | 'small'
      shape?: 'rectangular' | 'pill'
      text?: GoogleButtonText
      width?: number
      locale?: string
    },
  ): void
}

declare global {
  interface Window {
    google?: { accounts: { id: GsiId } }
  }
}

let loading: Promise<GsiId> | null = null

/** 구글 스크립트는 로그인 화면에 처음 들어올 때 한 번만 받는다. 앱 전체 첫 로딩에 얹지 않는다. */
function loadGsi(): Promise<GsiId> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  loading ??= new Promise<GsiId>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.onload = () =>
      window.google?.accounts?.id ? resolve(window.google.accounts.id) : reject(new Error('gsi'))
    script.onerror = () => {
      loading = null // 네트워크가 돌아오면 다시 시도할 수 있게
      script.remove()
      reject(new Error('구글 스크립트를 불러오지 못했다'))
    }
    document.head.appendChild(script)
  })
  return loading
}

/**
 * 구글 SDK는 `initialize`를 한 번만 부르게 되어 있어, 콜백은 지금 화면에 떠 있는 버튼의
 * 것으로 갈아 끼운다. 한 화면에 구글 버튼은 하나뿐이다.
 */
let currentHandler: ((credential: string) => void) | null = null
let initialized = false

export async function renderGoogleButton(
  parent: HTMLElement,
  onCredential: (credential: string) => void,
  text: GoogleButtonText,
): Promise<void> {
  if (!CLIENT_ID) return
  const gsi = await loadGsi()
  if (!initialized) {
    gsi.initialize({
      client_id: CLIENT_ID,
      callback: (response) => currentHandler?.(response.credential),
      ux_mode: 'popup',
      auto_select: false,
    })
    initialized = true
  }
  currentHandler = onCredential
  gsi.renderButton(parent, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    shape: 'pill',
    text,
    // 구글 버튼은 200~400px만 받는다
    width: Math.max(200, Math.min(400, parent.clientWidth)),
    locale: 'ko',
  })
}
