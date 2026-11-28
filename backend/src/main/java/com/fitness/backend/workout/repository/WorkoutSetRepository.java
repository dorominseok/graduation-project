package com.fitness.backend.workout.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    /** 정렬 기준은 저장 시각이다 — {@code setNo}는 구멍이 날 수 있어 순서를 못 맡긴다 */
    List<WorkoutSet> findBySessionIdOrderByRecordedAtAsc(Long sessionId);

    /** 멱등 처리. 이미 있으면 저장하지 않고 이 세트를 그대로 돌려준다 */
    Optional<WorkoutSet> findBySessionIdAndClientSetId(Long sessionId, UUID clientSetId);

    /**
     * 세트를 넣는다. 같은 {@code (session_id, client_set_id)}가 이미 있으면 아무것도 하지 않는다.
     *
     * <p>엔티티를 저장하고 유니크 충돌 예외를 잡는 방식은 쓸 수 없다 — 충돌이 난 트랜잭션에서는
     * 먼저 저장된 세트를 다시 읽지 못해 500이 된다. 충돌은 DB가 조용히 넘기게 한다
     * ({@code WorkoutSessionRepository#insertIfAbsent}와 같은 방식).
     *
     * @return 새로 넣었으면 1, 같은 키가 이미 있었으면 0
     */
    @Modifying
    @Query(value = """
            insert into workout_sets (session_id, exercise_id, round_no, set_no, weight_kg, reps, duration_sec,
                                      is_warmup, target_reps, recorded_at, client_set_id)
            values (:sessionId, :exerciseId, :roundNo, :setNo, cast(:weightKg as numeric), cast(:reps as smallint),
                    cast(:durationSec as integer), :isWarmup, cast(:targetReps as smallint), :recordedAt, :clientSetId)
            on conflict (session_id, client_set_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("sessionId") Long sessionId,
                       @Param("exerciseId") Long exerciseId,
                       @Param("roundNo") short roundNo,
                       @Param("setNo") short setNo,
                       @Param("weightKg") BigDecimal weightKg,
                       @Param("reps") Short reps,
                       @Param("durationSec") Integer durationSec,
                       @Param("isWarmup") boolean isWarmup,
                       @Param("targetReps") Short targetReps,
                       @Param("recordedAt") OffsetDateTime recordedAt,
                       @Param("clientSetId") UUID clientSetId);

    /** {@code setNo} 자동 부여용. 같은 세션 안 같은 카드(종목, 회차)의 세트 수 + 1이 다음 번호다 */
    int countBySessionIdAndExerciseIdAndRoundNo(Long sessionId, Long exerciseId, short roundNo);

    Optional<WorkoutSet> findByIdAndSessionId(Long id, Long sessionId);

    int countBySessionId(Long sessionId);

    /** 히스토리 목록의 세트 수·종목 목록을 한 번에 채운다. 세션마다 조회하면 N+1이다 */
    List<WorkoutSet> findBySessionIdInOrderByRecordedAtAsc(List<Long> sessionIds);

    /** 직전 수행 기록. 회차 안에서는 {@code setNo} 순서가 곧 수행 순서다 */
    List<WorkoutSet> findBySessionIdAndExerciseIdOrderByRoundNoAscSetNoAsc(Long sessionId, Long exerciseId);

    List<WorkoutSet> findBySessionIdInAndExerciseIdOrderByRoundNoAscSetNoAsc(List<Long> sessionIds, Long exerciseId);

    /** 카드 단위 삭제. 그 회차의 그 종목 세트를 한 번에 지운다 */
    int deleteBySessionIdAndExerciseIdAndRoundNo(Long sessionId, Long exerciseId, short roundNo);
}
