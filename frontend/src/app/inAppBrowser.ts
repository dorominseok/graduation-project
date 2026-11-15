/**
 * 카카오톡·인스타그램 같은 앱 안 브라우저.
 *
 * <p>구글은 앱 안 브라우저에서 로그인을 막는다(403 disallowed_useragent — 그 앱이 입력하는 비밀번호를
 * 엿볼 수 있어서). 초대 링크는 대부분 카톡으로 오므로, 앱에 들어오자마자 같은 주소를 휴대폰의 기본
 * 브라우저로 다시 연다. 넘길 방법이 없는 곳(아이폰의 인스타그램 등)은 구글 버튼 자리에 안내를 띄운다.
 */

export interface InAppBrowser {
  /** 안내 문구에 쓰는 이름 */
  name: string
  /** 같은 주소를 바깥 브라우저로 여는 주소. 넘길 방법이 없으면 null */
  escapeUrl: (href: string) => string | null
}

const ANDROID = /Android/i.test(navigator.userAgent)

/** 카카오톡이 공식으로 지원하는 "기본 브라우저로 열기" */
function kakao(href: string): string {
  return `kakaotalk://web/openExternal?url=${encodeURIComponent(href)}`
}

/** 라인은 주소에 이 값이 붙어 있으면 바깥 브라우저로 연다. 넘어간 뒤에는 앱이 떼어 낸다(cleanEscapeMark) */
const LINE_MARK = 'openExternalBrowser'
function line(href: string): string {
  const url = new URL(href)
  url.searchParams.set(LINE_MARK, '1')
  return url.toString()
}

/** 안드로이드는 대부분의 앱 안 브라우저에서 "크롬으로 열기" 주소가 통한다. 아이폰은 이런 길이 없다 */
function androidChrome(href: string): string | null {
  if (!ANDROID) return null
  const url = new URL(href)
  return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=${url.protocol.replace(':', '')};package=com.android.chrome;end`
}

/** 이름표(User-Agent)에 남기는 표시. 위에서부터 먼저 맞는 것 */
const KNOWN: { mark: RegExp; name: string; escapeUrl: (href: string) => string | null }[] = [
  { mark: /KAKAOTALK/i, name: '카카오톡', escapeUrl: kakao },
  { mark: /\bLine\//, name: '라인', escapeUrl: line },
  { mark: /Instagram/i, name: '인스타그램', escapeUrl: androidChrome },
  { mark: /FBAN|FBAV|FB_IAB/, name: '페이스북', escapeUrl: androidChrome },
  { mark: /NAVER\(inapp/i, name: '네이버 앱', escapeUrl: androidChrome },
  { mark: /DaumApps/i, name: '다음 앱', escapeUrl: androidChrome },
  { mark: /BAND\//, name: '밴드', escapeUrl: androidChrome },
  { mark: /everytimeApp/i, name: '에브리타임', escapeUrl: androidChrome },
  // 안드로이드 WebView 공통 표시. 홈 화면에 설치한 우리 앱은 크롬으로 돌아서 여기 걸리지 않는다.
  // 아이폰은 홈 화면 앱도 이름표가 WebView와 비슷해 공통 표시로 가리지 않는다
  { mark: /; wv\)/, name: '이 앱', escapeUrl: androidChrome },
]

export function detectInApp(ua: string = navigator.userAgent): InAppBrowser | null {
  const found = KNOWN.find((app) => app.mark.test(ua))
  return found ? { name: found.name, escapeUrl: found.escapeUrl } : null
}

/** 이 브라우저. 이름표는 바뀌지 않으므로 한 번만 본다 */
export const inApp: InAppBrowser | null = detectInApp()

/** 지금 주소를 바깥 브라우저로 연다. 넘길 방법이 없으면 false */
export function openInBrowser(): boolean {
  const target = inApp?.escapeUrl(window.location.href)
  if (!target) return false
  window.location.href = target
  return true
}

const TRIED_KEY = 'balancefit.inapp-escape'

/**
 * 앱에 들어오자마자 한 번 바깥 브라우저로 넘긴다. 같은 창에서 다시 열거나 돌아와도 또 넘기지 않는다 —
 * 넘기기가 안 되는 기기에서 계속 튕기지 않게. 그다음은 구글 버튼 자리의 "브라우저로 열기"로 한다.
 */
export function escapeInAppOnce(): void {
  if (!inApp) return
  try {
    if (sessionStorage.getItem(TRIED_KEY)) return
    sessionStorage.setItem(TRIED_KEY, '1')
  } catch {
    // 남기지 못하면 열 때마다 넘긴다. 넘어가면 이 창은 더 쓰지 않는다
  }
  openInBrowser()
}

/** 라인에서 넘어오며 주소에 붙은 표시를 뗀다. 화면 이동 없이 주소창만 고친다 */
export function cleanEscapeMark(): void {
  if (inApp) return
  const url = new URL(window.location.href)
  if (!url.searchParams.has(LINE_MARK)) return
  url.searchParams.delete(LINE_MARK)
  window.history.replaceState(window.history.state, '', url.toString())
}
