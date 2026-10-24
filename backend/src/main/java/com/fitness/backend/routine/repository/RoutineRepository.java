package com.fitness.backend.routine.repository;

import com.fitness.backend.routine.domain.Routine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    Optional<Routine> findByUserIdAndRoutineDate(Long userId, LocalDate routineDate);

    /** 남의 루틴은 있는지조차 드러내지 않는다 — 조회 단계에서 소유권까지 묶는다 */
    Optional<Routine> findByIdAndUserId(Long id, Long userId);

    /**
     * 그날 루틴이 없으면 만든다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>추천 버튼을 두 번 빠르게 눌러도 루틴은 하나다. "있는지 보고 넣기"는 둘 다 "없음"을 보고
     * 넣어 유니크 제약에 걸리고, 그 예외를 잡아도 트랜잭션이 이미 롤백으로 표시돼 500이 된다.
     * 하루 기록 열기(LOG-39)·응원(LOG-38)과 같은 방식이다.
     *
     * @return 새로 만들었으면 1, 이미 있었으면 0
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into routines (user_id, routine_date, reference_date, generated_by, llm_attempts,
                                  fallback_reason, comment, comment_by, final_check_passed, final_check_failures)
            values (:userId, :routineDate, :referenceDate, :generatedBy, :llmAttempts,
                    :fallbackReason, :comment, :commentBy, :finalCheckPassed, :finalCheckFailures)
            on conflict (user_id, routine_date) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("routineDate") LocalDate routineDate,
                       @Param("referenceDate") LocalDate referenceDate,
                       @Param("generatedBy") String generatedBy,
                       @Param("llmAttempts") int llmAttempts,
                       @Param("fallbackReason") String fallbackReason,
                       @Param("comment") String comment,
                       @Param("commentBy") String commentBy,
                       @Param("finalCheckPassed") boolean finalCheckPassed,
                       @Param("finalCheckFailures") String finalCheckFailures);

    /**
     * 기간 안의 지난 루틴들, 최근 날짜부터. 만족도를 물을지는 서비스가 정한다 — 가장 최근에 운동한
     * 루틴 하나만 보고, 그 루틴에 이미 답했으면 더 오래된 루틴은 묻지 않는다.
     */
    @Query("""
            select r from Routine r
             where r.userId = :userId
               and r.routineDate >= :from
               and r.routineDate < :before
             order by r.routineDate desc
            """)
    List<Routine> findRecent(@Param("userId") Long userId,
                             @Param("from") LocalDate from,
                             @Param("before") LocalDate before);
}
