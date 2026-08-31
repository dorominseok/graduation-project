import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import styles from './ScreenHeader.module.css'

interface ScreenHeaderProps {
  title: ReactNode
  /** 뒤로가기를 숨긴다. 탭 안 최상위 화면에서 쓴다. */
  hideBack?: boolean
  onBack?: () => void
  trailing?: ReactNode
}

/** 전체 화면 경로의 상단 헤더. 뒤로가기는 기본적으로 히스토리를 되돌린다. */
export function ScreenHeader({ title, hideBack, onBack, trailing }: ScreenHeaderProps) {
  const navigate = useNavigate()

  return (
    <header className={`${styles.root} ${hideBack ? styles.noBack : ''}`}>
      {!hideBack && (
        <button
          type="button"
          className={styles.back}
          onClick={() => (onBack ? onBack() : navigate(-1))}
          aria-label="뒤로"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M15 5l-7 7 7 7"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      )}
      <div className={styles.title}>{title}</div>
      <div className={styles.trailing}>{trailing}</div>
    </header>
  )
}
