import { Link, useLocation, useNavigate } from 'react-router-dom'
import { paths } from '../../app/paths'
import { isApiError } from '../../api'
import { GoogleButton, googleEnabled, useAuth } from '../../auth'
import { useToast } from '../../components'
import { cameFromInvite, redirectTarget } from './redirect'
import styles from './auth.module.css'

/**
 * 로그인 화면 — 구글 로그인만 둔다(결정 기록 D-21). 처음이면 서버가 그 자리에서 가입시키므로 가입 화면이 따로 없다.
 * 이메일·비밀번호 로그인과 가입 API는 서버에 남아 있다(이미 있는 계정·시험용). 같은 이메일로 가입했던 계정은
 * 구글로 로그인하면 그 계정에 묶인다(구글이 이메일 주인을 확인한 경우).
 */
export function LoginScreen() {
  const navigate = useNavigate()
  const location = useLocation()
  const { loginWithGoogle } = useAuth()
  const { showToast } = useToast()

  // RequireAuth가 남겨둔 "원래 가려던 곳". 없으면 홈으로 보낸다
  const redirectTo = redirectTarget(location.state)

  const handleGoogle = async (credential: string) => {
    try {
      await loginWithGoogle(credential)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    }
  }

  return (
    <div className={styles.login}>
      <div className={styles.brandBlock}>
        <div className={styles.brand}>밸런스핏</div>
        <div className={styles.tagline}>운동 기록을 분석해 부족한 부위를 찾아드려요</div>
      </div>

      {cameFromInvite(location.state) && (
        <div className={`${styles.inviteNotice} ${styles.inviteNoticeLogin}`}>그룹 초대를 받으셨네요. 로그인하면 바로 참여 화면으로 이어져요.</div>
      )}

      {googleEnabled ? (
        <GoogleButton onCredential={handleGoogle} />
      ) : (
        <p className={styles.notice}>지금은 로그인할 수 없어요. 잠시 뒤에 다시 열어 주세요.</p>
      )}

      <p className={styles.notice}>
        처음이면 바로 가입되고, <Link to={paths.privacy}>개인정보처리방침</Link>에 동의하는 것으로 봅니다.
      </p>
    </div>
  )
}
