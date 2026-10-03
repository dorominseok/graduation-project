package com.fitness.backend.analysis.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 볼륨 집계 전용 조회. 명세 8.2 「집계 조건」.
 *
 * <p>{@code WorkoutSetRepository}에 얹지 않고 따로 둔다 — 그쪽은 기록 화면이 쓰는
 * CRUD라 성격이 다르고, 분석이 어떤 조건으로 세트를 세는지가 한 파일에 모여 있어야
 * 판정이 틀렸을 때 볼 자리가 분명하다.
 */
public interface MuscleVolumeRepository extends Repository<WorkoutSet, Long> {

    /**
     * 기간 내 본세트를 근육별로 센다.
     *
     * <p>네 가지 조건이 전부 판정의 전제다.
     * <ul>
     *   <li>{@code status = DONE} — 진행 중({@code DRAFT})은 아직 한 운동이 아니다</li>
     *   <li>{@code isWarmup = false} — 워밍업을 세면 볼륨이 부풀어 부족이 최적으로 뒤집힌다</li>
     *   <li>{@code performedOn} 기준 — 저장 시각이 아니다. 어제 운동을 오늘 입력해도 어제로 센다</li>
     *   <li>{@code source} 무관 — {@code BACKFILL}도 실제로 한 운동이라 포함한다(명세 8.2)</li>
     * </ul>
     *
     * <p>{@code join}을 쓰지 않고 {@code where}로 이은 것은 {@code WorkoutSet}에
     * 연관 매핑이 없어서다. 세트는 {@code exerciseId}를 값으로만 들고 있다.
     */
    @Query("""
            select new com.fitness.backend.analysis.repository.MuscleSetCount(
                       e.primaryMuscle, e.deltRegion, count(w))
              from WorkoutSet w, WorkoutSession s, Exercise e
             where w.sessionId = s.id
               and w.exerciseId = e.id
               and s.userId = :userId
               and s.status = com.fitness.backend.workout.domain.SessionStatus.DONE
               and w.isWarmup = false
               and s.performedOn >= :from
               and s.performedOn <= :to
             group by e.primaryMuscle, e.deltRegion
            """)
    List<MuscleSetCount> aggregate(@Param("userId") Long userId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
