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

/**
 * 이 계정의 로그인 방법 (LOG-37).
 *
 * 구글로만 가입한 계정은 비밀번호가 없다 — 비밀번호 변경을 숨기고, 탈퇴 확인을 구글로 받는다.
 */
export interface LoginMethods {
  password: boolean
  google: boolean
}

/** 프로필 조회·수정 응답 (명세 4.5·4.6). */
export interface MeResponse {
  userId: number
  email: string
  nickname: string
  profile: Profile
  loginMethods: LoginMethods
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
  | 'SHOULDERS_FRONT'
  | 'SHOULDERS_REAR'
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
  /** 계열 카드 그림의 파일명을 만드는 데 쓴다 (LOG-24) */
  representativeNameEn: string | null
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

/** 기록 상세 안의 세트 (명세 6.5). */
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

/**
 * 하루 기록 (명세 6.5, LOG-39). 사용자의 하루에 하나이고 시작·종료가 없다.
 * 시각과 시간은 서버가 세트 저장 시각으로 계산한다.
 */
export interface WorkoutSession {
  id: number
  performedOn: string
  routineId: number | null
  /** 첫 세트 저장 시각. 세트가 없으면 null */
  startedAt: string | null
  /** 마지막 세트 저장 시각 */
  endedAt: string | null
  /** 30분 넘는 공백에서 나눈 구간들의 합. 사후 입력만 있으면 null */
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

/** 그날 기록 열기 (명세 6.2). 날짜를 생략하면 오늘이다 — 자정을 넘긴 운동이면 서버가 어제로 잇는다 */
export interface OpenDayRequest {
  performedOn?: string
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
 * 하루 기록 수정 요청 (명세 6.9). 기록이 이미 있는 날짜로는 옮길 수 없다(409).
 *
 * 값을 비울 때는 `null`이 아니라 빈 문자열과 `0`을 보낸다(LOG-23) — JSON에서
 * "안 보냄"과 "null"이 서버에서 구분되지 않기 때문이다.
 */
export interface UpdateSessionRequest {
  memo?: string
  performedOn?: string
  durationOverrideSec?: number
}

/** 히스토리 한 건 (명세 6.6). 운동한 날 하나다. 세트는 담기지 않는다. */
export interface SessionSummary {
  id: number
  performedOn: string
  effectiveDurationSec: number | null
  setCount: number
  exerciseCount: number
  exercises: { id: number; nameKo: string }[]
}

/** 캘린더 월별 요약 (명세 6.8). 운동한 날만 온다. */
export interface CalendarResponse {
  year: number
  month: number
  days: { date: string }[]
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

// ── 통계 (명세 7장)

/** 종목 하나의 추정 1RM 추이 (명세 7.1). 기록이 없는 날은 점이 없다 */
export interface OneRmTrend {
  exerciseId: number
  exerciseName: string
  unit: 'kg'
  from: string
  to: string
  points: {
    date: string
    estimatedOneRm: number
    /** 그날 최댓값을 만든 세트. 점을 눌렀을 때 근거로 보여준다 */
    basedOnSet: { weightKg: number; reps: number }
  }[]
}

/** 세션 하나의 세트별 강도 (명세 7.2). */
export interface SessionIntensity {
  sessionId: number
  performedOn: string
  exercises: {
    exerciseId: number
    exerciseName: string
    measureType: MeasureType
    /** 중량·횟수 종목이 아니거나 12회 이하 세트가 없으면 null */
    estimatedOneRm: number | null
    sets: {
      setNo: number
      weightKg: number | null
      reps: number | null
      durationSec: number | null
      isWarmup: boolean
      intensityPct: number | null
    }[]
  }[]
}

// ── 분석 (명세 8장)

export type VolumeVerdict = 'INSUFFICIENT' | 'BELOW_RECOMMENDED' | 'OPTIMAL' | 'EXCESSIVE'
export type SummaryBadge =
  | 'ALL_OPTIMAL'
  | 'PARTIAL_BELOW'
  | 'PARTIAL_INSUFFICIENT'
  | 'PARTIAL_EXCESSIVE'
  | 'MIXED'
export type TierKey = 'CHEST' | 'BACK' | 'SHOULDERS' | 'ARMS' | 'LEGS' | 'CORE'
export type MuscleGroupKey =
  | 'CHEST'
  | 'BACK'
  | 'DELT_FRONT'
  | 'DELT_REAR'
  | 'TRICEPS'
  | 'BICEPS'
  | 'QUADS'
  | 'POSTERIOR'
  | 'CORE'
  | 'CALVES'
  | 'FOREARMS'

/**
 * 부위별 볼륨·부족 판정 (명세 8.2).
 *
 * 화면은 합산·구간 분류·배지·신뢰도를 계산하지 않는다. 받은 값을 그리기만 한다.
 */
export interface MuscleVolume {
  referenceDate: string
  periodWeeks: number
  periodFrom: string
  periodTo: string
  /**
   * 주당 평균을 낼 때 나눈 일수 (LOG-31). 기록이 4주 미만이면 첫 기록일부터 센
   * 일수(최소 7), 아니면 28. 기록이 짧은 사람을 28일로 나누면 판정이 희석된다
   */
  basisDays: number
  /** basisDays의 시작일 */
  basisFrom: string
  shoulderSplitResolved: boolean
  confidence: {
    level: 'LOW' | 'NORMAL'
    /** 최근 집계 기간에 운동한 날 수 */
    workoutDayCount: number
    threshold: number
    /** LOW일 때만 온다 */
    message: string | null
  }
  /** 항상 6개, 서버가 정한 순서 */
  tiers: {
    key: TierKey
    label: string
    weeklySets: number
    totalSets: number
    hasChildren: boolean
    /** 하위가 하나일 때만. 둘이면 null — 합계가 한쪽 0을 가리지 않게 */
    verdict: VolumeVerdict | null
    verdictLabel: string | null
    /** 하위가 둘일 때만 */
    summaryBadge: SummaryBadge | null
    summaryBadgeLabel: string | null
    children: {
      key: MuscleGroupKey
      label: string
      weeklySets: number
      totalSets: number
      verdict: VolumeVerdict
      verdictLabel: string
    }[]
  }[]
  /** 종아리·전완. 판정하지 않는다 */
  displayOnly: { key: MuscleGroupKey; label: string; weeklySets: number; totalSets: number }[]
}

export type BalanceVerdict = 'BALANCED' | 'IMBALANCED' | 'INSUFFICIENT_DATA'
export type BalanceSideKey = 'PUSH' | 'PULL' | 'UPPER' | 'LOWER'

export interface BalanceSide {
  key: BalanceSideKey
  label: string
  weeklySets: number
  /** 합산에 쓴 판정 부위. 근거로 보여준다 */
  components: MuscleGroupKey[]
}

/** 밀기/당기기 · 상체/하체 균형 (명세 8.3). */
export interface Balance {
  referenceDate: string
  periodWeeks: number
  /** muscle-volume과 같은 분모 */
  basisDays: number
  ratioThreshold: number
  shoulderSplitResolved: boolean
  pairs: {
    key: 'PUSH_PULL' | 'UPPER_LOWER'
    label: string
    left: BalanceSide
    right: BalanceSide
    /** 양쪽 다 0이면 null */
    biggerSide: BalanceSideKey | null
    /** 작은 쪽이 0이면 null */
    ratio: number | null
    smallerSideZero: boolean
    verdict: BalanceVerdict
    verdictLabel: string
  }[]
}

// ── 친구 그룹 (명세 10장, LOG-38)

/** 내 그룹 목록 한 줄. */
export interface GroupSummary {
  id: number
  name: string
  memberCount: number
  isOwner: boolean
  /** 오늘 운동한 멤버 수 */
  todayWorkedOutCount: number
  /** 목록 카드의 멤버 얼굴. 들어온 순서 */
  members: { userId: number; nickname: string; isMe: boolean; todayStatus: TodayStatus }[]
}

/**
 * 오늘 운동했나. 손으로 체크하지 않고 기록에서 정한다(LOG-38) — 오늘 세트가 하나라도 있으면 DONE.
 * 종료가 없으므로 "운동 중"이라는 상태도 없다(LOG-39).
 */
export type TodayStatus = 'DONE' | 'NONE'

export interface MemberActivity {
  userId: number
  nickname: string
  isOwner: boolean
  isMe: boolean
  joinedAt: string
  today: {
    status: TodayStatus
    /** 응원할 대상. 운동하지 않았으면 null */
    sessionId: number | null
    cheerCount: number
    cheeredByMe: boolean
  }
  /** 이번 주(월~일) */
  week: {
    /** 운동한 날 수 */
    dayCount: number
    /** 시간 없는 사후 입력은 빠진다 */
    totalDurationSec: number
    workoutDates: string[]
  }
}

/** 그룹 상세 — 멤버별 오늘·이번 주. 멤버는 들어온 순서다. */
export interface GroupDetail {
  id: number
  name: string
  /** 멤버 누구나 받는다. 다시 만드는 것만 방장이 한다 */
  inviteCode: string
  isOwner: boolean
  today: string
  /** 이번 주 월요일 */
  weekStart: string
  members: MemberActivity[]
}

/** 초대 링크로 들어왔을 때 참여 전에 보여줄 정보. */
export interface InvitePreview {
  groupId: number
  name: string
  memberCount: number
  ownerNickname: string | null
  alreadyMember: boolean
  full: boolean
}

export interface JoinResult {
  groupId: number
  name: string
  alreadyMember: boolean
}

export interface CheerState {
  cheerCount: number
  cheeredByMe: boolean
}

/** 멤버의 하루 기록. 기록 상세(명세 6.5)에서 메모·세트 id를 뺀 것이다. */
export interface MemberSession extends CheerState {
  id: number
  startedAt: string | null
  endedAt: string | null
  /** 보정값 우선. 시간 없는 사후 입력은 null */
  durationSec: number | null
  exercises: {
    exerciseId: number
    exerciseName: string
    /** 동작 그림 파일 이름(exerciseImage)을 만든다 */
    exerciseNameEn: string | null
    measureType: MeasureType
    sets: {
      setNo: number
      weightKg: number | null
      reps: number | null
      durationSec: number | null
      isWarmup: boolean
    }[]
  }[]
}

/** 멤버의 하루 기록(읽기 전용). 그날 운동하지 않았으면 session이 null이다. */
export interface MemberDay {
  userId: number
  nickname: string
  date: string
  session: MemberSession | null
}
