/**
 * 라우트 경로 한 곳 정의.
 *
 * 목업의 appView 13종을 옮겼다. 다만 "미래 날짜 루틴 미리 짜기" 화면은
 * 옮기지 않는다 — workout_sessions는 미래 날짜를 받지 않고, 계획은 10월
 * routines 테이블의 일이기 때문이다(API 명세서 9.2, LOG-05).
 */
export const paths = {
  // 탭 밖 — 인증
  login: '/login',
  signup: '/signup',

  // 탭 안
  home: '/',
  history: '/history',
  analysis: '/analysis',
  group: '/group',
  profile: '/profile',

  // 탭 밖 — 전체 화면
  session: '/session',
  sessionDetail: (sessionId: number | string = ':sessionId') => `/session/${sessionId}`,
  routineDetail: (routineId: number | string = ':routineId') => `/routines/${routineId}`,
  routineFallback: '/routines/fallback',
  exerciseList: '/exercises',
  exerciseDetail: (exerciseId: number | string = ':exerciseId') => `/exercises/${exerciseId}`,
  settings: '/settings',
  accountSettings: '/settings/account',
  personalInfo: '/settings/personal',
  feedback: '/feedback',
} as const
