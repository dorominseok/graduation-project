import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  authApi,
  clearAccessToken,
  refreshAccessToken,
  setAccessToken,
  setAuthExpiredListener,
} from '../api'
import type { MeResponse } from '../api'
import { AuthContext } from './auth-context'
import type { AuthStatus } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<MeResponse | null>(null)

  /**
   * 앱이 뜰 때 세션을 복구한다.
   *
   * 액세스 토큰은 메모리에만 있어서 새로고침하면 없다. 리프레시 쿠키가 살아 있으면
   * 재발급으로 되살아나고, 없으면 그냥 비로그인이다(명세 4.3).
   *
   * StrictMode가 개발 중 이 효과를 두 번 실행하지만, 재발급은 single-flight라
   * 요청은 한 번만 나간다.
   */
  useEffect(() => {
    let cancelled = false

    const bootstrap = async () => {
      try {
        await refreshAccessToken()
        const me = await authApi.getMe()
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      } catch {
        if (cancelled) return
        setUser(null)
        setStatus('anonymous')
      }
    }

    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  /**
   * 재발급이 끝내 실패하면 API 클라이언트가 알려준다.
   *
   * 여기서 화면을 옮기지는 않는다 — 상태만 `anonymous`로 바꾸면 RequireAuth가
   * 로그인으로 보낸다. 라우터 밖에 있는 컴포넌트라 navigate를 쓸 수도 없다.
   */
  useEffect(() => {
    setAuthExpiredListener(() => {
      clearAccessToken()
      setUser(null)
      setStatus('anonymous')
    })
    return () => setAuthExpiredListener(null)
  }, [])

  /**
   * 로그인·가입 응답은 축약 사용자 정보만 담는다(명세 4.1). 프로필까지 필요하므로
   * 토큰을 넣고 곧바로 `GET /users/me`를 부른다. 사용자 모양을 한 가지로 유지하려는 것.
   */
  const completeSignIn = useCallback(async (accessToken: string) => {
    setAccessToken(accessToken)
    const me = await authApi.getMe()
    setUser(me)
    setStatus('authenticated')
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await authApi.login({ email, password })
      await completeSignIn(res.accessToken)
    },
    [completeSignIn],
  )

  const signUp = useCallback(
    async (email: string, password: string, nickname: string) => {
      const res = await authApi.signUp({ email, password, nickname })
      await completeSignIn(res.accessToken)
    },
    [completeSignIn],
  )

  /**
   * 서버 호출이 실패해도 화면은 로그아웃시킨다.
   *
   * 사용자가 로그아웃을 눌렀는데 로그인 상태로 남아 있는 쪽이 더 나쁘다.
   * 토큰은 `authApi.logout`이 finally에서 지우므로 여기서는 화면 상태만 맞춘다.
   */
  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const applyUser = useCallback((next: MeResponse) => setUser(next), [])

  const resetSession = useCallback(() => {
    clearAccessToken()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo(
    () => ({ status, user, login, signUp, logout, applyUser, resetSession }),
    [status, user, login, signUp, logout, applyUser, resetSession],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
