package com.fitness.backend.notification.repository;

import com.fitness.backend.notification.domain.NotificationSettings;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    /** 이 시각에 리마인더를 받기로 한 사람 중 구독이 있는 사람. 요일은 부르는 쪽이 비트로 거른다 */
    @Query("""
            select s from NotificationSettings s
             where s.reminderEnabled = true
               and s.reminderTime = :time
               and exists (select p.id from PushSubscription p where p.userId = s.userId)
            """)
    List<NotificationSettings> remindersAt(@Param("time") LocalTime time);

    /** 이 요일·시각에 주간 요약을 받기로 한 사람 중 구독이 있는 사람 */
    @Query("""
            select s from NotificationSettings s
             where s.summaryEnabled = true
               and s.summaryDay = :day
               and s.summaryTime = :time
               and exists (select p.id from PushSubscription p where p.userId = s.userId)
            """)
    List<NotificationSettings> summariesAt(@Param("day") short day, @Param("time") LocalTime time);
}
