package com.fitness.backend.auth.service;

/**
 * 발급 결과. 리프레시 토큰 <b>원문</b>은 이 순간에만 존재한다 — DB에는 해시만
 * 남으므로 여기서 쿠키에 싣지 못하면 영영 알 수 없다.
 *
 * @param accessToken      액세스 토큰(JWT)
 * @param expiresInSeconds 액세스 토큰 만료까지 남은 초. 응답의 {@code expiresIn}
 * @param refreshTokenRaw  리프레시 토큰 원문. 쿠키에만 싣고 응답 본문에는 넣지 않는다
 */
public record TokenPair(String accessToken, long expiresInSeconds, String refreshTokenRaw) {
}
