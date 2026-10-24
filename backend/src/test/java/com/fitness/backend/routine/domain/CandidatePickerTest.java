package com.fitness.backend.routine.domain;

import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static com.fitness.backend.exercise.domain.Equipment.DUMBBELL;
import static com.fitness.backend.exercise.domain.Equipment.MACHINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fitness.backend.exercise.domain.Equipment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 부위별 후보. 「루틴생성_로직_설계서」 4.1. */
class CandidatePickerTest {

    private static ExerciseOption ex(long id, String group, Equipment eq, boolean favorite, int recent) {
        return new ExerciseOption(id, group, eq, favorite, recent, recent == 0, null);
    }

    private static List<Long> ids(List<ExerciseOption> options) {
        return options.stream().map(ExerciseOption::id).toList();
    }

    @Test
    @DisplayName("즐겨찾기 → 해 본 종목(자주 한 순) → 새 종목 순이다")
    void favoritesThenHistoryThenNew() {
        List<ExerciseOption> pool = List.of(
                ex(1, "벤치", BARBELL, false, 0),
                ex(2, "플라이", CABLE, false, 2),
                ex(3, "딥스", DUMBBELL, true, 0),
                ex(4, "체스트프레스", MACHINE, false, 5));

        assertEquals(List.of(3L, 4L, 2L, 1L), ids(CandidatePicker.pick(pool, 5, 3)));
    }

    @Test
    @DisplayName("해 본 종목은 최대 3개까지만 — 나머지 자리는 새 종목에 준다")
    void historyCapped() {
        List<ExerciseOption> pool = List.of(
                ex(1, "a", BARBELL, false, 9), ex(2, "b", BARBELL, false, 8),
                ex(3, "c", BARBELL, false, 7), ex(4, "d", BARBELL, false, 6),
                ex(5, "e", CABLE, false, 0));

        assertEquals(List.of(1L, 2L, 3L, 5L), ids(CandidatePicker.pick(pool, 5, 3)));
    }

    @Test
    @DisplayName("새 종목은 같은 계열을 하나만 넣고, 기구가 다른 것을 먼저 넣는다")
    void newOnesAreDiverse() {
        List<ExerciseOption> pool = List.of(
                ex(1, "벤치", BARBELL, false, 0),
                ex(2, "벤치", DUMBBELL, false, 0),     // 같은 계열 — 빠진다
                ex(3, "프레스", BARBELL, false, 0),    // 기구가 겹쳐 뒤로
                ex(4, "플라이", CABLE, false, 0),
                ex(5, "머신", MACHINE, false, 0));

        assertEquals(List.of(1L, 4L, 5L, 3L), ids(CandidatePicker.pick(pool, 5, 3)));
    }

    @Test
    @DisplayName("새 종목 중에서는 입문 대표 종목이 먼저 — 시드 순서가 앞이어도 뒤로 간다")
    void startersFirstAmongNew() {
        List<ExerciseOption> pool = List.of(
                new ExerciseOption(1L, "디클라인", BARBELL, false, 0, true, null),
                new ExerciseOption(2L, "푸시업", Equipment.BODYWEIGHT, false, 0, true, 2),
                new ExerciseOption(3L, "벤치", BARBELL, false, 0, true, 1),
                new ExerciseOption(4L, "플라이", CABLE, false, 0, true, null));

        assertEquals(List.of(3L, 2L, 4L), ids(CandidatePicker.pick(pool, 3, 3)));
    }

    @Test
    @DisplayName("입문 대표 종목이어도 즐겨찾기·해 본 종목을 앞지르지 않는다")
    void startersAfterFavoritesAndHistory() {
        List<ExerciseOption> pool = List.of(
                new ExerciseOption(1L, "벤치", BARBELL, false, 0, true, 1),
                new ExerciseOption(2L, "딥스", Equipment.PULLUP_BAR, false, 3, false, null),
                new ExerciseOption(3L, "플라이", CABLE, true, 0, true, null));

        assertEquals(List.of(3L, 2L, 1L), ids(CandidatePicker.pick(pool, 5, 3)));
    }

    @Test
    @DisplayName("후보는 최대 5개, 종목이 적은 부위(어깨 후면 3개)는 있는 만큼")
    void limited() {
        List<ExerciseOption> many = List.of(
                ex(1, "a", BARBELL, false, 0), ex(2, "b", CABLE, false, 0), ex(3, "c", DUMBBELL, false, 0),
                ex(4, "d", MACHINE, false, 0), ex(5, "e", BARBELL, false, 0), ex(6, "f", CABLE, false, 0));
        List<ExerciseOption> few = List.of(
                ex(1, "a", BARBELL, false, 0), ex(2, "b", CABLE, false, 0), ex(3, "c", DUMBBELL, false, 0));

        assertEquals(5, CandidatePicker.pick(many, 5, 3).size());
        assertEquals(3, CandidatePicker.pick(few, 5, 3).size());
    }
}
