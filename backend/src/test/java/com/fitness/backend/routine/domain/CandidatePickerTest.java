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

/** 부위별 후보. */
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
    @DisplayName("새 종목은 계열이 겹치지 않는 것 먼저, 그 안에서 기구가 다른 것 먼저 — 겹치는 계열은 맨 뒤")
    void newOnesAreDiverse() {
        List<ExerciseOption> pool = List.of(
                ex(1, "벤치", BARBELL, false, 0),
                ex(2, "벤치", DUMBBELL, false, 0),     // 같은 계열 — 자리가 남을 때만
                ex(3, "프레스", BARBELL, false, 0),    // 기구가 겹쳐 뒤로
                ex(4, "플라이", CABLE, false, 0),
                ex(5, "머신", MACHINE, false, 0));

        assertEquals(List.of(1L, 4L, 5L, 3L), ids(CandidatePicker.pick(pool, 4, 3)));
        assertEquals(List.of(1L, 4L, 5L, 3L, 2L), ids(CandidatePicker.pick(pool, 5, 3)));
    }

    @Test
    @DisplayName("한 계열뿐인 부위(어깨 후면)도 후보가 하나로 줄지 않는다")
    void singleGroupStillFillsUp() {
        List<ExerciseOption> rearDelt = List.of(
                ex(67, "리어델트", BARBELL, false, 0),
                ex(68, "리어델트", CABLE, false, 0),
                ex(69, "리어델트", DUMBBELL, false, 0));

        assertEquals(3, CandidatePicker.pick(rearDelt, 5, 3).size());
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

    @Test
    @DisplayName("입문 대표 종목은 기구가 겹쳐도 새 종목의 맨 앞이다 — 랫풀다운 다음에 시티드 케이블 로우가 밀리지 않는다")
    void startersComeFirstEvenWithSameEquipment() {
        List<ExerciseOption> pool = List.of(
                ex(1, "친업", BARBELL, false, 0),
                ex(2, "로우", DUMBBELL, false, 0),
                ex(3, "슈러그", MACHINE, false, 0),
                new ExerciseOption(4L, "랫풀다운", CABLE, false, 0, true, 1),
                new ExerciseOption(5L, "케이블 로우", CABLE, false, 0, true, 2));

        assertEquals(List.of(4L, 5L, 1L, 2L, 3L), ids(CandidatePicker.pick(pool, 5, 3)));
        // 해 본 종목이 자리를 차지하면 남은 자리만큼만
        List<ExerciseOption> withHistory = List.of(
                ex(1, "친업", BARBELL, false, 4), ex(2, "로우", DUMBBELL, false, 3),
                new ExerciseOption(4L, "랫풀다운", CABLE, false, 0, true, 1),
                new ExerciseOption(5L, "케이블 로우", CABLE, false, 0, true, 2));
        assertEquals(List.of(1L, 2L, 4L), ids(CandidatePicker.pick(withHistory, 3, 3)));
    }

    private static ExerciseOption common(long id, String group, Equipment eq, int rank) {
        return new ExerciseOption(id, group, eq, false, 0, true, null, rank);
    }

    @Test
    @DisplayName("흔한 종목이 나머지보다 먼저다 — 기구가 다른 드문 종목보다 기구가 겹치는 레그 프레스가 앞선다")
    void commonBeforeRest() {
        List<ExerciseOption> pool = List.of(
                new ExerciseOption(1L, "스쿼트", BARBELL, false, 0, true, 1, 1),
                new ExerciseOption(2L, "런지", DUMBBELL, false, 0, true, 2, 1),
                ex(3, "데드리프트", CABLE, false, 0),          // 케이블 데드리프트 — 드물다
                common(4, "레그 익스텐션", MACHINE, 1),
                common(5, "레그 프레스", MACHINE, 1));

        assertEquals(List.of(1L, 2L, 4L, 5L, 3L), ids(CandidatePicker.pick(pool, 5, 3)));
        assertEquals(List.of(1L, 2L, 4L, 5L), ids(CandidatePicker.pick(pool, 4, 3)));
    }

    @Test
    @DisplayName("흔한 종목 안에서는 계열이 겹치지 않는 것 먼저, 흔한 정도 1이 2보다 먼저, 자리가 남으면 겹쳐도 흔한 것")
    void commonSpreadsGroupsThenFillsWithCommon() {
        List<ExerciseOption> pool = List.of(
                new ExerciseOption(1L, "데드리프트", BARBELL, false, 0, true, 1, 1),
                ex(2, "풀 스루", CABLE, false, 0),              // 드문 종목 — 새 계열이어도 뒤
                common(3, "레그컬", MACHINE, 1),
                common(4, "데드리프트", DUMBBELL, 1),           // 계열이 겹친다
                common(5, "힙 쓰러스트", BARBELL, 2),           // 기구가 겹쳐도 새 계열이라 먼저
                common(6, "킥백", CABLE, 2));

        // 3(레그컬) → 5·6(새 계열, 흔한 정도 2) → 4(계열이 겹치는 흔한 종목) 순. 드문 2는 자리가 없다
        assertEquals(List.of(1L, 3L, 5L, 6L, 4L), ids(CandidatePicker.pick(pool, 5, 3)));
    }
}
