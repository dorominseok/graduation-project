import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Chevron, ScreenHeader, useToast } from '../../components'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import styles from './settings.module.css'

/** 설정. 9월 범위에서는 계정 관련 진입과 로그아웃만 둔다. */
export function SettingsScreen() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const { showToast } = useToast()
  const [leaving, setLeaving] = useState(false)

  /**
   * 로그아웃. 서버가 리프레시를 폐기하고 쿠키를 만료시킨다(명세 4.4).
   *
   * 액세스 토큰은 남은 만료 시간 동안 유효하지만 메모리에서 지우므로 이 탭에서는
   * 더 쓰이지 않는다.
   */
  const handleLogout = async () => {
    if (leaving) return
    setLeaving(true)
    try {
      await logout()
      navigate(paths.login, { replace: true })
    } catch {
      // logout은 서버 호출이 실패해도 로컬 상태를 비운다. 화면만 옮기면 된다.
      showToast({ message: '로그아웃했어요' })
      navigate(paths.login, { replace: true })
    }
  }

  return (
    <>
      <ScreenHeader title="설정" />

      <div className={styles.page}>
        <div className={styles.section}>
          <div className={styles.sectionTitle}>계정</div>
          <div className={styles.card}>
            <Link to={paths.personalInfo} className={styles.row}>
              <span className={styles.rowLabel}>개인정보</span>
              <Chevron />
            </Link>
            <Link to={paths.accountSettings} className={styles.row}>
              <span className={styles.rowLabel}>계정 설정</span>
              <Chevron />
            </Link>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.card}>
            <button
              type="button"
              className={`${styles.row} ${styles.rowDanger}`}
              onClick={handleLogout}
              disabled={leaving}
            >
              <span className={styles.rowLabel}>로그아웃</span>
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
