import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { paths } from '../../app/paths'
import { ErrorCodes, isApiError } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components'
import { validateEmail, validateNickname, validatePassword } from './validation'
import styles from './auth.module.css'

/**
 * 회원가입 화면.
 *
 * 가입 성공 시 서버가 토큰을 함께 내려주므로 로그인 왕복은 없다(API 명세서 4.1).
 */
export function SignupScreen() {
  const navigate = useNavigate()
  const { signUp } = useAuth()
  const { showToast } = useToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [errors, setErrors] = useState<{
    email?: string
    password?: string
    nickname?: string
  }>({})
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (submitting) return

    const emailError = validateEmail(email)
    const passwordError = validatePassword(password)
    const nicknameError = validateNickname(nickname)
    if (emailError || passwordError || nicknameError) {
      setErrors({
        email: emailError ?? undefined,
        password: passwordError ?? undefined,
        nickname: nicknameError ?? undefined,
      })
      return
    }
    setErrors({})
    setSubmitting(true)

    try {
      await signUp(email, password, nickname)
      navigate(paths.home, { replace: true })
    } catch (err) {
      if (!isApiError(err)) throw err

      // 중복 이메일은 이메일 필드 아래에 붙인다. 어느 칸을 고쳐야 하는지가 분명해서.
      if (err.code === ErrorCodes.EMAIL_ALREADY_EXISTS) {
        setErrors({ email: err.message })
        return
      }

      if (err.code === ErrorCodes.VALIDATION_ERROR) {
        const fields = err.fieldErrors()
        setErrors({
          email: fields.email,
          password: fields.password,
          nickname: fields.nickname,
        })
        return
      }

      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.signup} onSubmit={handleSubmit} noValidate>
      <div className={styles.signupTitleBlock}>
        <div className={styles.pageTitle}>회원가입</div>
        <div className={styles.tagline}>기록을 시작하려면 계정을 만들어주세요</div>
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
            placeholder="8자 이상"
            autoComplete="new-password"
          />
          {errors.password && <div className={styles.error}>{errors.password}</div>}
        </div>

        <div>
          <div className={styles.label}>닉네임</div>
          <input
            className={`${styles.input} ${errors.nickname ? styles.inputError : ''}`}
            type="text"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="앱에서 보일 이름"
            autoComplete="nickname"
          />
          {errors.nickname && <div className={styles.error}>{errors.nickname}</div>}
        </div>
      </div>

      <button type="submit" className={styles.submit} disabled={submitting}>
        {submitting ? '가입 중…' : '가입하기'}
      </button>

      <Link to={paths.login} className={styles.switch}>
        이미 계정이 있으신가요? <span className={styles.switchAccent}>로그인</span>
      </Link>
    </form>
  )
}
