package com.fitness.backend.routine.domain;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.analysis.domain.MuscleGroup.CALVES;
import static com.fitness.backend.analysis.domain.MuscleGroup.CHEST;
import static com.fitness.backend.analysis.domain.MuscleGroup.CORE;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_FRONT;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_REAR;
import static com.fitness.backend.analysis.domain.MuscleGroup.FOREARMS;
import static com.fitness.backend.analysis.domain.MuscleGroup.POSTERIOR;
import static com.fitness.backend.analysis.domain.MuscleGroup.QUADS;
import static com.fitness.backend.analysis.domain.MuscleGroup.TRICEPS;
import static com.fitness.backend.routine.domain.RoutineExclusion.Reason.BUNDLE_RECOVERING;
import static com.fitness.backend.routine.domain.RoutineExclusion.Reason.SELF_RECOVERING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.domain.RoutineExclusion.Excluded;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회복 때문에 오늘 루틴에서 빠지는 부위와 이유. 루틴과 회복 지도가 같이 쓰는 규칙이라 경우마다 고정한다. */
class RoutineExclusionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TWO_DAYS_AGO = TODAY.minusDays(2);
    private static final int R = 2;

    @Test
    @DisplayName("회복 중인 부위는 본인이 이유고, 같은 묶음의 회복된 부위는 그 부위가 이유다")
    void selfAndBundle() {
        Map<MuscleGroup, Excluded> excluded = RoutineExclusion.of(
                Map.of(CHEST, YESTERDAY, TRICEPS, TWO_DAYS_AGO, BACK, TWO_DAYS_AGO), TODAY, R);

        assertEquals(Set.of(CHEST, DELT_FRONT, TRICEPS), excluded.keySet(), "밀기 묶음만 빠진다");
        assertEquals(new Excluded(SELF_RECOVERING, List.of()), excluded.get(CHEST));
        assertEquals(new Excluded(BUNDLE_RECOVERING, List.of(CHEST)), excluded.get(TRICEPS), "그제 한 삼두는 회복됐어도 빠진다");
        assertEquals(new Excluded(BUNDLE_RECOVERING, List.of(CHEST)), excluded.get(DELT_FRONT), "기록이 없는 부위도 빠진다");
    }

    @Test
    @DisplayName("같은 묶음에서 둘이 회복 중이면 남은 부위의 이유에 둘 다 담긴다")
    void twoCausesInOneBundle() {
        Map<MuscleGroup, Excluded> excluded = RoutineExclusion.of(
                Map.of(BICEPS, TODAY, DELT_REAR, YESTERDAY), TODAY, R);

        assertEquals(Set.of(BACK, DELT_REAR, BICEPS), excluded.keySet());
        assertEquals(new Excluded(BUNDLE_RECOVERING, List.of(DELT_REAR, BICEPS)), excluded.get(BACK));
        assertEquals(SELF_RECOVERING, excluded.get(BICEPS).reason(), "본인이 회복 중이면 묶음보다 본인이 이유다");
    }

    @Test
    @DisplayName("하체도 묶음이다 — 앞허벅지가 회복 중이면 뒤허벅지·둔근도 빠진다")
    void lowerBundle() {
        Map<MuscleGroup, Excluded> excluded = RoutineExclusion.of(Map.of(QUADS, TODAY), TODAY, R);

        assertEquals(Set.of(QUADS, POSTERIOR), excluded.keySet());
        assertEquals(new Excluded(BUNDLE_RECOVERING, List.of(QUADS)), excluded.get(POSTERIOR));
    }

    @Test
    @DisplayName("코어는 묶음이 없다 — 코어만 빠지고, 다른 묶음이 회복 중이어도 코어는 남는다")
    void coreHasNoBundle() {
        assertEquals(Map.of(CORE, new Excluded(SELF_RECOVERING, List.of())),
                RoutineExclusion.of(Map.of(CORE, YESTERDAY), TODAY, R));

        Map<MuscleGroup, Excluded> everyBundle = RoutineExclusion.of(
                Map.of(CHEST, TODAY, BACK, TODAY, QUADS, TODAY), TODAY, R);
        assertEquals(8, everyBundle.size(), "묶음 셋의 여덟 부위");
        assertTrue(!everyBundle.containsKey(CORE));
    }

    @Test
    @DisplayName("종아리·전완은 루틴이 고르지 않는 부위라 빠진다고 하지 않고 다른 부위를 빼지도 않는다")
    void displayOnlyMusclesAreIgnored() {
        assertTrue(RoutineExclusion.of(Map.of(CALVES, TODAY, FOREARMS, YESTERDAY), TODAY, R).isEmpty());
    }

    @Test
    @DisplayName("회복 기간이 지났거나 기록이 없으면 아무것도 빠지지 않는다")
    void nothingRecovering() {
        assertTrue(RoutineExclusion.of(Map.of(CHEST, TWO_DAYS_AGO, BACK, TODAY.minusDays(9)), TODAY, R).isEmpty());
        assertTrue(RoutineExclusion.of(Map.of(), TODAY, R).isEmpty());
    }

    @Test
    @DisplayName("회복 일수가 0이면 오늘 한 부위도 빠지지 않는다")
    void recoveryDaysZero() {
        assertTrue(RoutineExclusion.of(Map.of(CHEST, TODAY, CORE, TODAY), TODAY, 0).isEmpty());
    }
}
