import { Link } from 'react-router-dom'
import { Chevron, ScreenHeader } from '../../components'
import { TRAINING_GOAL_LABEL } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import styles from '../full/settings.module.css'

/**
 * 프로필 탭. 지금 로그인한 계정을 보여주고 설정으로 보낸다.
 *
 * 프로필 항목은 훈련 목표 1종뿐이다 — 키·체중은 분석·추천 어디에도 쓰이지 않아
 * 뺐다(LOG-11·LOG-14).
 */
export function ProfileScreen() {
  const { user } = useAuth()

  // RequireAuth를 지나야 닿는 화면이라 user는 채워져 있다.
  if (!user) return null

  const goal = user.profile.goal

  return (
    <>
      <ScreenHeader title="프로필" hideBack />

      <div className={styles.page}>
        <div className={styles.identity}>
          <div className={styles.nickname}>{user.nickname}</div>
          <div className={styles.email}>{user.email}</div>
          <span className={`${styles.goalChip} ${goal ? '' : styles.goalChipEmpty}`}>
            {goal ? TRAINING_GOAL_LABEL[goal] : '훈련 목표 미설정'}
          </span>
        </div>

        <div className={styles.section}>
          <div className={styles.card}>
            <Link to={paths.personalInfo} className={styles.row}>
              <span className={styles.rowLabel}>개인정보</span>
              <span className={styles.rowValue}>
                {goal ? TRAINING_GOAL_LABEL[goal] : '미설정'}
              </span>
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
