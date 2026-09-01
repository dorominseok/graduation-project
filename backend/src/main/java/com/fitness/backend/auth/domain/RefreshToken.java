package com.fitness.backend.auth.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰. {@code V3__auth_profile_exercise_meta.sql}.
 *
 * <p>액세스 토큰과 달리 <b>불투명 문자열</b>이다(명세 2.3). JWT가 아니므로 서버가
 * DB를 봐야만 유효성을 알 수 있고, 그 대가로 <b>즉시 폐기가 가능해진다</b> — JWT의
 * 약점인 "만료 전까지 무효화 불가"를 이 토큰이 보완하는 구조다.
 *
 * <p><b>원문을 저장하지 않는다.</b> SHA-256 해시만 갖는다. DB가 유출돼도 그 값으로
 * 토큰을 재구성할 수 없다. 비밀번호를 해시로만 두는 것과 같은 이유다.
 */
@Entity
@Getter
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 원문의 SHA-256 해시(64자 hex). 조회 키이자 유일성 제약 대상이다. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** 회전·로그아웃·재사용 감지로 폐기된 시각. {@code null}이면 살아 있다. */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    private RefreshToken(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    /**
     * 폐기한다. 행을 지우지 않고 표시만 남기는 이유는 <b>재사용을 감지하기 위해서</b>다.
     * 지워 버리면 폐기된 토큰이 다시 들어와도 "처음 보는 토큰"과 구분되지 않는다.
     */
    public void revoke(OffsetDateTime at) {
        if (this.revokedAt == null) {
            this.revokedAt = at;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime at) {
        return !expiresAt.isAfter(at);
    }

    /** 재발급에 쓸 수 있는 상태인가. */
    public boolean isUsable(OffsetDateTime at) {
        return !isRevoked() && !isExpired(at);
    }
}
