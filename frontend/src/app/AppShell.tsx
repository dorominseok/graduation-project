import { Outlet } from 'react-router-dom'
import { TabBar } from '../components'
import styles from './AppShell.module.css'

/** 하단 탭이 있는 화면들의 공통 레이아웃. 탭 밖 전체 화면은 이걸 쓰지 않는다. */
export function AppShell() {
  return (
    <div className={styles.root}>
      <main className={styles.content}>
        <Outlet />
      </main>
      <TabBar />
    </div>
  )
}
