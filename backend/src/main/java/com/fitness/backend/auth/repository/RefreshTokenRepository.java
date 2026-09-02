package com.fitness.backend.auth.repository;

import com.fitness.backend.auth.domain.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 해당 사용자의 살아 있는 리프레시 토큰을 전부 폐기한다.
     *
     * <p>재사용이 감지됐을 때 호출한다. 도둑이 이미 받아 간 새 토큰까지 함께 끊어야
     * 하므로 대상이 "그 토큰 하나"가 아니라 "그 사용자의 전부"다(명세 2.3).
     *
     * <p>벌크 갱신이라 영속성 컨텍스트를 우회한다. 호출 후 같은 트랜잭션에서 해당
     * 엔티티를 다시 읽지 않도록 주의한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
