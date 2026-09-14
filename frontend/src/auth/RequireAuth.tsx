import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { paths } from '../app/paths'
import { useAuth } from './auth-context'

/**
 * 로그인이 필요한 경로를 감싸는 가드.
 *
 * 서버 쪽 `SecurityConfig`가 최종 방어선이고(명세 1.3), 이건 화면이 401을 받고
 * 나서야 반응하는 걸 막기 위한 것이다. 목록은 서버와 맞춰둘 필요가 없다 —
 * 어차피 보호 경로에 들어가면 API가 거절한다.
 */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  // 부트스트랩이 끝나기 전에는 판단하지 않는다. 여기서 로그인으로 보내면
  // 새로고침할 때마다 이미 로그인한 사용자가 튕긴다.
  if (status === 'loading') return null

  if (status === 'anonymous') {
    // 로그인 후 원래 가려던 곳으로 돌려보내려고 위치를 남긴다.
    return <Navigate to={paths.login} state={{ from: location }} replace />
  }

  return <Outlet />
}
