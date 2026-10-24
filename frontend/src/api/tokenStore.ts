/**
 * 액세스 토큰 보관소. **메모리에만 둔다**.
 *
 * 새로고침하면 사라지지만, 앱이 뜰 때 AuthProvider가 리프레시 쿠키로 곧바로 복구한다.
 * 모듈 변수로 두는 이유는 API 클라이언트가 리액트 밖에서 동작해야 해서다 — 상태를
 * 리액트에 두면 fetch 래퍼가 훅에 묶인다.
 */

let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function clearAccessToken(): void {
  accessToken = null
}
