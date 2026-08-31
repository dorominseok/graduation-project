import { useEffect } from 'react'
import type { ReactNode } from 'react'
import styles from './BottomSheet.module.css'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  title?: ReactNode
  children: ReactNode
}

/** 하단에서 올라오는 시트. 스크림을 누르거나 Esc로 닫는다. */
export function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <>
      <button
        type="button"
        className={styles.scrim}
        onClick={onClose}
        aria-label="닫기"
      />
      <div className={styles.sheet} role="dialog" aria-modal="true">
        <div className={styles.header}>
          <div className={styles.title}>{title}</div>
          <button
            type="button"
            className={styles.close}
            onClick={onClose}
            aria-label="닫기"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </>
  )
}
