/**
 * 앱 밖에서도 휴식 알림(결정 기록 D-14, 「운동기록_방식_설계서」 3.7).
 *
 * 쉬는 동안 앱을 벗어나면 상단바에 "휴식 중"(내용: 오후 2:32에 끝나요 · 다음 세트)을 조용히 띄우고, 끝나면 **같은 알림을**
 * "휴식 끝"으로 바꿔 울린다. 제목은 두 낱말만 — 휴대폰은 제목 옆에 앱 이름·시각을 붙여 길면 잘린다(2026-11-28 실제 휴대폰)(같은 `tag`면 쌓이지 않고 그 자리에서 바뀐다). 앱으로 돌아오면 지운다 — 앱 안에서는
 * 휴식 띠와 끝 소리·진동이 있어 겹친다. 누르면 쉬고 나서 할 세트의 종목 화면으로 간다(`sw.ts`의 notificationclick).
 *
 * - 남은 초는 적지 않고 **끝나는 시각**을 적는다. 앱이 뒤로 가면 브라우저가 앱 코드를 멈춰 1초마다 고칠 수 없고,
 *   시각은 한 번 적으면 계속 맞다.
 * - 1차: 끝을 앱 안 타이머로 알아챈다. 앱이 뒤에 있는 동안 휴대폰이 앱을 멈추면 늦게 바뀔 수 있다 — 그러면
 *   서버가 끝나는 시각에 푸시를 보내는 2차로 간다(D-14 "다시 볼 때").
 * - 안드로이드 크롬은 화면의 `new Notification()`을 막아 서비스워커 등록으로만 띄운다. 그래서 켤 때 등록한다.
 */

import { PushPermissionError, ensureWorker, pushSupport, releaseWorkerIfUnused } from './push'

const TAG = 'rest'
const ICON = '/icons/icon-192.png'
/** 상단바 작은 그림 — 쉬는 동안은 타이머, 끝나면 아령(운동할 차례). 흰 모양만 쓰인다 */
const BADGE_RESTING = '/icons/badge-rest-96.png'
const BADGE_DONE = '/icons/badge-96.png'
const TIME = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })

/** 쉬고 나서 할 세트(`RestOrigin`과 같은 모양) */
export interface RestNext {
  path: string
  exerciseName: string
  setNumber: number
}

/** 이 기기에서 띄울 수 있는지. 아이폰은 홈 화면에 추가한 앱에서만 된다 */
export function canNotifyRest(): boolean {
  return pushSupport() === 'supported'
}

/** 켜져 있고 권한도 있는지 — 권한을 나중에 브라우저 설정에서 막았으면 꺼진 것으로 보인다 */
export function restNotifyAllowed(): boolean {
  return canNotifyRest() && Notification.permission === 'granted'
}

/**
 * 켠다 — 권한을 묻고 서비스워커를 등록한다. **누른 그 순간에 불러야 한다**(아이폰은 누른 직후에만 권한을 묻는다).
 * 거절하면 {@link PushPermissionError}.
 */
export async function enableRestNotify(): Promise<void> {
  const granted = await Notification.requestPermission()
  if (granted !== 'granted') throw new PushPermissionError(granted)
  await ensureWorker()
}

/** 끈다 — 떠 있는 휴식 알림을 지우고, 운동 알림도 안 쓰면 서비스워커를 지운다 */
export async function disableRestNotify(): Promise<void> {
  await clearRestNotification()
  await releaseWorkerIfUnused()
}

/** 쉬는 중 — 소리·진동 없이 */
export async function showResting(endsAt: number, next: RestNext | null): Promise<void> {
  const registration = await activeRegistration()
  if (!registration) return
  await registration.showNotification('휴식 중', {
    body: `${TIME.format(endsAt)}에 끝나요${next ? ` · ${next.exerciseName} ${next.setNumber}세트` : ''}`,
    tag: TAG,
    icon: ICON,
    badge: BADGE_RESTING,
    silent: true,
    lang: 'ko',
    data: { url: next?.path ?? '/session' },
  })
}

/** 휴식 끝 — 같은 알림을 바꾸고 다시 울린다 */
export async function showRestDone(next: RestNext | null): Promise<void> {
  const registration = await activeRegistration()
  if (!registration) return
  await registration.showNotification('휴식 끝', {
    body: next ? `${next.exerciseName} ${next.setNumber}세트` : undefined,
    tag: TAG,
    icon: ICON,
    badge: BADGE_DONE,
    // 같은 tag로 바꿀 때 기본은 조용히 바뀐다. 끝은 알아야 하니 다시 울린다
    renotify: true,
    silent: false,
    vibrate: [200, 100, 200],
    lang: 'ko',
    data: { url: next?.path ?? '/session' },
  } as NotificationOptions)
}

/** 앱으로 돌아왔거나 껐을 때 */
export async function clearRestNotification(): Promise<void> {
  if (!canNotifyRest()) return
  const registration = await navigator.serviceWorker.getRegistration('/')
  const shown = (await registration?.getNotifications({ tag: TAG })) ?? []
  shown.forEach((notification) => notification.close())
}

async function activeRegistration(): Promise<ServiceWorkerRegistration | null> {
  if (!restNotifyAllowed()) return null
  const registration = await navigator.serviceWorker.getRegistration('/')
  return registration?.active ? registration : null
}
