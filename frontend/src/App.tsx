import type { ReactNode } from 'react'
import { RouterProvider } from 'react-router-dom'
import { RestTimerProvider, ToastProvider } from './components'
import { AuthProvider, useAuth } from './auth'
import { router } from './app/router'

/** 휴식 타이머는 로그인한 사용자 것이다. 로그아웃 뒤 다른 계정에 남은 시간이 보이지 않게 */
function RestTimerForUser({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return <RestTimerProvider userId={user?.userId ?? null}>{children}</RestTimerProvider>
}

export default function App() {
  return (
    <ToastProvider>
      {/* 라우터 밖에 둔다 — RequireAuth가 라우트 안에서 이 상태를 읽는다. */}
      <AuthProvider>
        {/* 휴식 타이머는 화면을 옮겨도 이어져야 해서 라우터보다 바깥에 둔다 */}
        <RestTimerForUser>
          <RouterProvider router={router} />
        </RestTimerForUser>
      </AuthProvider>
    </ToastProvider>
  )
}
