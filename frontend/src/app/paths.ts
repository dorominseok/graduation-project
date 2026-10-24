/**
 * 라우트 경로 한 곳 정의.
 *
 * 목업의 appView 13종을 옮겼다. 다만 "미래 날짜 루틴 미리 짜기" 화면은
 * 옮기지 않는다 — 기록은 미래 날짜를 받지 않고, 루틴은 오늘 하루 것만 만든다
 * (LOG-05, LOG-40).
 */
export const paths = {
  // 탭 밖 — 인증
  login: '/login',
  signup: '/signup',
  privacy: '/privacy',

  // 탭 안
  home: '/',
  history: '/history',
  analysis: '/analysis',
  group: '/group',
  profile: '/profile',

  // 탭 밖 — 하단바를 보일지는 router.tsx가 정한다
  session: '/session',
  sessionDetail: (sessionId: number | string = ':sessionId') => `/session/${sessionId}`,
  routineDetail: (routineId: number | string = ':routineId') => `/routines/${routineId}`,
  exerciseList: '/exercises',
  exerciseDetail: (exerciseId: number | string = ':exerciseId') => `/exercises/${exerciseId}`,
  settings: '/settings',
  accountSettings: '/settings/account',
  personalInfo: '/settings/personal',
  feedback: '/feedback',

  // 친구 그룹 (LOG-38). 그룹 탭 아래에 둬서 하단바의 "그룹"에 불이 켜진다
  groupDetail: (groupId: number | string = ':groupId') => `/group/${groupId}`,
  groupMember: (groupId: number | string = ':groupId', memberId: number | string = ':memberId') =>
    `/group/${groupId}/members/${memberId}`,
  groupMemberDay: (
    groupId: number | string = ':groupId',
    memberId: number | string = ':memberId',
    date: string = ':date',
  ) => `/group/${groupId}/members/${memberId}/days/${date}`,
  /** 초대 링크. 카톡 등으로 보내는 주소라 짧게 둔다 */
  join: (code: string = ':code') => `/join/${code}`,
} as const
