/**
 * 액세스 토큰 보관소. **메모리에만 둔다** (명세 2.3).
 *
 * localStorage를 쓰지 않으므로 새로고침하면 사라진다. 사라져도 리프레시 쿠키가
 * 남아 있어 앱이 뜰 때 `POST /auth/refresh`로 곧바로 복구되므로(4.3),
 * 사용자 입장에서 로그인은 끊기지 않는다 — AuthProvider의 부트스트랩이 그 일을 한다.
 *
 * 모듈 변수 하나로 두는 이유는 API 클라이언트가 리액트 밖에서 동작해야 하기
 * 때문이다. 상태를 리액트에 두면 fetch 래퍼가 훅에 묶인다.
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
