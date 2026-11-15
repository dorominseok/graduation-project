package com.fitness.backend.notification.push;

import java.time.Duration;

/** 푸시 하나를 브라우저 회사의 푸시 서버로 보낸다. 테스트는 보낸 것을 모으는 가짜로 바꾼다 */
public interface PushGateway {

    enum Result {
        /** 푸시 서버가 받았다 */
        DELIVERED,
        /** 구독이 없어졌다(404·410) — 지운다 */
        GONE,
        /** 일시적으로 실패했다(네트워크·429·5xx) — 구독은 둔다 */
        FAILED,
        /** 서버에 VAPID 키가 없어 보내지 않았다(로컬 기본) */
        SKIPPED
    }

    /** 브라우저가 구독할 때 준 값 */
    record Target(String endpoint, String p256dh, String auth) {
    }

    /**
     * @param payload 서비스워커가 받을 JSON(UTF-8)
     * @param ttl     푸시 서버가 기기를 기다려 줄 시간. 지나면 버린다
     */
    Result send(Target target, byte[] payload, Duration ttl);

    /** 브라우저가 구독할 때 쓰는 공개키. 키가 없으면 null */
    String publicKey();
}
