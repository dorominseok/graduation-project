import { paths } from '../../app/paths'

/** RequireAuth가 남겨둔 "원래 가려던 곳" */
interface FromState {
  from?: { pathname?: string }
}

/** 로그인·가입 뒤에 갈 곳. 없으면 홈 */
export function redirectTarget(state: unknown): string {
  return (state as FromState | null)?.from?.pathname ?? paths.home
}

/**
 * 그룹 초대 링크를 눌렀다가 로그인 화면으로 왔는가. 그렇다면 로그인·가입 뒤 참여 화면으로
 * 이어진다는 걸 알려준다 — 처음 쓰는 사람은 링크를 눌렀는데 로그인이 나와 헷갈린다.
 */
export function cameFromInvite(state: unknown): boolean {
  return redirectTarget(state).startsWith('/join/')
}
