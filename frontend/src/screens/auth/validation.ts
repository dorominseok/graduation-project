/**
 * 폼 검증 규칙. 서버 규칙과 같은 값을 쓴다.
 * 서버가 최종 판단하고, 여기서는 왕복을 줄이기 위한 사전 확인만 한다.
 */

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 72 // BCrypt 입력 상한
export const NICKNAME_MIN = 1
export const NICKNAME_MAX = 50

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
