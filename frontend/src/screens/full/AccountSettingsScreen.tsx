import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, Chevron, ScreenHeader, useToast } from '../../components'
import { ErrorCodes, authApi, isApiError } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { validatePassword } from '../auth/validation'
import styles from './settings.module.css'

type Sheet = 'password' | 'withdraw' | null

/**
 * 계정 설정 — 이메일 확인, 비밀번호 변경, 회원 탈퇴.
 *
 * 이메일은 읽기 전용이다. 로그인 식별자라 바꾸려면 중복 검사와 기존 세션 처리가
 * 따라붙는데, 지인 10명 검증에서 바꿀 이유가 없다(LOG-21).
 */
export function AccountSettingsScreen() {
  const navigate = useNavigate()
  const { user, logout, resetSession } = useAuth()
  const { showToast } = useToast()
  const [sheet, setSheet] = useState<Sheet>(null)

  if (!user) return null

  return (
    <>
      <ScreenHeader title="계정" />

      <div className={styles.page}>
        <div className={styles.section}>
          <div className={styles.card}>
            <div className={`${styles.row} ${styles.rowStatic}`}>
              <span className={styles.rowLabel}>이메일</span>
              <span className={styles.rowValue}>{user.email}</span>
            </div>
            <button
              type="button"
              className={styles.row}
              onClick={() => setSheet('password')}
            >
              <span className={styles.rowLabel}>비밀번호 변경</span>
              <Chevron />
            </button>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.card}>
            <button
              type="button"
              className={`${styles.row} ${styles.rowDanger}`}
              onClick={() => setSheet('withdraw')}
            >
              <span className={styles.rowLabel}>회원 탈퇴</span>
            </button>
          </div>
          <p className={styles.hint}>
            탈퇴하면 계정과 모든 운동 기록이 지워지고 되돌릴 수 없습니다.
          </p>
        </div>
      </div>

      <BottomSheet
        open={sheet === 'password'}
        onClose={() => setSheet(null)}
        title="비밀번호 변경"
      >
        <ChangePasswordForm
          onDone={async () => {
            setSheet(null)
            // 서버가 리프레시를 폐기했으므로 이 세션은 끝이다. 액세스 토큰이 아직
            // 살아 있는 동안 정상 로그아웃해서 남은 흔적을 정리한다.
            await logout().catch(() => resetSession())
            showToast({ message: '비밀번호를 바꿨어요. 다시 로그인해주세요.' })
            navigate(paths.login, { replace: true })
          }}
        />
      </BottomSheet>

      <BottomSheet
        open={sheet === 'withdraw'}
        onClose={() => setSheet(null)}
        title="회원 탈퇴"
      >
        <WithdrawForm
          onDone={() => {
            setSheet(null)
            // 계정이 사라져 로그아웃을 부를 대상이 없다. 로컬 상태만 비운다.
            resetSession()
            showToast({ message: '탈퇴 처리했어요' })
            navigate(paths.login, { replace: true })
          }}
        />
      </BottomSheet>
    </>
  )
}

function ChangePasswordForm({ onDone }: { onDone: () => void | Promise<void> }) {
  const { showToast } = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<{ current?: string; next?: string; confirm?: string }>({})
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (saving) return

    const nextError = validatePassword(next)
    if (!current || nextError || next !== confirm) {
      setErrors({
        current: current ? undefined : '현재 비밀번호를 입력해주세요',
        next: nextError ?? undefined,
        confirm: next === confirm ? undefined : '새 비밀번호와 다릅니다',
      })
      return
    }
    setErrors({})
    setSaving(true)

    try {
      await authApi.changePassword({ currentPassword: current, newPassword: next })
      await onDone()
    } catch (err) {
      if (!isApiError(err)) throw err

      // 현재 비밀번호가 틀린 경우다. 새 비밀번호 칸이 아니라 그 칸에 붙여야
      // 어디를 고쳐야 하는지 분명하다(명세 4.8).
      if (err.code === ErrorCodes.INVALID_CREDENTIALS) {
        setErrors({ current: err.message })
        return
      }
      if (err.code === ErrorCodes.VALIDATION_ERROR) {
        setErrors({ next: err.fieldErrors().newPassword ?? err.message })
        return
      }
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className={styles.sheetBody} onSubmit={handleSubmit} noValidate>
      <div>
        <label className={styles.label} htmlFor="current-password">현재 비밀번호</label>
        <input
          id="current-password"
          type="password"
          className={`${styles.input} ${errors.current ? styles.inputError : ''}`}
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          autoComplete="current-password"
        />
        {errors.current && <div className={styles.error}>{errors.current}</div>}
      </div>

      <div>
        <label className={styles.label} htmlFor="new-password">새 비밀번호</label>
        <input
          id="new-password"
          type="password"
          className={`${styles.input} ${errors.next ? styles.inputError : ''}`}
          value={next}
          onChange={(e) => setNext(e.target.value)}
          placeholder="8자 이상"
          autoComplete="new-password"
        />
        {errors.next && <div className={styles.error}>{errors.next}</div>}
      </div>

      <div>
        <label className={styles.label} htmlFor="confirm-password">새 비밀번호 확인</label>
        <input
          id="confirm-password"
          type="password"
          className={`${styles.input} ${errors.confirm ? styles.inputError : ''}`}
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
        />
        {errors.confirm && <div className={styles.error}>{errors.confirm}</div>}
      </div>

      <p className={styles.hint}>
        비밀번호를 변경하면 로그인된 모든 기기에서 로그아웃됩니다.
      </p>

      <button type="submit" className={styles.submit} disabled={saving}>
        {saving ? '변경 중…' : '비밀번호 변경'}
      </button>
    </form>
  )
}

function WithdrawForm({ onDone }: { onDone: () => void }) {
  const { showToast } = useToast()
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (saving) return
    if (!password) {
      setError('비밀번호를 입력해주세요')
      return
    }
    setError(undefined)
    setSaving(true)

    try {
      await authApi.deleteMe(password)
      onDone()
    } catch (err) {
      if (!isApiError(err)) throw err
      if (err.code === ErrorCodes.INVALID_CREDENTIALS) {
        setError(err.message)
        return
      }
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className={styles.sheetBody} onSubmit={handleSubmit} noValidate>
      <p className={styles.hint}>
        계정과 모든 운동 기록이 지워집니다. 되돌릴 수 없습니다. 확인을 위해 비밀번호를
        입력해주세요.
      </p>

      <div>
        <label className={styles.label} htmlFor="withdraw-password">비밀번호</label>
        <input
          id="withdraw-password"
          type="password"
          className={`${styles.input} ${error ? styles.inputError : ''}`}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />
        {error && <div className={styles.error}>{error}</div>}
      </div>

      <button type="submit" className={styles.danger} disabled={saving}>
        {saving ? '처리 중…' : '탈퇴하기'}
      </button>
    </form>
  )
}
