/**
 * 친구 그룹.
 *
 * 멤버가 아닌 그룹은 서버가 404로 답한다 — 그룹이 있는지조차 드러내지 않으려는 것이라,
 * 화면도 "없는 그룹"과 "내 그룹이 아님"을 구분하지 않는다.
 */

import { request } from './client'
import type {
  CalendarResponse,
  CheerState,
  GroupDetail,
  GroupSummary,
  InvitePreview,
  JoinResult,
  MemberDay,
} from './types'

export function getMyGroups(): Promise<GroupSummary[]> {
  return request<GroupSummary[]>('/groups')
}

/** 만든 사람이 방장이자 첫 멤버다. 초대코드가 담긴 상세가 바로 온다. */
export function createGroup(name: string): Promise<GroupDetail> {
  return request<GroupDetail>('/groups', { method: 'POST', body: { name } })
}

export function getGroup(groupId: number): Promise<GroupDetail> {
  return request<GroupDetail>(`/groups/${groupId}`)
}

/** 방장만. */
export function renameGroup(groupId: number, name: string): Promise<GroupDetail> {
  return request<GroupDetail>(`/groups/${groupId}`, { method: 'PATCH', body: { name } })
}

/** 방장만. 멤버들의 운동 기록은 그대로 남는다. */
export function deleteGroup(groupId: number): Promise<void> {
  return request<void>(`/groups/${groupId}`, { method: 'DELETE' })
}

/** 방장만. 옛 코드와 링크로는 더 들어올 수 없다. */
export function regenerateInviteCode(groupId: number): Promise<{ inviteCode: string }> {
  return request<{ inviteCode: string }>(`/groups/${groupId}/invite-code`, { method: 'POST' })
}

export function previewInvite(code: string): Promise<InvitePreview> {
  return request<InvitePreview>(`/groups/invite/${encodeURIComponent(code)}`)
}

/** 이미 멤버여도 오류가 아니다 — `alreadyMember`로 온다. */
export function joinGroup(code: string): Promise<JoinResult> {
  return request<JoinResult>('/groups/join', { method: 'POST', body: { code } })
}

/** 방장이 나가면 가장 먼저 들어온 멤버가 방장이 되고, 마지막 한 명이면 그룹이 지워진다. */
export function leaveGroup(groupId: number): Promise<void> {
  return request<void>(`/groups/${groupId}/members/me`, { method: 'DELETE' })
}

/** 방장만. */
export function removeMember(groupId: number, memberId: number): Promise<void> {
  return request<void>(`/groups/${groupId}/members/${memberId}`, { method: 'DELETE' })
}

/** 멤버의 월별 기록. 내 캘린더와 같은 모양이다. */
export function getMemberCalendar(
  groupId: number,
  memberId: number,
  year: number,
  month: number,
): Promise<CalendarResponse> {
  return request<CalendarResponse>(
    `/groups/${groupId}/members/${memberId}/calendar?year=${year}&month=${month}`,
  )
}

/** 멤버의 하루 기록. 메모는 오지 않는다. */
export function getMemberDay(groupId: number, memberId: number, date: string): Promise<MemberDay> {
  return request<MemberDay>(`/groups/${groupId}/members/${memberId}/days/${date}`)
}

/** 두 번 눌러도 한 번이다. 응원 뒤의 수가 온다. */
export function cheer(groupId: number, sessionId: number): Promise<CheerState> {
  return request<CheerState>(`/groups/${groupId}/sessions/${sessionId}/cheer`, { method: 'PUT' })
}

export function cancelCheer(groupId: number, sessionId: number): Promise<CheerState> {
  return request<CheerState>(`/groups/${groupId}/sessions/${sessionId}/cheer`, { method: 'DELETE' })
}
