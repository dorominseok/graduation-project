package com.fitness.backend.notification.push;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * VAPID 키 한 쌍을 만들어 {@code .env}에 붙일 형식으로 찍는다 — {@code ./gradlew vapidKeys}.
 *
 * <p>한 번 만들어 계속 쓴다. 키를 바꾸면 이미 구독한 브라우저는 모두 다시 구독해야 한다.
 * 비밀키는 서버 {@code .env}에만 넣고 저장소·대화·로그에 남기지 않는다.
 */
public final class VapidKeyGenerator {

    private VapidKeyGenerator() {
    }

    public static void main(String[] args) {
        KeyPair pair = EcKeys.generate();
        System.out.println("VAPID_PUBLIC_KEY=" + EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) pair.getPublic())));
        System.out.println("VAPID_PRIVATE_KEY=" + EcKeys.toBase64Url(EcKeys.encode((ECPrivateKey) pair.getPrivate())));
    }
}
