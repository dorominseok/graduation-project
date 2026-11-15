/**
 * 알림 설정과 기기 등록(API 명세 12장). 알림을 보내는 쪽은 서버의 예약 작업과 그룹 일이고,
 * 화면은 설정을 바꾸고 이 기기를 등록·해제만 한다. 기기 쪽 일(권한·구독)은 `app/push.ts`.
 */

import { request } from './client'
import type { NotificationSettings, PushSubscriptionRequest, UpdateNotificationSettingsRequest } from './types'

/** 저장한 적이 없으면 기본값이 온다(`saved: false`) */
export function getSettings(): Promise<NotificationSettings> {
  return request<NotificationSettings>('/users/me/notification-settings')
}

/** 시각은 08:00~21:30 · 30분 단위, 리마인더를 켜면 요일이 하나 이상이어야 한다(아니면 400) */
export function updateSettings(body: UpdateNotificationSettingsRequest): Promise<NotificationSettings> {
  return request<NotificationSettings>('/users/me/notification-settings', { method: 'PUT', body })
}

/** 이 기기를 등록한다. 처음이면 서버가 설정을 기본값으로 저장한다. 같은 주소면 주인만 바뀐다 */
export function subscribe(body: PushSubscriptionRequest): Promise<NotificationSettings> {
  return request<NotificationSettings>('/users/me/push-subscriptions', { method: 'POST', body })
}

/** 내 기기가 아니거나 없으면 아무 일도 하지 않는다(204) */
export function unsubscribe(endpoint: string): Promise<void> {
  return request<void>('/users/me/push-subscriptions', { method: 'DELETE', body: { endpoint } })
}
