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
import * as push from '../app/push'
import { AuthContext } from './auth-context'
import type { AuthStatus } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<MeResponse | null>(null)

  /**
   * 앱이 뜰 때 세션을 복구한다. 리프레시 쿠키가 살아 있으면 재발급으로 되살아나고,
   * 없으면 비로그인이다. StrictMode가 개발 중 두 번 실행해도 재발급은 single-flight라
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
   * 같은 브라우저에서 다른 계정이 켜 둔 알림 구독이 남아 있으면 버린다 — 앞 계정의 알림이 이 기기로
   * 오지 않게. 로그아웃 없이 로그인이 만료된 뒤 다른 계정이 들어온 경우다(`app/push.ts`).
   */
  const userId = user?.userId
  useEffect(() => {
    if (userId !== undefined) void push.releaseIfOtherUser(userId).catch(() => undefined)
  }, [userId])

  /**
   * 로그인·가입 응답은 축약 사용자 정보만 담는다. 프로필까지 필요하므로
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

  const loginWithGoogle = useCallback(
    async (credential: string) => {
      const res = await authApi.googleLogin(credential)
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
   *
   * 이 기기의 알림도 끈다 — 로그아웃한 계정의 알림이 이 기기로 오지 않게. 토큰이 살아 있을 때
   * 서버에서 지워야 해서 먼저 하고, 느려도 로그아웃을 붙잡지 않게 3초까지만 기다린다.
   */
  const logout = useCallback(async () => {
    try {
      await Promise.race([
        push.disable({ tellServer: true }).catch(() => undefined),
        new Promise((resolve) => setTimeout(resolve, 3000)),
      ])
      await authApi.logout()
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const applyUser = useCallback((next: MeResponse) => setUser(next), [])

  /** 탈퇴 뒤처럼 서버에 부를 대상이 없을 때. 알림 구독은 서버가 계정과 함께 지웠으니 브라우저 쪽만 지운다 */
  const resetSession = useCallback(() => {
    void push.disable({ tellServer: false }).catch(() => undefined)
    clearAccessToken()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo(
    () => ({ status, user, login, loginWithGoogle, signUp, logout, applyUser, resetSession }),
    [status, user, login, loginWithGoogle, signUp, logout, applyUser, resetSession],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
