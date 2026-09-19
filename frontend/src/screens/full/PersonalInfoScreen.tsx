import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { ErrorCodes, authApi, isApiError } from '../../api'
import { useAuth } from '../../auth'
import { validateNickname } from '../auth/validation'
import styles from './settings.module.css'

/**
 * 개인정보 수정 — 닉네임(명세 4.6).
 *
 * 훈련 목표는 화면에서 뺐다(2026-09-20). 계획서에 없던 항목이고, 루틴 추천의
 * 입력(기능명세서 2.1)도 운동 기록뿐이라 어디에도 쓰이지 않는다. 필요해지면
 * 10월 루틴생성 로직 설계에서 근거를 갖춰 다시 넣는다.
 */
export function PersonalInfoScreen() {
  const navigate = useNavigate()
  const { user, applyUser } = useAuth()
  const { showToast } = useToast()

  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  if (!user) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (saving) return

    const nicknameError = validateNickname(nickname)
    if (nicknameError) {
      setError(nicknameError)
      return
    }
    setError(undefined)
    setSaving(true)

    try {
      // profile 객체를 보내지 않으면 서버가 프로필을 건드리지 않는다(명세 4.6).
      const next = await authApi.updateMe({ nickname })
      applyUser(next)
      showToast({ message: '저장했어요' })
      navigate(-1)
    } catch (err) {
      if (!isApiError(err)) throw err
      if (err.code === ErrorCodes.VALIDATION_ERROR) {
        setError(err.fieldErrors().nickname ?? err.message)
        return
      }
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <ScreenHeader title="개인정보" />

      <form className={styles.page} onSubmit={handleSubmit} noValidate>
        <div className={styles.fields}>
          <div>
            <label className={styles.label} htmlFor="nickname">
              닉네임
            </label>
            <input
              id="nickname"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="앱에서 보일 이름"
              autoComplete="nickname"
            />
            {error && <div className={styles.error}>{error}</div>}
          </div>
        </div>

        <button type="submit" className={styles.submit} disabled={saving}>
          {saving ? '저장 중…' : '저장'}
        </button>
      </form>
    </>
  )
}
