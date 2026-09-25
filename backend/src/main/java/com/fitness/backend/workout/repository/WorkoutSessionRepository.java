package com.fitness.backend.workout.repository;

import com.fitness.backend.workout.domain.SessionStatus;
import com.fitness.backend.workout.domain.WorkoutSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * 소유권까지 한 번에 본다. {@code findById} 후 {@code userId}를 비교하면
     * 남의 세션이 존재하는지가 404와 403으로 갈려 드러나므로, 조회 단계에서 묶는다.
     */
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    /** 진행 중 세션 이어쓰기(명세 6.7). {@code DRAFT}는 사용자당 하나뿐이라 단건이다 */
    Optional<WorkoutSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    boolean existsByUserIdAndStatus(Long userId, SessionStatus status);
}
