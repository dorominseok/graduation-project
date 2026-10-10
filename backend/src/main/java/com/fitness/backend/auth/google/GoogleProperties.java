package com.fitness.backend.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 로그인 설정.
 *
 * @param clientId Google Cloud에서 만든 웹 클라이언트 ID. 공개값이다 — 화면에도 같은 값이 들어간다.
 *                 구글이 발급한 ID 토큰의 {@code aud}가 이 값이어야 우리 앱에 발급된 것이다.
 *                 비어 있으면 구글 로그인은 꺼진다
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(String clientId) {

    public boolean enabled() {
        return clientId != null && !clientId.isBlank();
    }
}
