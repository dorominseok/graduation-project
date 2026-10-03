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
     * 그 종목을 마지막으로 수행한 세션(명세 5.5).
     *
     * <p>정렬이 {@code performedOn} 우선인 것은 {@code BACKFILL} 때문이다 — 어제 운동을
     * 오늘 입력하면 저장 시각이 가장 최근이 되므로, 저장 시각으로 정렬하면 오늘 입력한
     * 과거 기록이 "직전 수행"으로 올라온다.
     *
     * <p>같은 날짜에 세션이 둘일 수 있어(명세 6.1) 그 종목의 마지막 저장 시각으로 한 번 더 가른다.
     */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId
              and s.status = com.fitness.backend.workout.domain.SessionStatus.DONE
              and exists (select 1 from WorkoutSet w
                          where w.sessionId = s.id and w.exerciseId = :exerciseId)
            order by s.performedOn desc,
                     (select max(w2.recordedAt) from WorkoutSet w2
                      where w2.sessionId = s.id and w2.exerciseId = :exerciseId) desc
            """)
    List<WorkoutSession> findLastPerformed(@Param("userId") Long userId,
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

    /**
     * 판정 신뢰도용 완료 세션 수(명세 8.2 {@code confidence}).
     *
     * <p>세트가 아니라 <b>세션</b>을 센다. 같은 기간에 세트 40개가 쌓였어도 그게
     * 두 번의 운동에서 나왔다면 주당 평균이라는 말 자체가 성립하지 않는다.
     */
    int countByUserIdAndStatusAndPerformedOnBetween(Long userId, SessionStatus status,
                                                    LocalDate from, LocalDate to);

    /**
     * 기준일 이전(포함) 첫 완료 기록일. 주당 평균의 분모를 정한다(LOG-31).
     *
     * <p>집계 구간(28일) 안이 아니라 <b>전체 기록</b>에서 찾는다. 두 달 전에 시작한
     * 사람이 최근 28일 중 앞 2주를 쉬었다면, 그 2주도 평균에 들어가야 맞다 —
     * 구간 안의 첫 기록일로 자르면 쉰 기간이 지워진다.
     */
    @Query("""
            select min(s.performedOn) from WorkoutSession s
             where s.userId = :userId
               and s.status = com.fitness.backend.workout.domain.SessionStatus.DONE
               and s.performedOn <= :to
            """)
    LocalDate findFirstDonePerformedOn(@Param("userId") Long userId, @Param("to") LocalDate to);
}
