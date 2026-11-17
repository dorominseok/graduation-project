package com.fitness.backend.notification.repository;

import com.fitness.backend.notification.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    long countByUserId(Long userId);

    /** 비밀번호를 바꿀 때 — 모든 기기가 로그아웃되므로 알림 기기도 모두 지운다(「알림_설계서」 4.1) */
    long deleteByUserId(Long userId);
}
