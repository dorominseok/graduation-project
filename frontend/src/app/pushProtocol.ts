/**
 * 서비스워커(`sw.ts`)와 화면(`app/push.ts`)이 같이 쓰는 약속. 서비스워커는 화면 코드를 들이지 않고
 * 따로 빌드되므로 둘 다 여기서만 가져온다.
 */

/** 알림을 누르면 서비스워커가 열린 창에 보내는 메시지 — "이 앱 안 경로로 옮겨라" */
export const NAVIGATE_MESSAGE = 'balancefit:navigate'

export interface NavigateMessage {
  type: typeof NAVIGATE_MESSAGE
  url: string
}

/**
 * 앱 안 경로만 받는다. 다른 주소나 이상한 값이면 홈.
 *
 * 글자로 "/로 시작하는지"만 보면 안 된다 — 브라우저는 `/\evil.example`의 `\`를 `/`로 읽고 탭·줄바꿈을
 * 지우므로 둘 다 `//evil.example`(다른 사이트)이 된다. 그래서 브라우저와 같은 방식으로 주소를 풀어 출처가 이
 * 앱과 같을 때만 경로를 쓴다. `location`은 서비스워커에서도 화면에서도 이 앱의 주소다.
 */
export function safeAppPath(url: unknown): string {
  if (typeof url !== 'string') return '/'
  try {
    const parsed = new URL(url, location.origin)
    return parsed.origin === location.origin ? parsed.pathname + parsed.search + parsed.hash : '/'
  } catch {
    return '/'
  }
}
