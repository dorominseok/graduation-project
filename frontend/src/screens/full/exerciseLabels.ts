/**
 * 종목 열거값을 화면에 적을 한국어로 바꾸는 표.
 *
 * <p>종목 고르기·기록·상세가 같은 값을 쓰므로 한 곳에 둔다. 세 곳에 따로
 * 적어두면 한 군데만 고쳐져 같은 종목이 화면마다 다르게 불린다.
 */

export const BODY_PART_LABEL: Record<string, string> = {
  CHEST: '가슴',
  BACK: '등',
  SHOULDERS: '어깨',
  ARMS: '팔',
  LEGS: '하체',
  CORE: '코어',
}

export const EQUIPMENT_LABEL: Record<string, string> = {
  BARBELL: '바벨',
  DUMBBELL: '덤벨',
  MACHINE: '머신',
  CABLE: '케이블',
  BODYWEIGHT: '맨몸',
  PULLUP_BAR: '철봉',
}

/** 카드 안 알약용. 좁은 자리에 들어가야 해서 기호 없이 붙여 쓴다 */
export const MEASURE_CHIP_LABEL: Record<string, string> = {
  WEIGHT_REPS: '중량+횟수',
  BODYWEIGHT_REPS: '횟수만',
  WEIGHTED_BODYWEIGHT: '추가중량+횟수',
  TIME: '시간',
}

/** 상세 화면의 설명줄용. 자리가 넉넉해 무엇을 적는지 풀어 쓴다 */
export const MEASURE_LABEL: Record<string, string> = {
  WEIGHT_REPS: '무게 × 횟수',
  BODYWEIGHT_REPS: '맨몸 × 횟수',
  WEIGHTED_BODYWEIGHT: '중량 추가 × 횟수',
  TIME: '시간',
}

export const PUSH_PULL_LABEL: Record<string, string> = {
  PUSH: '밀기',
  PULL: '당기기',
  NONE: '해당 없음',
}
