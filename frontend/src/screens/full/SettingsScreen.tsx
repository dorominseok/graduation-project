import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Chevron, ScreenHeader, useToast } from '../../components'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { TINTS, getTint, setTint } from '../../app/theme'
import type { Tint } from '../../app/theme'
import {
  canKeepScreenOn,
  canVibrate,
  getRestAlert,
  previewSound,
  previewVibration,
  setRestAlert,
} from '../../app/restAlert'
import type { RestAlertSettings } from '../../app/restAlert'
import { canNotifyRest, disableRestNotify, enableRestNotify, restNotifyAllowed } from '../../app/restNotification'
import { PushPermissionError } from '../../app/push'
import { SwitchRow } from './SwitchRow'
import styles from './settings.module.css'

const TINT_LABEL: Record<Tint, string> = {
  sky: '하늘',
  white: '화이트',
  lavender: '라벤더',
  mint: '민트',
  peach: '피치',
  aurora: '오로라',
}

/** 설정. 계정 관련 진입과 약관·고지, 알림, 휴식 타이머, 유리 색감, 로그아웃을 둔다. 화면 스타일은 유리 하나라 고르지 않는다 */
export function SettingsScreen() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const { showToast } = useToast()
  const [leaving, setLeaving] = useState(false)
  const [tint, setTintState] = useState<Tint>(getTint)

  const [restAlert, setRestAlertState] = useState<RestAlertSettings>(getRestAlert)

  const changeTint = (next: Tint) => {
    setTint(next)
    setTintState(next)
  }

  /** 휴식 타이머 소리·진동·화면 켜 두기. 소리·진동은 켤 때 한 번 들려준다 */
  const changeRestAlert = (patch: Partial<RestAlertSettings>) => {
    const next = { ...restAlert, ...patch }
    setRestAlert(next)
    setRestAlertState(next)
    if (patch.sound) previewSound()
    if (patch.vibrate) previewVibration()
  }

  /** 앱 밖에서도 휴식 알림. 켤 때 알림 권한을 묻는다 — 누른 그 순간에 물어야 해서 앞에 기다리는 일을 두지 않는다 */
  const restNotifyOn = restAlert.notify && restNotifyAllowed()
  const toggleRestNotify = async () => {
    if (restNotifyOn) {
      changeRestAlert({ notify: false })
      void disableRestNotify().catch(() => undefined)
      return
    }
    try {
      await enableRestNotify()
      changeRestAlert({ notify: true })
    } catch (err) {
      showToast({
        message:
          err instanceof PushPermissionError && err.permission === 'denied'
            ? '알림이 차단돼 있어요. 브라우저의 사이트 설정(설치한 앱이면 휴대폰 설정 › 앱 › 밸런스핏)에서 허용해 주세요.'
            : '알림을 켜지 못했어요',
        tone: 'danger',
      })
    }
  }

  /**
   * 로그아웃. 서버가 리프레시를 폐기하고 쿠키를 만료시킨다.
   *
   * 액세스 토큰은 남은 만료 시간 동안 유효하지만 메모리에서 지우므로 이 탭에서는
   * 더 쓰이지 않는다.
   */
  const handleLogout = async () => {
    if (leaving) return
    setLeaving(true)
    try {
      await logout()
      navigate(paths.login, { replace: true })
    } catch {
      // logout은 서버 호출이 실패해도 로컬 상태를 비운다. 화면만 옮기면 된다.
      showToast({ message: '로그아웃했어요' })
      navigate(paths.login, { replace: true })
    }
  }

  return (
    <>
      <ScreenHeader title="설정" />

      <div className={styles.page}>
        <div className={styles.section}>
          <div className={styles.sectionTitle}>계정</div>
          <div className={styles.card}>
            <Link to={paths.personalInfo} className={styles.row}>
              <span className={styles.rowLabel}>개인정보</span>
              <Chevron />
            </Link>
            <Link to={paths.accountSettings} className={styles.row}>
              <span className={styles.rowLabel}>계정 설정</span>
              <Chevron />
            </Link>
            <Link to={paths.privacy} className={styles.row}>
              <span className={styles.rowLabel}>개인정보처리방침</span>
              <Chevron />
            </Link>
            <Link to={paths.licenses} className={styles.row}>
              <span className={styles.rowLabel}>오픈소스 라이선스</span>
              <Chevron />
            </Link>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionTitle}>알림</div>
          <div className={styles.card}>
            <Link to={paths.notificationSettings} className={styles.row}>
              <span className={styles.rowLabel}>운동 알림</span>
              <Chevron />
            </Link>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionTitle}>휴식 타이머</div>
          <div className={styles.card}>
            <SwitchRow
              label="끝나면 소리"
              on={restAlert.sound}
              onToggle={() => changeRestAlert({ sound: !restAlert.sound })}
            />
            {canVibrate() && (
              <SwitchRow
                label="끝나면 진동"
                on={restAlert.vibrate}
                onToggle={() => changeRestAlert({ vibrate: !restAlert.vibrate })}
              />
            )}
            {canKeepScreenOn() && (
              <SwitchRow
                label="쉬는 동안 화면 켜 두기"
                on={restAlert.keepScreenOn}
                onToggle={() => changeRestAlert({ keepScreenOn: !restAlert.keepScreenOn })}
              />
            )}
            {canNotifyRest() && (
              <SwitchRow label="앱 밖에서도 알림" on={restNotifyOn} onToggle={() => void toggleRestNotify()} />
            )}
          </div>
          <p className={styles.hint}>
            {restNotifyOn
              ? '앱을 벗어나면 끝나는 시각을 알림으로 보여 주고, 끝나면 알림이 바뀌며 울려요.'
              : canKeepScreenOn()
                ? '앱 화면이 켜져 있을 때만 알릴 수 있어요. 쉬는 동안은 화면이 저절로 꺼지지 않고, 전원 버튼으로는 언제든 끌 수 있어요.'
                : '앱 화면이 켜져 있을 때만 알릴 수 있어요. 화면이 꺼지면 휴식이 끝나도 울리지 않아요.'}{' '}
            무음 모드에서는 소리가 나지 않을 수 있어요.
          </p>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionTitle}>화면</div>
          <div className={`${styles.card} ${styles.themePicker}`}>
            {/* 유리 배경의 색감. 동그라미가 그 배경의 축소판이다 */}
            <div className={styles.tints} role="radiogroup" aria-label="배경 색감">
              {TINTS.map((t) => (
                <button
                  key={t}
                  type="button"
                  role="radio"
                  aria-checked={t === tint}
                  className={`${styles.tint} ${t === tint ? styles.tintOn : ''}`}
                  onClick={() => changeTint(t)}
                >
                  <span className={styles.tintDot} data-tint={t} aria-hidden="true" />
                  <span className={styles.tintLabel}>{TINT_LABEL[t]}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.card}>
            <button
              type="button"
              className={`${styles.row} ${styles.rowDanger}`}
              onClick={handleLogout}
              disabled={leaving}
            >
              <span className={styles.rowLabel}>로그아웃</span>
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
