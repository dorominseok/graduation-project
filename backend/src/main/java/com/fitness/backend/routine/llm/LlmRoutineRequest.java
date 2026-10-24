package com.fitness.backend.routine.llm;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.SlotPlanner;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM에게 주는 것 — 오늘 할 부위와 세트(규칙이 정함), 부위마다 후보 종목. 설계서 5.1.
 *
 * <p>부위는 큰 근육 순서로 준다. 우선순위 순서로 주면 "어깨 후면부터"처럼 그 순서를 운동 순서로
 * 베끼기 쉽다 — 순서는 LLM이 정할 일이지만 출발점은 중립이어야 한다.
 */
public record LlmRoutineRequest(List<Slot> slots) {

    /** @param sets 참고용. 4 이상이면 두 종목으로 나눌 수 있다(L5). LLM은 숫자를 돌려주지 않는다 */
    public record Slot(MuscleGroup muscle, int sets, List<Candidate> candidates) {
    }

    /** @param favorite 즐겨찾기 / @param firstTime 오늘 전에 한 번도 안 한 종목 */
    public record Candidate(long exerciseId, String name, Equipment equipment, boolean favorite, boolean firstTime) {
    }

    /** 후보가 없는 슬롯은 뺀다 — 규칙 선택도 그 슬롯을 건너뛴다 */
    public static LlmRoutineRequest of(List<RoutineSlot> slots,
                                       Map<MuscleGroup, List<ExerciseOption>> candidates,
                                       Map<Long, String> names) {
        List<Slot> result = slots.stream()
                .sorted(Comparator.comparingInt(s -> SlotPlanner.SIZE_ORDER.indexOf(s.muscle())))
                .map(slot -> new Slot(slot.muscle(), slot.sets(),
                        candidates.getOrDefault(slot.muscle(), List.of()).stream()
                                .map(o -> new Candidate(o.id(), names.get(o.id()), o.equipment(), o.favorite(),
                                        o.firstTime()))
                                .toList()))
                .filter(slot -> !slot.candidates().isEmpty())
                .toList();
        return new LlmRoutineRequest(result);
    }

    public Optional<Slot> slot(MuscleGroup muscle) {
        return slots.stream().filter(s -> s.muscle() == muscle).findFirst();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }
}
