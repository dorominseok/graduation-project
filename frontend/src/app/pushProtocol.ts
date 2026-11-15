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

/** 앱 안 경로만 받는다. 다른 주소나 이상한 값이면 홈 */
export function safeAppPath(url: unknown): string {
  return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') ? url : '/'
}
