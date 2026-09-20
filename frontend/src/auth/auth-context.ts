import { createContext, use } from 'react'
import type { MeResponse } from '../api'

/**
 * 인증 상태.
 *
 * `loading`이 따로 있는 이유는 부트스트랩 때문이다. 액세스 토큰은 메모리에만 있어
 * 새로고침하면 사라지고, 리프레시 쿠키로 복구되기까지 한 번의 왕복이 걸린다.
 * 그 사이를 `anonymous`로 보면 이미 로그인한 사용자가 로그인 화면으로 튕긴다.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: MeResponse | null
  login: (email: string, password: string) => Promise<void>
  signUp: (email: string, password: string, nickname: string) => Promise<void>
  logout: () => Promise<void>
  /**
   * 서버 호출 없이 화면 상태만 비운다.
   *
   * 탈퇴처럼 계정 자체가 사라져 로그아웃을 부를 대상이 없을 때 쓴다.
   */
  resetSession: () => void
  /** 프로필 수정 후 화면 상태를 맞추기 위해. 서버 응답을 그대로 넘긴다. */
  applyUser: (user: MeResponse) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있다')
  return ctx
}
