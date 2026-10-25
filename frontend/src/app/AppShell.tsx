import { Outlet } from 'react-router-dom'
import { RestBar, TabBar } from '../components'
import styles from './AppShell.module.css'

/**
 * 하단 탭이 있는 화면들의 공통 레이아웃. 탭 5개와 둘러보는 화면(종목·설정)이 쓴다.
 * 하던 일이 있는 화면(기록·루틴·피드백)은 이걸 쓰지 않는다.
 */
export function AppShell() {
  return (
    <div className={styles.root}>
      <main className={styles.content}>
        <Outlet />
      </main>
      <TabBar />
      {/* 기록 중에 종목 상세를 보러 와도 쉬는 시간이 보이게 */}
      <RestBar aboveTabBar />
    </div>
  )
}
