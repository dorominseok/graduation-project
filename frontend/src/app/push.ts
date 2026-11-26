/**
 * 이 기기의 알림 — 받을 수 있는 환경인지, 권한, 서비스워커 등록, 구독(「알림_설계서」 4.1).
 *
 * 서버에 남는 것은 "어느 계정이 어느 기기(푸시 주소)로 받는지"다. 브라우저 쪽 구독은 계정을 모르므로,
 * 어느 계정이 이 기기에서 켰는지를 여기 저장해 둔다(`OWNER_KEY`). 같은 브라우저로 다른 계정이 로그인하면
 * 앞 계정의 알림이 이 기기로 오지 않게 구독을 버린다 — 푸시 주소가 죽으면 서버도 다음 발송에서 지운다.
 */

import { notificationApi } from '../api'
import { getRestAlert } from './restAlert'
import type { NotificationSettings } from '../api'
import { NAVIGATE_MESSAGE, safeAppPath } from './pushProtocol'
import type { NavigateMessage } from './pushProtocol'

const SW_URL = '/sw.js'
const OWNER_KEY = 'balancefit.push.user'

/**
 * - `supported` — 받을 수 있다
 * - `ios-browser` — 아이폰·아이패드의 브라우저 탭. 홈 화면에 추가한 앱에서만 받는다(iOS 16.4 이상)
 * - `ios-old` — 홈 화면 앱이지만 iOS가 16.4보다 낮다
 * - `unsupported` — 이 브라우저가 웹 알림을 못 받는다(카카오톡 안 브라우저 등)
 */
export type PushSupport = 'supported' | 'ios-browser' | 'ios-old' | 'unsupported'

export function pushSupport(): PushSupport {
  if ('serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window) return 'supported'
  if (isIos()) return isStandalone() ? 'ios-old' : 'ios-browser'
  return 'unsupported'
}

function isIos(): boolean {
  const ua = navigator.userAgent
  // 아이패드는 데스크톱 사파리처럼 "Macintosh"라고 하지만 터치가 된다
  return /iPhone|iPad|iPod/.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

function isStandalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

/** 브라우저의 알림 권한. 받을 수 없는 환경이면 null */
export function permission(): NotificationPermission | null {
  return pushSupport() === 'supported' ? Notification.permission : null
}

/** 이 기기가 이 계정으로 알림을 받고 있는지 — 권한이 있고, 구독이 있고, 이 계정이 켰다 */
export async function isOnForUser(userId: number): Promise<boolean> {
  if (permission() !== 'granted') return false
  const subscription = await currentSubscription()
  return subscription !== null && readOwner() === String(userId)
}

/** 권한을 거절했을 때. 브라우저 설정에서 풀어야 한다 */
export class PushPermissionError extends Error {
  readonly permission: NotificationPermission

  constructor(permission: NotificationPermission) {
    super(`notification permission: ${permission}`)
    this.permission = permission
  }
}

/**
 * 이 기기에서 알림을 켠다 — 권한을 묻고, 서비스워커를 등록하고, 구독해서 서버에 등록한다.
 *
 * **누른 그 순간에 불러야 한다.** 아이폰은 사용자가 누른 직후가 아니면 권한을 묻지 못하므로, 권한
 * 요청 앞에 기다리는 일(서버 호출 등)을 두지 않는다.
 */
export async function enable(userId: number, vapidPublicKey: string): Promise<NotificationSettings> {
  const granted = await Notification.requestPermission()
  if (granted !== 'granted') throw new PushPermissionError(granted)

  const registration = await ensureWorker()

  const serverKey = fromBase64Url(vapidPublicKey)
  let subscription = await registration.pushManager.getSubscription()
  // 서버 키가 바뀌었으면 옛 키로 만든 구독은 받을 수 없다. 새로 만든다
  if (subscription && !sameBytes(subscription.options.applicationServerKey, serverKey)) {
    await subscription.unsubscribe()
    subscription = null
  }
  subscription ??= await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: serverKey })

  const settings = await notificationApi.subscribe(toRequest(subscription))
  writeOwner(String(userId))
  return settings
}

/**
 * 이 기기에서 알림을 끈다 — 서버에서 지우고(`tellServer`), 브라우저 구독과 서비스워커를 지운다.
 *
 * 서버 호출이 실패해도 끈다. 브라우저 구독을 지우면 그 푸시 주소가 죽어, 서버가 다음 발송에서
 * "구독이 없다"는 답을 받고 지운다.
 */
export async function disable({ tellServer }: { tellServer: boolean }): Promise<void> {
  if (pushSupport() !== 'supported') return
  const registration = await navigator.serviceWorker.getRegistration('/')
  const subscription = (await registration?.pushManager.getSubscription()) ?? null
  if (subscription && tellServer) {
    await notificationApi.unsubscribe(subscription.endpoint).catch(() => undefined)
  }
  await subscription?.unsubscribe().catch(() => false)
  // 휴식 알림도 같은 서비스워커로 띄운다. 그쪽이 켜져 있으면 남긴다
  if (!getRestAlert().notify) await registration?.unregister().catch(() => false)
  writeOwner(null)
}

/**
 * 알림 전용 서비스워커를 등록하고 켜질 때까지 기다린다. 운동 알림과 휴식 알림(`restNotification.ts`)이 같이 쓴다.
 *
 * navigator.serviceWorker.ready는 쓰지 않는다 — 한 번 풀리면 끈 뒤(등록 해제) 다시 켜도 지워진 옛 등록을 돌려준다.
 */
export async function ensureWorker(): Promise<ServiceWorkerRegistration> {
  const registration = await navigator.serviceWorker.register(SW_URL, { scope: '/' })
  await activated(registration)
  return registration
}

/** 휴식 알림을 끌 때. 운동 알림(구독)이 없으면 서비스워커도 지운다 — 알림을 쓰지 않는 사람에게는 두지 않는다 */
export async function releaseWorkerIfUnused(): Promise<void> {
  if (pushSupport() !== 'supported') return
  const registration = await navigator.serviceWorker.getRegistration('/')
  if (!registration || (await registration.pushManager.getSubscription())) return
  await registration.unregister().catch(() => false)
}

/**
 * 다른 계정이 켜 둔 구독이면 버린다. 로그인할 때마다 부른다.
 *
 * 앞 계정이 로그아웃하지 않고(로그인이 만료돼) 다른 계정이 들어온 경우다. 그대로 두면 앞 계정의
 * 리마인더·응원이 이 기기로 온다.
 */
export async function releaseIfOtherUser(userId: number): Promise<void> {
  if (pushSupport() !== 'supported') return
  const owner = readOwner()
  if (owner === String(userId)) return
  const subscription = await currentSubscription()
  if (subscription || owner !== null) await disable({ tellServer: false })
}

/**
 * 서버 기록이 없어졌으면 다시 등록한다. 설정 › 알림을 열 때 부른다.
 *
 * 이 기기는 켜져 있는데 계정에 기기가 하나도 없는 경우 — 서버가 일시적인 발송 오류로 지웠거나
 * 다른 기기에서 정리된 경우다. 같은 주소를 다시 보내면 서버는 그대로 받는다.
 */
export async function resync(userId: number, settings: NotificationSettings): Promise<NotificationSettings> {
  if (settings.subscriptionCount > 0 || !(await isOnForUser(userId))) return settings
  const subscription = await currentSubscription()
  return subscription ? notificationApi.subscribe(toRequest(subscription)) : settings
}

/**
 * 알림을 누르면 서비스워커가 열린 창에 경로를 보낸다(`sw.ts`). 그 경로로 옮긴다. 앱이 뜰 때 한 번 건다.
 */
export function listenForNotificationClicks(navigate: (path: string) => void): () => void {
  if (!('serviceWorker' in navigator)) return () => undefined
  const onMessage = (event: MessageEvent) => {
    const data = event.data as Partial<NavigateMessage> | null
    if (data?.type === NAVIGATE_MESSAGE) navigate(safeAppPath(data.url))
  }
  navigator.serviceWorker.addEventListener('message', onMessage)
  return () => navigator.serviceWorker.removeEventListener('message', onMessage)
}

/** 서비스워커가 켜질 때까지(installing → activated). 이미 켜진 것이 있으면 바로 */
function activated(registration: ServiceWorkerRegistration): Promise<void> {
  if (registration.active) return Promise.resolve()
  const worker = registration.installing ?? registration.waiting
  if (!worker) return Promise.reject(new Error('no service worker to activate'))
  return new Promise((resolve, reject) => {
    const onChange = () => {
      if (worker.state === 'activated') resolve()
      else if (worker.state === 'redundant') reject(new Error('service worker became redundant'))
      else return
      worker.removeEventListener('statechange', onChange)
    }
    worker.addEventListener('statechange', onChange)
  })
}

/** 등록하지 않고 지금 있는 구독만 본다 — 알림을 켜지 않은 사람에게 서비스워커를 만들지 않는다 */
async function currentSubscription(): Promise<PushSubscription | null> {
  if (pushSupport() !== 'supported') return null
  const registration = await navigator.serviceWorker.getRegistration('/')
  return (await registration?.pushManager.getSubscription()) ?? null
}

function toRequest(subscription: PushSubscription) {
  const json = subscription.toJSON()
  return { endpoint: subscription.endpoint, keys: { p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' } }
}

function fromBase64Url(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')
  const binary = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

function sameBytes(a: ArrayBuffer | null, b: Uint8Array): boolean {
  if (!a || a.byteLength !== b.length) return false
  const view = new Uint8Array(a)
  return view.every((byte, i) => byte === b[i])
}

function readOwner(): string | null {
  try {
    return localStorage.getItem(OWNER_KEY)
  } catch {
    return null
  }
}

function writeOwner(userId: string | null): void {
  try {
    if (userId === null) localStorage.removeItem(OWNER_KEY)
    else localStorage.setItem(OWNER_KEY, userId)
  } catch {
    // 저장하지 못하면 다음 로그인 때 다른 계정 것으로 보고 구독을 버린다 — 다시 켜면 된다
  }
}
