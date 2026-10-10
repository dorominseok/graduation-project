package com.fitness.backend.auth.google;

/**
 * 구글이 서명해 보증한 사용자 정보. ID 토큰에서 검증을 마친 값만 담는다.
 *
 * @param subject       구글 회원번호({@code sub}). 바뀌지 않으므로 계정은 이걸로 찾는다
 * @param email         구글 계정의 이메일. 바뀔 수 있다
 * @param emailVerified 구글이 이메일 소유를 확인했는가. 기존 계정에 연결할 때의 조건이다
 * @param name          구글 프로필 이름. 없을 수 있다
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
