package com.fitness.backend.workout.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    /** 정렬 기준은 저장 시각이다 — {@code setNo}는 구멍이 날 수 있어 순서를 못 맡긴다(명세 6.12) */
    List<WorkoutSet> findBySessionIdOrderByRecordedAtAsc(Long sessionId);

    /** 멱등 처리(명세 6.4). 이미 있으면 저장하지 않고 이 세트를 그대로 돌려준다 */
    Optional<WorkoutSet> findBySessionIdAndClientSetId(Long sessionId, UUID clientSetId);

    /** {@code setNo} 자동 부여용. 같은 세션 안 같은 종목의 세트 수 + 1이 다음 번호다 */
    int countBySessionIdAndExerciseId(Long sessionId, Long exerciseId);

    Optional<WorkoutSet> findByIdAndSessionId(Long id, Long sessionId);

    int countBySessionId(Long sessionId);

    /** 히스토리 목록의 세트 수·종목 목록을 한 번에 채운다. 세션마다 조회하면 N+1이다 */
    List<WorkoutSet> findBySessionIdInOrderByRecordedAtAsc(List<Long> sessionIds);

    /** 종목 단위 삭제(LOG-22). 그 종목의 세트를 한 번에 지운다 */
    int deleteBySessionIdAndExerciseId(Long sessionId, Long exerciseId);
}
