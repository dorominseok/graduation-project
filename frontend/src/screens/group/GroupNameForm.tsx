import { useState } from 'react'
import { ErrorCodes, isApiError } from '../../api'
import { useToast } from '../../components'
import formStyles from '../full/settings.module.css'

interface GroupNameFormProps {
  initial?: string
  submitLabel: string
  savingLabel: string
  hint?: string
  /** 서버에 보내는 일. 실패하면 이름 칸 아래나 토스트로 알린다 */
  onSubmit: (name: string) => Promise<void>
}

/** 그룹 이름 입력 — 만들기와 이름 바꾸기가 같이 쓴다. 규칙(1~20자)은 서버와 같다 */
export function GroupNameForm({ initial = '', submitLabel, savingLabel, hint, onSubmit }: GroupNameFormProps) {
  const { showToast } = useToast()
  const [name, setName] = useState(initial)
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (saving) return
    if (!name.trim()) {
      setError('그룹 이름을 입력해주세요')
      return
    }
    setError(undefined)
    setSaving(true)
    try {
      await onSubmit(name.trim())
    } catch (err) {
      if (!isApiError(err)) throw err
      // 전각 공백만 넣은 이름은 서버가 걸러 VALIDATION_ERROR로 온다
      const field = err.fieldErrors().name
      if (field || err.code === ErrorCodes.VALIDATION_ERROR) setError(field ?? err.message)
      else showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className={formStyles.sheetBody} onSubmit={handleSubmit} noValidate>
      <div>
        <label className={formStyles.label} htmlFor="group-name">그룹 이름</label>
        <input
          id="group-name"
          className={`${formStyles.input} ${error ? formStyles.inputError : ''}`}
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={20}
          autoComplete="off"
        />
        {error && <div className={formStyles.error}>{error}</div>}
      </div>
      {hint && <p className={formStyles.hint}>{hint}</p>}
      <button type="submit" className={formStyles.submit} disabled={saving}>
        {saving ? savingLabel : submitLabel}
      </button>
    </form>
  )
}
