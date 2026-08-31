import { createContext, use } from 'react'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface ToastOptions {
  message: string
  /** 되돌리기 같은 단일 동작. 누르면 토스트는 닫힌다. */
  action?: ToastAction
  tone?: 'default' | 'danger'
  /** 자동으로 닫히기까지의 시간(ms). */
  durationMs?: number
}

export interface ToastContextValue {
  showToast: (options: ToastOptions) => void
  dismissToast: (id: number) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

export function useToast(): ToastContextValue {
  const ctx = use(ToastContext)
  if (!ctx) throw new Error('useToast는 ToastProvider 안에서만 쓸 수 있다')
  return ctx
}
