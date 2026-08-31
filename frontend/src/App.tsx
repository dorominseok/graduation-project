import { RouterProvider } from 'react-router-dom'
import { ToastProvider } from './components'
import { router } from './app/router'

export default function App() {
  return (
    <ToastProvider>
      <RouterProvider router={router} />
    </ToastProvider>
  )
}
