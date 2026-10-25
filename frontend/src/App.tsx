import { RouterProvider } from 'react-router-dom'
import { RestTimerProvider, ToastProvider } from './components'
import { AuthProvider } from './auth'
import { router } from './app/router'

export default function App() {
  return (
    <ToastProvider>
      {/* 라우터 밖에 둔다 — RequireAuth가 라우트 안에서 이 상태를 읽는다. */}
      <AuthProvider>
        {/* 휴식 타이머는 화면을 옮겨도 이어져야 해서 라우터보다 바깥에 둔다 */}
        <RestTimerProvider>
          <RouterProvider router={router} />
        </RestTimerProvider>
      </AuthProvider>
    </ToastProvider>
  )
}
