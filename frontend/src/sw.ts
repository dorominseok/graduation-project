/// <reference lib="webworker" />

/**
 * 알림 전용 서비스워커(「알림_설계서」 4.1). 알림을 띄우고(push), 누르면 그 화면으로 보낸다(notificationclick).
 *
 * **요청은 가로채지 않는다** — fetch 처리도 캐시도 없다. 서비스워커를 꺼 둔 이유("배포한 새 화면이 캐시에
 * 막혀 늦게 보인다")를 다시 만들지 않기 위해서다. 그래서 화면·API·`/licenses/`는 서비스워커가 없을 때와
 * 똑같이 오간다.
 *
 * 등록은 설정 › 알림에서 이 기기의 알림을 켤 때만 한다(`app/push.ts`). 알림을 켜지 않은 사람에게는 없다.
 * 빌드는 vite-plugin-pwa가 따로 해서 `/sw.js`로 내놓는다(vite.config.ts).
 */

import { NAVIGATE_MESSAGE, safeAppPath } from './app/pushProtocol'
import type { NavigateMessage } from './app/pushProtocol'

declare const self: ServiceWorkerGlobalScope

/** 서버가 보내는 본문(백엔드 NotificationSender.Message) */
interface PushMessage {
  title?: string
  body?: string
  url?: string
  tag?: string
}

// 새 버전은 기다리지 않고 바로 바꾼다. 가로채는 요청이 없어 열린 화면과 버전이 어긋나도 문제없다
self.addEventListener('install', () => {
  void self.skipWaiting()
})

self.addEventListener('push', (event) => {
  let message: PushMessage
  try {
    message = event.data?.json() ?? {}
  } catch {
    // JSON이 아니면 글자 그대로 본문에
    message = { body: event.data?.text() }
  }
  event.waitUntil(
    self.registration.showNotification(message.title ?? '밸런스핏', {
      body: message.body,
      // 같은 tag(reminder, summary, cheer-그룹…)는 쌓이지 않고 새 것으로 바뀐다
      tag: message.tag,
      icon: '/icons/icon-192.png',
      // 안드로이드 상태 표시줄의 작은 그림. 흰 모양만 쓰인다
      badge: '/icons/badge-96.png',
      lang: 'ko',
      data: { url: safeAppPath(message.url) },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = safeAppPath((event.notification.data as { url?: unknown } | null)?.url)
  event.waitUntil(open(url))
})

/**
 * 열린 앱 창이 있으면 앞으로 가져와 그 안에서 옮기고, 없으면 새로 연다.
 *
 * 열린 창을 새로 불러오지 않는 이유: 로그인 상태(액세스 토큰)가 메모리에 있어 새로 불러오면
 * 재발급부터 다시 한다. 화면의 라우터가 옮기면 그대로다.
 */
async function open(url: string) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  const client = windows.find((c) => new URL(c.url).origin === self.location.origin)
  if (client) {
    await client.focus()
    client.postMessage({ type: NAVIGATE_MESSAGE, url } satisfies NavigateMessage)
    return
  }
  await self.clients.openWindow(url)
}
