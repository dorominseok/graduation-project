import { RouterProvider } from 'react-router-dom'
import { ToastProvider } from './components'
import { AuthProvider } from './auth'
import { router } from './app/router'

export default function App() {
  return (
    <ToastProvider>
      {/* 라우터 밖에 둔다 — RequireAuth가 라우트 안에서 이 상태를 읽는다. */}
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </ToastProvider>
  )
}
