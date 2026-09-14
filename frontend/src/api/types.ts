/**
 * 백엔드 응답 타입. API 명세서 4장의 DTO를 그대로 옮긴다.
 *
 * 서버가 계약이므로 여기서 모양을 바꾸지 않는다 — 화면에 맞는 형태가 필요하면
 * 화면 쪽에서 가공한다.
 */

/** 훈련 목표. 부록 A `user.goal` (LOG-14). 체중 목표가 아니다. */
export type TrainingGoal = 'STRENGTH' | 'HYPERTROPHY' | 'ENDURANCE' | 'GENERAL_FITNESS'

export const TRAINING_GOAL_LABEL: Record<TrainingGoal, string> = {
  STRENGTH: '근력',
  HYPERTROPHY: '근비대',
  ENDURANCE: '지구력',
  GENERAL_FITNESS: '일반 체력',
}

/** 가입·로그인 응답에 딸려오는 축약 사용자 정보 (명세 4.1). */
export interface UserSummary {
  userId: number
  email: string
  nickname: string
}

/**
 * 가입·로그인 응답 (명세 4.1·4.2).
 *
 * 리프레시 토큰은 여기 없다 — `Set-Cookie`로만 내려간다(명세 2.3).
 */
export interface TokenResponse {
  tokenType: string
  accessToken: string
  /** 액세스 토큰 수명(초). 기본 1800 = 30분. */
  expiresIn: number
  user: UserSummary
}

/** 재발급 응답 (명세 4.3). 사용자 정보는 담기지 않는다. */
export interface RefreshResponse {
  tokenType: string
  accessToken: string
  expiresIn: number
}

/** 프로필. 항목이 하나여도 객체로 감싼 형태를 유지한다(명세 4.5). */
export interface Profile {
  goal: TrainingGoal | null
}

/** 프로필 조회·수정 응답 (명세 4.5·4.6). */
export interface MeResponse {
  userId: number
  email: string
  nickname: string
  profile: Profile
  createdAt: string
}

/**
 * 프로필 부분 수정 요청 (명세 4.6).
 *
 * `profile` 키가 없으면 프로필을 건드리지 않고, 있으면 그 안의 `goal`로 설정한다.
 * `goal: null`은 미설정으로 되돌리라는 뜻이다 — "없음"과 "지움"이 다르다.
 */
export interface UpdateMeRequest {
  nickname?: string
  profile?: { goal: TrainingGoal | null }
}
