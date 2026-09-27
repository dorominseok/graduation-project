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

// ── 운동 종목 (명세 5장)

/** 표시 부위 6종 (명세 5.1). 판정 부위 9종과는 다른 축이다. */
export type BodyPart = 'CHEST' | 'BACK' | 'LEGS' | 'SHOULDERS' | 'ARMS' | 'CORE'

export type Equipment = 'BARBELL' | 'DUMBBELL' | 'MACHINE' | 'CABLE' | 'BODYWEIGHT' | 'PULLUP_BAR'

/** 세트에 무엇을 입력받을지 정한다 (명세 5.1 표). */
export type MeasureType = 'WEIGHT_REPS' | 'BODYWEIGHT_REPS' | 'WEIGHTED_BODYWEIGHT' | 'TIME'

export type PushPull = 'PUSH' | 'PULL' | 'NONE'

/**
 * 종목 탐색 분류 12종 (LOG-24).
 *
 * 표시 부위 6종(`BodyPart`)이나 판정 부위 9종과 다른 축이다 — 저쪽 둘은 집계·판정
 * 기준이고 이쪽은 사용자가 종목을 찾을 때 떠올리는 묶음이다.
 */
export type BrowseCategory =
  | 'CHEST'
  | 'BACK'
  | 'SHOULDERS'
  | 'TRAPS'
  | 'TRICEPS'
  | 'BICEPS'
  | 'FOREARMS'
  | 'ABS'
  | 'LOWER_BACK'
  | 'GLUTES'
  | 'LEGS'
  | 'CALVES'

/** 부위 그리드의 한 칸 (LOG-24). 종목이 0개인 분류도 내려온다. */
export interface CategoryCount {
  category: BrowseCategory
  label: string
  count: number
}

/** 계열 목록의 한 칸 (LOG-24). `representativeId`는 카드 이미지에 쓸 대표 종목이다. */
export interface GroupCount {
  groupName: string
  count: number
  representativeId: number | null
}

/** 종목 한 건 (명세 5.2·5.3). `isFavorite`는 인증된 요청에만 담긴다. */
export interface Exercise {
  id: number
  nameKo: string
  nameEn: string | null
  bodyPart: BodyPart
  primaryMuscle: string | null
  pushPull: PushPull
  measureType: MeasureType
  equipment: Equipment
  deltRegion: 'FRONT' | 'REAR' | null
  /** 같은 동작의 변형 묶음 (V6, LOG-24) */
  groupName: string | null
  isFavorite?: boolean
}

/** 페이지 envelope (명세 1.4). */
export interface Page<T> {
  content: T[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
  }
}

// ── 운동 기록 (명세 6장)

export type SessionStatus = 'DRAFT' | 'DONE'

/** 입력 경로. 화면에서 고르게 하지 않고 진입 경로로 정한다 (명세 6.2). */
export type SessionSource = 'LIVE' | 'BACKFILL'

/** 세션 상세 안의 세트 (명세 6.5). */
export interface SetInGroup {
  id: number
  clientSetId: string
  setNo: number
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  isWarmup: boolean
  recordedAt: string
}

/** 종목별로 묶은 세트. 수행 순서대로 온다 (명세 6.5). */
export interface ExerciseGroup {
  exerciseId: number
  exerciseName: string
  measureType: MeasureType
  sets: SetInGroup[]
}

/** 세션 상세 (명세 6.5). */
export interface WorkoutSession {
  id: number
  performedOn: string
  status: SessionStatus
  source: SessionSource
  routineId: number | null
  startedAt: string | null
  endedAt: string | null
  durationSec: number | null
  durationOverrideSec: number | null
  effectiveDurationSec: number | null
  memo: string | null
  createdAt: string
  updatedAt: string
  exercises: ExerciseGroup[]
}

/** 세트 저장 응답 (명세 6.4). */
export interface WorkoutSet {
  id: number
  sessionId: number
  clientSetId: string
  exerciseId: number
  exerciseName: string
  setNo: number
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  isWarmup: boolean
  recordedAt: string
}

export interface CreateSessionRequest {
  performedOn: string
  source?: SessionSource
  routineId?: number | null
}

/**
 * 세트 저장 요청 (명세 6.4).
 *
 * `clientSetId`는 완료 체크 시점에 만드는 멱등 키다. 재전송돼도 세트가 두 번
 * 쌓이지 않게 하려면 화면이 만들어 보내야 한다 — 서버가 만들 수 없다.
 */
export interface CreateSetRequest {
  clientSetId: string
  exerciseId: number
  weightKg?: number | null
  reps?: number | null
  durationSec?: number | null
  isWarmup?: boolean
  setNo?: number | null
}

/** 세트 수정 요청 (명세 6.11). 보내지 않은 항목은 그대로 둔다. */
export interface UpdateSetRequest {
  weightKg?: number
  reps?: number
  durationSec?: number
  isWarmup?: boolean
}

/**
 * 세션 수정 요청 (명세 6.9).
 *
 * 값을 비울 때는 `null`이 아니라 빈 문자열과 `0`을 보낸다(LOG-23) — JSON에서
 * "안 보냄"과 "null"이 서버에서 구분되지 않기 때문이다.
 */
export interface UpdateSessionRequest {
  memo?: string
  performedOn?: string
  durationOverrideSec?: number
}

/** 히스토리 한 건 (명세 6.6). 세트는 담기지 않는다. */
export interface SessionSummary {
  id: number
  performedOn: string
  status: SessionStatus
  source: SessionSource
  effectiveDurationSec: number | null
  setCount: number
  exerciseCount: number
  exercises: { id: number; nameKo: string }[]
}

/** 캘린더 월별 요약 (명세 6.8). */
export interface CalendarResponse {
  year: number
  month: number
  days: { date: string; sessionCount: number; hasDraft: boolean }[]
}

/** 직전 수행 기록 (명세 5.5). 종목을 고르는 순간 세트 행을 채우는 데 쓴다. */
export interface LastPerformance {
  exerciseId: number
  exerciseName: string
  measureType: MeasureType
  sessionId: number
  performedOn: string
  sets: {
    setNo: number
    weightKg: number | null
    reps: number | null
    durationSec: number | null
    isWarmup: boolean
  }[]
}
