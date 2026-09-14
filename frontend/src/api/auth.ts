/**
 * 인증·프로필 엔드포인트. 9월 2주차에 구현된 7개 (명세 4.1~4.7).
 *
 * 각 함수는 호출만 담당하고 토큰 저장은 하지 않는다 — 상태 관리는 AuthProvider의
 * 일이다. 여기서 토큰까지 만지면 로그인 흐름이 두 곳에 나뉜다.
 */

import { request } from './client'
import { clearAccessToken } from './tokenStore'
import type { ChangePasswordRequest, MeResponse, TokenResponse, UpdateMeRequest } from './types'

export interface SignUpBody {
  email: string
  password: string
  nickname: string
}

export interface LoginBody {
  email: string
  password: string
}

/** 가입. 성공하면 곧바로 로그인 상태가 되도록 토큰이 함께 온다(명세 4.1). */
export function signUp(body: SignUpBody): Promise<TokenResponse> {
  return request<TokenResponse>('/auth/signup', { method: 'POST', body, anonymous: true })
}

/**
 * 로그인 (명세 4.2).
 *
 * 실패는 이메일이 없을 때와 비밀번호가 틀렸을 때가 같은 `INVALID_CREDENTIALS`로 온다.
 * 화면도 구분하지 말고 한 문구로 보여줘야 한다 — 구분하면 가입 여부가 드러난다.
 */
export function login(body: LoginBody): Promise<TokenResponse> {
  return request<TokenResponse>('/auth/login', { method: 'POST', body, anonymous: true })
}

/**
 * 로그아웃 (명세 4.4). 서버가 리프레시를 폐기하고 쿠키를 만료시킨다.
 *
 * 서버 호출이 실패해도 로컬 토큰은 지운다. 사용자가 로그아웃을 눌렀는데 화면이
 * 로그인 상태로 남아 있는 것이 더 나쁘기 때문이다.
 */
export async function logout(): Promise<void> {
  try {
    await request<void>('/auth/logout', { method: 'POST' })
  } finally {
    clearAccessToken()
  }
}

/** 내 프로필 (명세 4.5). */
export function getMe(): Promise<MeResponse> {
  return request<MeResponse>('/users/me')
}

/** 프로필 부분 수정 (명세 4.6). */
export function updateMe(body: UpdateMeRequest): Promise<MeResponse> {
  return request<MeResponse>('/users/me', { method: 'PATCH', body })
}

/**
 * 비밀번호 변경 (명세 4.8).
 *
 * 성공하면 서버가 리프레시 토큰을 전부 폐기하고 쿠키를 만료시키므로 이 세션은 더
 * 이어갈 수 없다. 다만 액세스 토큰은 남은 시간 동안 유효하므로 여기서 지우지 않는다
 * — 호출부가 곧바로 `logout()`을 부르면 그 토큰으로 정상 종료할 수 있다.
 */
export function changePassword(body: ChangePasswordRequest): Promise<void> {
  return request<void>('/users/me/password', { method: 'PATCH', body })
}

/** 회원 탈퇴 (명세 4.7). 되돌릴 수 없어 비밀번호를 다시 받는다. */
export async function deleteMe(password: string): Promise<void> {
  await request<void>('/users/me', { method: 'DELETE', body: { password } })
  clearAccessToken()
}
