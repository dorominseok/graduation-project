package com.fitness.backend.workout.repository;

import com.fitness.backend.workout.domain.SessionStatus;
import com.fitness.backend.workout.domain.WorkoutSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * 소유권까지 한 번에 본다. {@code findById} 후 {@code userId}를 비교하면
     * 남의 세션이 존재하는지가 404와 403으로 갈려 드러나므로, 조회 단계에서 묶는다.
     */
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    /** 진행 중 세션 이어쓰기(명세 6.7). {@code DRAFT}는 사용자당 하나뿐이라 단건이다 */
    Optional<WorkoutSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    boolean existsByUserIdAndStatus(Long userId, SessionStatus status);

    /**
     * 히스토리 목록(명세 6.6).
     *
     * <p>{@code exerciseId}는 <b>세션 단위 필터</b>다 — 그 종목의 세트가 하나라도 있는
     * 세션을 고를 뿐, 응답에서 다른 종목의 세트를 빼지 않는다.
     *
     * <p>{@code null}이면 그 조건을 걸지 않는다. 조건마다 메서드를 만들면 조합이
     * 여덟 가지가 되어 관리가 안 된다.
     */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId
              and s.performedOn >= :from
              and s.performedOn <= :to
              and (:status is null or s.status = :status)
              and (:exerciseId is null
                   or exists (select 1 from WorkoutSet w
                              where w.sessionId = s.id and w.exerciseId = :exerciseId))
            """)
    Page<WorkoutSession> search(@Param("userId") Long userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("status") SessionStatus status,
                                @Param("exerciseId") Long exerciseId,
                                Pageable pageable);

    /**
     * 캘린더 월별 요약(명세 6.8).
     *
     * <p>DB에서 집계하지 않고 그 달의 세션을 그대로 읽어 메모리에서 묶는다 —
     * 한 달 치는 많아야 수십 건이고, 집계 쿼리를 두면 {@code hasDraft} 같은 조건이
     * 늘 때마다 쿼리를 고쳐야 한다.
     */
    List<WorkoutSession> findByUserIdAndPerformedOnBetween(Long userId, LocalDate from, LocalDate to);
}
