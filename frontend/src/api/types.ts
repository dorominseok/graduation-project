/**
 * 백엔드 응답 타입. API 명세서 4장의 DTO를 그대로 옮긴다.
 *
 * 서버가 계약이므로 여기서 모양을 바꾸지 않는다 — 화면에 맞는 형태가 필요하면
 * 화면 쪽에서 가공한다.
 */

/**
 * 훈련 목표. 부록 A `user.goal`.
 *
 * 화면에서는 쓰지 않는다(2026-09-20). 계획서에 없던 항목이고 루틴 추천 입력에도
 * 없어서 표시·수정 화면을 뺐다. 타입만 남긴 이유는 서버 응답(4.5)에 아직 이 필드가
 * 있어서다 — 서버에서 컬럼을 걷어내면 함께 지운다.
 */
export type TrainingGoal = 'STRENGTH' | 'HYPERTROPHY' | 'ENDURANCE' | 'GENERAL_FITNESS'

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
 * `profile`은 보내지 않는다 — 키를 빼면 서버가 프로필을 건드리지 않는다.
 * 화면이 다루는 항목은 닉네임뿐이다.
 */
export interface UpdateMeRequest {
  nickname?: string
}

/**
 * 비밀번호 변경 요청 (명세 4.8).
 *
 * 성공하면 서버가 리프레시 토큰을 전부 폐기하고 쿠키를 만료시킨다 — 바꾼 본인도
 * 다시 로그인해야 한다.
 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
