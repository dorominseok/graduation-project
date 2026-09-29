package com.fitness.backend.stats.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 추정 1RM 추이의 재료 조회. 명세 7.1. */
public interface OneRmTrendRepository extends Repository<WorkoutSet, Long> {

    /**
     * 한 종목의 1RM 추정 대상 세트를 날짜순으로 읽는다.
     *
     * <p>워밍업도 넣는다(명세 7.1) — 어차피 중량이 낮아 그날 최댓값이 되지 않으므로
     * 따로 거를 이유가 없고, 거르면 워밍업으로 잘못 체크된 본세트가 통째로 빠진다.
     *
     * <p>{@code reps} 상한을 파라미터로 받는 것은 {@code app.analysis.one-rm-max-reps}가
     * 조정 가능한 값이기 때문이다(8.5). 고반복 구간에서 Epley 오차가 커져 15회 20회를
     * 넣으면 실제로 들지 못할 1RM이 나온다(LOG-07).
     */
    @Query("""
            select new com.fitness.backend.stats.repository.OneRmSample(
                       s.performedOn, w.weightKg, w.reps)
              from WorkoutSet w, WorkoutSession s
             where w.sessionId = s.id
               and w.exerciseId = :exerciseId
               and s.userId = :userId
               and s.status = com.fitness.backend.workout.domain.SessionStatus.DONE
               and s.performedOn >= :from
               and s.performedOn <= :to
               and w.weightKg is not null
               and w.reps is not null
               and w.reps >= 1
               and w.reps <= :maxReps
             order by s.performedOn asc
            """)
    List<OneRmSample> findSamples(@Param("userId") Long userId,
                                  @Param("exerciseId") Long exerciseId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("maxReps") int maxReps);
}
