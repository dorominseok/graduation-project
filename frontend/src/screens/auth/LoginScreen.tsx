import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { paths } from '../../app/paths'
import { ErrorCodes, isApiError } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components'
import { validateEmail, validatePassword } from './validation'
import styles from './auth.module.css'

/** RequireAuth가 남겨둔 "원래 가려던 곳". 없으면 홈으로 보낸다. */
interface FromState {
  from?: { pathname?: string }
}

export function LoginScreen() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const { showToast } = useToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = (location.state as FromState | null)?.from?.pathname ?? paths.home

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (submitting) return

    const emailError = validateEmail(email)
    const passwordError = validatePassword(password)
    if (emailError || passwordError) {
      setErrors({ email: emailError ?? undefined, password: passwordError ?? undefined })
      return
    }
    setErrors({})
    setSubmitting(true)

    try {
      await login(email, password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      if (!isApiError(err)) throw err

      if (err.code === ErrorCodes.VALIDATION_ERROR) {
        const fields = err.fieldErrors()
        setErrors({ email: fields.email, password: fields.password })
        return
      }

      // INVALID_CREDENTIALS는 이메일이 없을 때와 비밀번호가 틀렸을 때 모두 같은 코드로
      // 온다. 화면도 구분하지 않는다 — 구분해 보여주면 가입 여부가 드러난다(명세 4.2).
      // 입력 오류라 토스트 대신 폼 안에 붙인다. 토스트는 네트워크 같은 예상 밖 오류용.
      if (err.code === ErrorCodes.INVALID_CREDENTIALS) {
        setErrors({ password: err.message })
        return
      }

      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.login} onSubmit={handleSubmit} noValidate>
      <div className={styles.brandBlock}>
        <div className={styles.brand}>밸런스핏</div>
        <div className={styles.tagline}>운동 기록을 분석해 부족한 부위를 찾아드려요</div>
      </div>

      <div className={styles.fields}>
        <div>
          <div className={styles.label}>이메일</div>
          <input
            className={`${styles.input} ${errors.email ? styles.inputError : ''}`}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            autoComplete="email"
          />
          {errors.email && <div className={styles.error}>{errors.email}</div>}
        </div>

        <div>
          <div className={styles.label}>비밀번호</div>
          <input
            className={`${styles.input} ${errors.password ? styles.inputError : ''}`}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            autoComplete="current-password"
          />
          {errors.password && <div className={styles.error}>{errors.password}</div>}
        </div>
      </div>

      <button type="submit" className={styles.submit} disabled={submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </button>

      <Link to={paths.signup} className={styles.switch}>
        계정이 없으신가요? <span className={styles.switchAccent}>회원가입</span>
      </Link>
    </form>
  )
}
