import { Link } from 'react-router-dom'
import { Chevron, ScreenHeader } from '../../components'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import styles from '../full/settings.module.css'

/** 프로필 탭. 지금 로그인한 계정을 보여주고 설정으로 보낸다. */
export function ProfileScreen() {
  const { user } = useAuth()

  // RequireAuth를 지나야 닿는 화면이라 user는 채워져 있다.
  if (!user) return null

  return (
    <>
      <ScreenHeader title="프로필" hideBack />

      <div className={styles.page}>
        <div className={styles.identity}>
          <div className={styles.nickname}>{user.nickname}</div>
          <div className={styles.email}>{user.email}</div>
        </div>

        <div className={styles.section}>
          <div className={styles.card}>
            <Link to={paths.personalInfo} className={styles.row}>
              <span className={styles.rowLabel}>개인정보</span>
              <Chevron />
            </Link>
            <Link to={paths.accountSettings} className={styles.row}>
              <span className={styles.rowLabel}>계정</span>
              <Chevron />
            </Link>
            <Link to={paths.settings} className={styles.row}>
              <span className={styles.rowLabel}>설정</span>
              <Chevron />
            </Link>
          </div>
        </div>
      </div>
    </>
  )
}
