/**
 * 날짜·시간 표기. 서버는 날짜를 "YYYY-MM-DD", 시간을 초로 준다.
 */

const WEEKDAY_SHORT = ['일', '월', '화', '수', '목', '금', '토']

/** 로컬 날짜를 YYYY-MM-DD로. toISOString은 UTC로 바꿔 하루가 밀린다 */
export function toDateKey(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

/** "2026-10-11" → 로컬 자정의 Date. new Date("2026-10-11")은 UTC로 읽혀 하루가 밀릴 수 있다 */
export function fromDateKey(key: string): Date {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** "2026-10-11" → "10월 11일 (일)" */
export function formatDayLabel(key: string): string {
  const date = fromDateKey(key)
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAY_SHORT[date.getDay()]})`
}

/** 휴식 시간 "1:30" */
export function formatRest(seconds: number): string {
  const mm = Math.floor(seconds / 60)
  const ss = String(seconds % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

/** 운동 시간 합. 1시간 넘으면 "1시간 5분", 안 되면 "45분", 1분이 안 되면 "1분 미만" */
export function formatDuration(totalSec: number): string {
  if (totalSec <= 0) return '0분'
  const minutes = Math.floor(totalSec / 60)
  if (minutes === 0) return '1분 미만'
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${minutes}분`
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}
