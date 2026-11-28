package com.fitness.backend.stats.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 추정 1RM 추이의 재료 조회. */
public interface OneRmTrendRepository extends Repository<WorkoutSet, Long> {

    /**
     * 한 종목의 1RM 추정 대상 세트를 날짜순으로 읽는다.
     *
     * <p>워밍업도 넣는다 — 어차피 중량이 낮아 그날 최댓값이 되지 않으므로
     * 따로 거를 이유가 없고, 거르면 워밍업으로 잘못 체크된 본세트가 통째로 빠진다.
     *
     * <p>{@code reps} 상한을 파라미터로 받는 것은 {@code app.analysis.one-rm-max-reps}가
     * 조정 가능한 값이기 때문이다. 고반복 구간에서 Epley 오차가 커져 15회 20회를
     * 넣으면 실제로 들지 못할 1RM이 나온다.
     *
     * <p>0kg 세트는 뺀다 — 덤벨 종목을 맨몸으로 하고 0kg으로 적은 날에 "추정 1RM 0.0kg" 점이 찍혀
     * 추이가 바닥으로 꺼진다.
     *
     * <p>GTG(회차 0)는 뺀다 — 일부러 가볍게 한 세트라 GTG만 한 날에 낮은 점이 찍힌다(결정 기록 D-23).
     */
    @Query("""
            select new com.fitness.backend.stats.repository.OneRmSample(
                       s.performedOn, w.weightKg, w.reps)
              from WorkoutSet w, WorkoutSession s
             where w.sessionId = s.id
               and w.exerciseId = :exerciseId
               and s.userId = :userId
               and s.performedOn >= :from
               and s.performedOn <= :to
               and w.weightKg > 0
               and w.reps is not null
               and w.reps >= 1
               and w.reps <= :maxReps
               and w.roundNo <> 0
             order by s.performedOn asc
            """)
    List<OneRmSample> findSamples(@Param("userId") Long userId,
                                  @Param("exerciseId") Long exerciseId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("maxReps") int maxReps);
}
