import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { ErrorCodes, TRAINING_GOAL_LABEL, authApi, isApiError } from '../../api'
import type { TrainingGoal } from '../../api'
import { useAuth } from '../../auth'
import { validateNickname } from '../auth/validation'
import styles from './settings.module.css'

const GOALS = Object.keys(TRAINING_GOAL_LABEL) as TrainingGoal[]

/**
 * 개인정보 수정 — 닉네임과 훈련 목표(명세 4.6).
 *
 * 목표를 고른 항목에서 한 번 더 누르면 미설정으로 되돌아간다. 명세가 "없음"과
 * "null로 지움"을 구분하므로 되돌릴 방법이 화면에도 있어야 한다.
 */
export function PersonalInfoScreen() {
  const navigate = useNavigate()
  const { user, applyUser } = useAuth()
  const { showToast } = useToast()

  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [goal, setGoal] = useState<TrainingGoal | null>(user?.profile.goal ?? null)
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
      // goal은 null도 의미 있는 값이라 profile 객체를 항상 함께 보낸다.
      const next = await authApi.updateMe({ nickname, profile: { goal } })
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

          <div>
            <span className={styles.label}>훈련 목표</span>
            <div className={styles.goalGrid}>
              {GOALS.map((value) => (
                <button
                  key={value}
                  type="button"
                  className={`${styles.goalOption} ${goal === value ? styles.goalOptionActive : ''}`}
                  aria-pressed={goal === value}
                  onClick={() => setGoal(goal === value ? null : value)}
                >
                  {TRAINING_GOAL_LABEL[value]}
                </button>
              ))}
            </div>
            <p className={styles.hint}>
              루틴 추천이 반복 횟수와 강도를 목표에 맞춰 정합니다. 고른 항목을 다시 누르면
              미설정으로 돌아갑니다.
            </p>
          </div>
        </div>

        <button type="submit" className={styles.submit} disabled={saving}>
          {saving ? '저장 중…' : '저장'}
        </button>
      </form>
    </>
  )
}
