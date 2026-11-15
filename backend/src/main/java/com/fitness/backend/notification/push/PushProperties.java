package com.fitness.backend.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 설정(`app.push.*`). 키는 {@code ./gradlew vapidKeys}로 만들고 비밀키는 서버 {@code .env}에만 둔다.
 *
 * @param publicKey  VAPID 공개키(압축하지 않은 P-256 점 65바이트) base64url
 * @param privateKey VAPID 비밀키(32바이트) base64url. 저장소·로그에 남기지 않는다
 * @param subject    푸시 서버가 문제가 있을 때 연락할 곳. 사이트 주소를 쓴다
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public PushProperties {
        if (blank(publicKey) != blank(privateKey)) {
            throw new IllegalStateException("VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY는 둘 다 넣거나 둘 다 비워야 한다");
        }
    }

    /** 키가 있어 알림을 보낼 수 있는지. 둘 다 비면 보내지 않는다(로컬·테스트 기본) */
    public boolean enabled() {
        return !blank(publicKey);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
