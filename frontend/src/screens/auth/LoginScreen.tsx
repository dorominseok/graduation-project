import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { paths } from '../../app/paths'
import { validateEmail, validatePassword } from './validation'
import styles from './auth.module.css'

/**
 * 로그인 화면. API 연동은 아직 하지 않는다 —
 * POST /auth/login 붙이는 자리는 handleSubmit 안에 표시해뒀다.
 */
export function LoginScreen() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const emailError = validateEmail(email)
    const passwordError = validatePassword(password)
    if (emailError || passwordError) {
      setErrors({ email: emailError ?? undefined, password: passwordError ?? undefined })
      return
    }
    setErrors({})
    // TODO(9월): POST /auth/login → accessToken 저장 후 홈으로.
    // 401 INVALID_CREDENTIALS는 이메일/비밀번호를 구분하지 않고 한 문구로 표시한다.
    navigate(paths.home)
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

      <button type="submit" className={styles.submit}>
        로그인
      </button>

      <Link to={paths.signup} className={styles.switch}>
        계정이 없으신가요? <span className={styles.switchAccent}>회원가입</span>
      </Link>
    </form>
  )
}
