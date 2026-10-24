package com.fitness.backend.routine.repository;

import com.fitness.backend.routine.domain.RoutineItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, Long> {

    List<RoutineItem> findByRoutineIdOrderByPositionAsc(Long routineId);

    Optional<RoutineItem> findByIdAndRoutineId(Long id, Long routineId);
}
