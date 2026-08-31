/**
 * 폼 검증 규칙. API 명세서 4.1의 서버 규칙과 같은 값을 쓴다.
 * 서버가 최종 판단하고, 여기서는 왕복을 줄이기 위한 사전 확인만 한다.
 */

export const EMAIL_MAX = 255
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 72 // BCrypt 입력 상한
export const NICKNAME_MIN = 1
export const NICKNAME_MAX = 50

// 서버 검증이 최종이므로 형식만 거르는 느슨한 패턴을 쓴다.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string): string | null {
  if (!value) return '이메일을 입력해주세요'
  if (value.length > EMAIL_MAX) return `이메일은 ${EMAIL_MAX}자를 넘을 수 없어요`
  if (!EMAIL_PATTERN.test(value)) return '이메일 형식이 올바르지 않아요'
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) return '비밀번호를 입력해주세요'
  if (value.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상이어야 해요`
  if (value.length > PASSWORD_MAX) return `비밀번호는 ${PASSWORD_MAX}자를 넘을 수 없어요`
  return null
}

export function validateNickname(value: string): string | null {
  if (value.length < NICKNAME_MIN) return '닉네임을 입력해주세요'
  if (value.length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MAX}자를 넘을 수 없어요`
  return null
}
