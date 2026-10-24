package com.fitness.backend.routine.repository;

import com.fitness.backend.workout.domain.WorkoutSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 루틴을 짤 때 기록에서 직접 읽는 값(「루틴생성_로직_설계서」 1.2). 분석 응답에 없는 것들이다.
 *
 * <p>모두 <b>본세트만</b> 센다 — 분석 판정과 같은 단위다(분석 1.2).
 */
public interface TrainingHistoryRepository extends Repository<WorkoutSet, Long> {

    /** 근육(주동근)별 마지막 운동일. 오늘 포함 — 회복 제외에 쓴다(설계서 2.2) */
    @Query("""
            select new com.fitness.backend.routine.repository.LastTrainedRow(
                       e.primaryMuscle, e.deltRegion, max(s.performedOn))
              from WorkoutSet w, WorkoutSession s, Exercise e
             where w.sessionId = s.id
               and w.exerciseId = e.id
               and s.userId = :userId
               and w.isWarmup = false
               and s.performedOn <= :today
             group by e.primaryMuscle, e.deltRegion
            """)
    List<LastTrainedRow> lastTrained(@Param("userId") Long userId, @Param("today") LocalDate today);

    /** 종목별로 기간 안에 기록한 날 수. 후보 순서(자주 한 순)에 쓴다(설계서 4.1) */
    @Query("""
            select new com.fitness.backend.routine.repository.ExerciseDaysRow(
                       w.exerciseId, count(distinct s.performedOn))
              from WorkoutSet w, WorkoutSession s
             where w.sessionId = s.id
               and s.userId = :userId
               and s.performedOn >= :from
               and s.performedOn <= :to
             group by w.exerciseId
            """)
    List<ExerciseDaysRow> exerciseDays(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** 이 날짜 전에 한 번이라도 기록한 종목. 이 밖의 종목이 [처음]이다(설계서 4.2) */
    @Query("""
            select distinct w.exerciseId
              from WorkoutSet w, WorkoutSession s
             where w.sessionId = s.id
               and s.userId = :userId
               and s.performedOn < :before
            """)
    List<Long> exercisesDoneBefore(@Param("userId") Long userId, @Param("before") LocalDate before);

    /** 그날 종목별 본세트 수. 루틴 진행과 만족도를 물을지에 쓴다 */
    @Query("""
            select new com.fitness.backend.routine.repository.ExerciseDaysRow(w.exerciseId, count(w))
              from WorkoutSet w, WorkoutSession s
             where w.sessionId = s.id
               and s.userId = :userId
               and s.performedOn = :date
               and w.isWarmup = false
             group by w.exerciseId
            """)
    List<ExerciseDaysRow> setsOn(@Param("userId") Long userId, @Param("date") LocalDate date);
}
