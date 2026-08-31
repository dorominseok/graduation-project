import { Outlet } from 'react-router-dom'
import styles from './FullScreenLayout.module.css'

/** 탭 없이 화면 전체를 쓰는 경로들의 레이아웃. */
export function FullScreenLayout() {
  return (
    <div className={styles.root}>
      <Outlet />
    </div>
  )
}
