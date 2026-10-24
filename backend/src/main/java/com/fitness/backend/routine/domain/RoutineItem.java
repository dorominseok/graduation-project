package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 루틴 항목({@code routine_items}).
 *
 * <p>계획이다 — 체크한 세트는 {@code workout_sets}에만 있고 여기에는 오지 않는다.
 * 후보정을 해도 원래 값을 {@code original*}에 남긴다. 수행률의 분모가 원래 계획이고,
 * 후보정 비율도 여기서 센다.
 */
@Entity
@Getter
@Table(name = "routine_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "routine_id", nullable = false)
    private Long routineId;

    @Column(nullable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false, length = 20)
    private MuscleGroup muscleGroup;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "planned_sets", nullable = false)
    private short plannedSets;

    @Column(name = "target_min", nullable = false)
    private short targetMin;

    @Column(name = "target_max", nullable = false)
    private short targetMax;

    @Column(name = "first_time", nullable = false)
    private boolean firstTime;

    /** 근거 — 만든 날의 주당 평균. 다시 계산하면 오늘 기록이 섞인다 */
    @Column(name = "basis_weekly_sets", nullable = false, precision = 4, scale = 1)
    private BigDecimal basisWeeklySets;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis_verdict", nullable = false, length = 20)
    private VolumeVerdict basisVerdict;

    @Column(name = "original_exercise_id")
    private Long originalExerciseId;

    @Column(name = "original_sets")
    private Short originalSets;

    @Column(nullable = false)
    private boolean removed;

    private RoutineItem(Long routineId, short position, MuscleGroup muscleGroup, Long exerciseId,
                        short plannedSets, RepRange range, boolean firstTime,
                        BigDecimal basisWeeklySets, VolumeVerdict basisVerdict) {
        this.routineId = routineId;
        this.position = position;
        this.muscleGroup = muscleGroup;
        this.exerciseId = exerciseId;
        this.plannedSets = plannedSets;
        this.targetMin = (short) range.min();
        this.targetMax = (short) range.max();
        this.firstTime = firstTime;
        this.basisWeeklySets = basisWeeklySets;
        this.basisVerdict = basisVerdict;
    }

    public static RoutineItem plan(Long routineId, int position, MuscleGroup muscleGroup, Long exerciseId,
                                   int plannedSets, RepRange range, boolean firstTime,
                                   BigDecimal basisWeeklySets, VolumeVerdict basisVerdict) {
        return new RoutineItem(routineId, (short) position, muscleGroup, exerciseId, (short) plannedSets,
                range, firstTime, basisWeeklySets, basisVerdict);
    }

    /** 후보정 — 종목 교체. 처음 바꿀 때만 원래 종목을 남긴다 */
    public void replaceExercise(Long newExerciseId, RepRange range, boolean firstTime) {
        if (newExerciseId.equals(exerciseId)) {
            return;
        }
        if (originalExerciseId == null) {
            originalExerciseId = exerciseId;
        }
        this.exerciseId = newExerciseId;
        this.targetMin = (short) range.min();
        this.targetMax = (short) range.max();
        this.firstTime = firstTime;
    }

    /** 후보정 — 세트 조정. 처음 바꿀 때만 원래 세트를 남긴다 */
    public void adjustSets(int sets) {
        if (sets == plannedSets) {
            return;
        }
        if (originalSets == null) {
            originalSets = plannedSets;
        }
        this.plannedSets = (short) sets;
    }

    /** 후보정 — 빼기. 지우지 않는다. 원래 계획은 수행률의 분모로 남는다 */
    public void remove() {
        this.removed = true;
    }

    /** 원래 계획의 세트 — 수행률 분모 */
    public int originalPlannedSets() {
        return originalSets != null ? originalSets : plannedSets;
    }

    public boolean adjusted() {
        return originalExerciseId != null || originalSets != null || removed;
    }
}
