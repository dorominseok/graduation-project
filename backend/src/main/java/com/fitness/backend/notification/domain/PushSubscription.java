package com.fitness.backend.notification.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 푸시 구독 — 브라우저(기기)마다 하나. 브라우저가 준 푸시 주소와 암호화 키를 그대로 둔다.
 *
 * <p>푸시 주소가 유일하다. 같은 브라우저로 다른 계정에 로그인해 다시 구독하면 주소는 같고 주인만 바뀐다
 * ({@link #reassign}).
 */
@Entity
@Getter
@Table(name = "push_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    /** 브라우저의 공개키(P-256, 압축하지 않은 점 65바이트) base64url */
    @Column(nullable = false, length = 100)
    private String p256dh;

    /** 인증 비밀(16바이트) base64url */
    @Column(nullable = false, length = 50)
    private String auth;

    private PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription of(Long userId, String endpoint, String p256dh, String auth) {
        return new PushSubscription(userId, endpoint, p256dh, auth);
    }

    /** 같은 브라우저를 다른 계정이 쓰게 됐거나 브라우저가 키를 바꿨을 때 */
    public void reassign(Long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
