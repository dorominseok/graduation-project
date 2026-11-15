import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ScreenHeader, useToast } from '../../components'
import { isApiError, notificationApi } from '../../api'
import type { DayOfWeek, NotificationSettings, UpdateNotificationSettingsRequest } from '../../api'
import { useAuth } from '../../auth'
import * as push from '../../app/push'
import type { PushSupport } from '../../app/push'
import settingsStyles from './settings.module.css'
import styles from './notification.module.css'

const DAYS: { key: DayOfWeek; label: string }[] = [
  { key: 'MONDAY', label: '월' },
  { key: 'TUESDAY', label: '화' },
  { key: 'WEDNESDAY', label: '수' },
  { key: 'THURSDAY', label: '목' },
  { key: 'FRIDAY', label: '금' },
  { key: 'SATURDAY', label: '토' },
  { key: 'SUNDAY', label: '일' },
]

/** 고를 수 있는 시각 — 08:00~21:30, 30분 단위. 조용한 시간(22:00~08:00)과 겹치지 않는다(서버도 같은 규칙) */
const TIMES: string[] = Array.from({ length: 28 }, (_, i) => {
  const minutes = 8 * 60 + i * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})

/** "19:30" → "오후 7:30" */
function timeLabel(time: string): string {
  const [h, m] = time.split(':').map(Number)
  const half = h < 12 ? '오전' : '오후'
  const hour = h % 12 === 0 ? 12 : h % 12
  return `${half} ${hour}:${String(m).padStart(2, '0')}`
}

const byWeekOrder = (a: DayOfWeek, b: DayOfWeek) =>
  DAYS.findIndex((d) => d.key === a) - DAYS.findIndex((d) => d.key === b)

/** 바꾸고 이만큼 더 바꾸지 않으면 저장한다. 요일을 여러 개 누를 때 한 번만 보내려고 */
const SAVE_DELAY_MS = 500

/**
 * 설정 › 알림(「알림_설계서」 4.1).
 *
 * 위는 **이 기기**에서 받을지(권한·구독), 아래는 **계정**의 설정(종류·요일·시각)이다. 계정 설정은 모든
 * 기기에 같이 적용되고, 바꾸면 따로 저장 버튼 없이 바로 저장한다.
 */
export function NotificationSettingsScreen() {
  const { user } = useAuth()
  const { showToast } = useToast()
  const [settings, setSettings] = useState<NotificationSettings | null>(null)
  const [deviceOn, setDeviceOn] = useState(false)
  const [permission, setPermission] = useState(push.permission)
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  const support = push.pushSupport()

  const userId = user?.userId
  // 올리면 서버 값을 다시 불러온다(불러오기 실패 뒤 다시, 저장 실패 뒤 되돌리기)
  const [loadCount, setLoadCount] = useState(0)
  const reload = useCallback(() => setLoadCount((n) => n + 1), [])

  useEffect(() => {
    if (userId === undefined) return
    let alive = true
    const load = async () => {
      try {
        const loaded = await notificationApi.getSettings()
        const synced = await push.resync(userId, loaded).catch(() => loaded)
        const on = await push.isOnForUser(userId)
        if (!alive) return
        setSettings(synced)
        setDeviceOn(on)
        setFailed(false)
      } catch {
        if (alive) setFailed(true)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [userId, loadCount])

  // ── 계정 설정 저장. 마지막 값만, 하나씩 차례로 보낸다. 화면을 떠날 때 남은 것은 바로 보낸다
  const pending = useRef<UpdateNotificationSettingsRequest | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const queue = useRef<Promise<unknown>>(Promise.resolve())

  const flush = useCallback(() => {
    clearTimeout(timer.current)
    const body = pending.current
    pending.current = null
    if (!body) return
    queue.current = queue.current.then(async () => {
      try {
        await notificationApi.updateSettings(body)
      } catch (err) {
        showToast({ message: isApiError(err) ? err.message : '알림 설정을 저장하지 못했어요', tone: 'danger' })
        reload()
      }
    })
  }, [reload, showToast])

  useEffect(() => flush, [flush])

  const change = (next: Partial<UpdateNotificationSettingsRequest>) => {
    if (!settings) return
    // 사용자가 고른 값이 되므로 "골라 두었어요" 안내는 내린다
    const merged = { ...settings, ...next, saved: true }
    setSettings(merged)
    pending.current = { reminder: merged.reminder, summary: merged.summary, cheer: merged.cheer, group: merged.group }
    clearTimeout(timer.current)
    timer.current = setTimeout(flush, SAVE_DELAY_MS)
  }

  // ── 이 기기
  const toggleDevice = async () => {
    if (!settings || userId === undefined || busy) return
    setBusy(true)
    try {
      // 기기 수만 새로 받는다. 계정 설정은 화면에서 바꾸는 중일 수 있어 덮지 않는다
      const applyCount = (count: number) =>
        setSettings((prev) => (prev ? { ...prev, subscriptionCount: count, saved: true } : prev))
      if (deviceOn) {
        await push.disable({ tellServer: true })
        setDeviceOn(false)
        showToast({ message: '이 기기에서는 알림을 받지 않아요' })
        applyCount((await notificationApi.getSettings()).subscriptionCount)
      } else {
        // 아이폰은 누른 직후에만 권한을 물을 수 있어 enable이 가장 먼저 권한을 묻는다
        const result = await push.enable(userId, settings.vapidPublicKey ?? '')
        setDeviceOn(true)
        showToast({ message: '이 기기로 알림을 보내 드릴게요' })
        applyCount(result.subscriptionCount)
      }
    } catch (err) {
      // 권한을 거절한 경우는 아래 안내 문구가 바뀌는 것으로 알린다
      if (!(err instanceof push.PushPermissionError)) {
        showToast({ message: isApiError(err) ? err.message : '알림을 켜지 못했어요. 잠시 뒤 다시 해 주세요', tone: 'danger' })
      }
    } finally {
      setPermission(push.permission())
      setBusy(false)
    }
  }

  if (!user) return null

  return (
    <>
      <ScreenHeader title="알림" />

      <div className={`${settingsStyles.page} ${styles.screen}`}>
        {failed && (
          <div className={settingsStyles.section}>
            <p className={settingsStyles.hint}>알림 설정을 불러오지 못했어요.</p>
            <button type="button" className={settingsStyles.submit} onClick={reload}>
              다시 불러오기
            </button>
          </div>
        )}

        {settings && (
          <>
            <div className={settingsStyles.section}>
              <div className={settingsStyles.card}>
                <SwitchRow
                  label="이 기기에서 알림 받기"
                  on={deviceOn}
                  disabled={busy || (!deviceOn && !canEnable(support, permission, settings))}
                  onToggle={toggleDevice}
                />
              </div>
              <p className={settingsStyles.hint}>{deviceHint(support, permission, settings, deviceOn)}</p>
            </div>

            <div className={settingsStyles.section}>
              <div className={settingsStyles.sectionTitle}>운동 리마인더</div>
              <div className={settingsStyles.card}>
                <SwitchRow
                  label="운동할 날 알려 주기"
                  on={settings.reminder.enabled}
                  onToggle={() => change({ reminder: { ...settings.reminder, enabled: !settings.reminder.enabled } })}
                />
                {settings.reminder.enabled && (
                  <>
                    <Field label="요일">
                      <DayPicker
                        label="리마인더 요일"
                        selected={settings.reminder.days}
                        onPick={(day) => {
                          const days = settings.reminder.days
                          if (days.includes(day) && days.length === 1) {
                            showToast({ message: '요일을 하나 이상 골라 주세요' })
                            return
                          }
                          const next = days.includes(day) ? days.filter((d) => d !== day) : [...days, day].sort(byWeekOrder)
                          change({ reminder: { ...settings.reminder, days: next } })
                        }}
                      />
                    </Field>
                    <Field label="시각">
                      <TimeSelect
                        label="리마인더 시각"
                        value={settings.reminder.time}
                        onChange={(time) => change({ reminder: { ...settings.reminder, time } })}
                      />
                    </Field>
                  </>
                )}
              </div>
              <p className={settingsStyles.hint}>
                고른 요일·시각에 그날 운동 기록이 없으면, 오늘의 루틴이 고른 부위와 그 이유를 알려 드려요. 쉬는
                날은 빼고 고르세요.
                {!settings.saved && ' 요일은 최근 4주에 자주 운동한 요일로 골라 두었어요.'}
              </p>
            </div>

            <div className={settingsStyles.section}>
              <div className={settingsStyles.sectionTitle}>주간 요약</div>
              <div className={settingsStyles.card}>
                <SwitchRow
                  label="한 주 돌아보기"
                  on={settings.summary.enabled}
                  onToggle={() => change({ summary: { ...settings.summary, enabled: !settings.summary.enabled } })}
                />
                {settings.summary.enabled && (
                  <>
                    <Field label="요일">
                      <DayPicker
                        label="주간 요약 요일"
                        single
                        selected={[settings.summary.day]}
                        onPick={(day) => change({ summary: { ...settings.summary, day } })}
                      />
                    </Field>
                    <Field label="시각">
                      <TimeSelect
                        label="주간 요약 시각"
                        value={settings.summary.time}
                        onChange={(time) => change({ summary: { ...settings.summary, time } })}
                      />
                    </Field>
                  </>
                )}
              </div>
              <p className={settingsStyles.hint}>이번 주에 운동한 날과 더 채우면 좋은 부위를 알려 드려요.</p>
            </div>

            <div className={settingsStyles.section}>
              <div className={settingsStyles.sectionTitle}>그룹</div>
              <div className={settingsStyles.card}>
                <SwitchRow label="응원 받음" on={settings.cheer} onToggle={() => change({ cheer: !settings.cheer })} />
                <SwitchRow label="그룹 소식" on={settings.group} onToggle={() => change({ group: !settings.group })} />
              </div>
              <p className={settingsStyles.hint}>
                그룹 소식은 내 그룹에 새 멤버가 들어올 때(방장에게)와 방장을 넘겨받을 때 와요.
              </p>
            </div>

            <p className={settingsStyles.hint}>
              밤 10시부터 아침 8시까지는 알림을 보내지 않아요. 그 시간에 받은 응원·그룹 소식은 알림 없이 그룹
              화면에서만 보여요.
            </p>
          </>
        )}
      </div>
    </>
  )
}

/** 이 기기에서 켤 수 있는지 */
function canEnable(support: PushSupport, permission: NotificationPermission | null, settings: NotificationSettings) {
  return support === 'supported' && permission !== 'denied' && settings.pushAvailable && settings.vapidPublicKey !== null
}

/** 이 기기 줄 아래 한 줄 — 왜 켤 수 없는지, 켜면 어떻게 되는지 */
function deviceHint(
  support: PushSupport,
  permission: NotificationPermission | null,
  settings: NotificationSettings,
  deviceOn: boolean,
): string {
  if (support === 'ios-browser') {
    return '아이폰은 홈 화면에 추가한 밸런스핏에서만 알림을 받을 수 있어요. 사파리의 공유 › 홈 화면에 추가로 설치한 뒤 그 앱에서 켜 주세요.'
  }
  if (support === 'ios-old') return '아이폰은 iOS 16.4 이상에서 알림을 받을 수 있어요.'
  if (support === 'unsupported') {
    return '이 브라우저에서는 알림을 받을 수 없어요. 카카오톡 같은 앱 안에서 열었다면 크롬이나 사파리로 열어 주세요.'
  }
  if (!settings.pushAvailable) return '지금은 서버에서 알림을 보낼 수 없어요. 준비되면 여기서 켤 수 있어요.'
  if (permission === 'denied') {
    return '알림이 차단돼 있어요. 브라우저의 사이트 설정(설치한 앱이면 휴대폰 설정 › 앱 › 밸런스핏)에서 알림을 허용한 뒤 다시 열어 주세요.'
  }
  if (deviceOn) {
    const others = settings.subscriptionCount - 1
    return others > 0 ? `이 기기와 다른 기기 ${others}대로 알림이 와요.` : '이 기기로 알림이 와요.'
  }
  return '켜면 이 기기로 알림이 와요. 휴대폰·컴퓨터마다 따로 켜요.'
}

function SwitchRow({
  label,
  on,
  disabled,
  onToggle,
}: {
  label: string
  on: boolean
  disabled?: boolean
  onToggle: () => void
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      className={`${settingsStyles.row} ${styles.switchRow}`}
      disabled={disabled}
      onClick={onToggle}
    >
      <span className={settingsStyles.rowLabel}>{label}</span>
      <span className={`${styles.switch} ${on ? styles.switchOn : ''}`} aria-hidden="true" />
    </button>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <span className={styles.fieldLabel}>{label}</span>
      {children}
    </div>
  )
}

function DayPicker({
  label,
  selected,
  single,
  onPick,
}: {
  label: string
  selected: DayOfWeek[]
  single?: boolean
  onPick: (day: DayOfWeek) => void
}) {
  return (
    <div className={styles.days} role={single ? 'radiogroup' : 'group'} aria-label={label}>
      {DAYS.map(({ key, label: dayLabel }) => {
        const on = selected.includes(key)
        return (
          <button
            key={key}
            type="button"
            role={single ? 'radio' : undefined}
            aria-checked={single ? on : undefined}
            aria-pressed={single ? undefined : on}
            className={`${styles.day} ${on ? styles.dayOn : ''}`}
            onClick={() => onPick(key)}
          >
            {dayLabel}
          </button>
        )
      })}
    </div>
  )
}

function TimeSelect({ label, value, onChange }: { label: string; value: string; onChange: (time: string) => void }) {
  return (
    <div className={styles.selectWrap}>
      <select className={styles.select} aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
        {TIMES.map((time) => (
          <option key={time} value={time}>
            {timeLabel(time)}
          </option>
        ))}
      </select>
      <svg className={styles.selectArrow} width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  )
}
