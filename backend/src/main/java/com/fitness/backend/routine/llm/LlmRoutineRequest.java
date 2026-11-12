package com.fitness.backend.routine.llm;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.domain.SlotPlanner;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM에게 주는 것 — 오늘 할 부위와 세트(규칙이 정함), 부위마다 후보 종목.
 *
 * <p>부위는 큰 근육 순서로 준다. 우선순위 순서로 주면 "어깨 후면부터"처럼 그 순서를 운동 순서로
 * 베끼기 쉽다 — 순서는 LLM이 정할 일이지만 출발점은 중립이어야 한다.
 */
public record LlmRoutineRequest(List<Slot> slots, Today today) {

    /**
     * 오늘 왜 이 묶음인지 — 규칙이 정한 것을 숫자 없이 요약한 말({@code RoutineReason.brief}). 화면에는 규칙이 쓴
     * 문장이 따로 보이므로 LLM은 이를 되풀이하지 않고, 종목을 고른 이유와 이어질 때만 쓴다.
     *
     * @param bundle 그날 묶음 이름(하체·밀기·당기기)
     */
    public record Today(String bundle, String why) {
    }

    /**
     * @param verdict    그 부위의 판정(부족·권장 이하·최적). 설명이 "부족한 등은…"처럼 판정과 이어질 수 있게
     * @param sets       참고용. LLM은 숫자를 돌려주지 않는다
     * @param splittable 두 종목으로 나눌 수 있는지(L5). 규칙 선택과 같은 기준이다
     */
    public record Slot(MuscleGroup muscle, VolumeVerdict verdict, int sets, boolean splittable,
                       List<Candidate> candidates) {

        /** 해 본 종목이 하나도 없는 슬롯 — 나눌 때 입문 대표 종목이나 즐겨찾기만 쓴다(L5) */
        public boolean untried() {
            return candidates.stream().allMatch(Candidate::firstTime);
        }
    }

    /**
     * @param favorite   즐겨찾기
     * @param firstTime  오늘 전에 한 번도 안 한 종목
     * @param starter    입문 대표 종목 — 그 부위에서 입문자가 먼저 익힐 종목. LLM에게는 보내지 않는다
     * @param recentDays 최근 8주에 이 종목을 한 날 수. LLM에게는 {@link #experience}로 바꿔 준다
     */
    public record Candidate(long exerciseId, String name, Equipment equipment, boolean favorite, boolean firstTime,
                            boolean starter, int recentDays) {

        /** 이 날수부터 "자주" — 8주에 4일이면 두 주에 한 번꼴이다 */
        static final int OFTEN_FROM_DAYS = 4;

        /**
         * 해 본 정도를 말로. 날수를 그대로 주면 설명에 숫자가 따라 나오고(L6에 걸려 설명을 버리게 된다),
         * 설명에 쓸 말도 "늘 하던"·"처음 해 보는" 정도면 된다.
         */
        public String experience() {
            if (firstTime) {
                return "처음";
            }
            return recentDays >= OFTEN_FROM_DAYS ? "자주" : "가끔";
        }
    }

    /**
     * 후보가 없는 슬롯은 뺀다 — 규칙 선택도 그 슬롯을 건너뛴다.
     *
     * <p>해 본 종목이 없는데 나눌 수 있는 슬롯(큰 근육)은 <b>입문 대표 종목과 즐겨찾기만</b> 후보로 준다.
     * 프롬프트로 "대표 종목으로만 나눠라"고 했더니 앞허벅지를 스쿼트 + 레그 익스텐션으로 나누는 답이
     * 이어졌다(실험 2026-11-12 — 그 슬롯 20회 중 1차 통과 0). 고를 수 없게 하는 쪽이 확실하다.
     */
    public static LlmRoutineRequest of(List<RoutineSlot> slots,
                                       Map<MuscleGroup, List<ExerciseOption>> candidates,
                                       Map<Long, String> names,
                                       Today today) {
        List<Slot> result = slots.stream()
                .sorted(Comparator.comparingInt(s -> SlotPlanner.SIZE_ORDER.indexOf(s.muscle())))
                .map(slot -> {
                    List<ExerciseOption> options = candidates.getOrDefault(slot.muscle(), List.of());
                    boolean splittable = RuleComposer.splittable(slot.muscle(), slot.sets(), options);
                    boolean startersOnly = splittable && !RuleComposer.tried(options);
                    return new Slot(slot.muscle(), slot.verdict(), slot.sets(), splittable,
                            options.stream()
                                    .filter(o -> !startersOnly || o.starterRank() != null || o.favorite())
                                    .map(o -> new Candidate(o.id(), names.get(o.id()), o.equipment(), o.favorite(),
                                            o.firstTime(), o.starterRank() != null, o.recentDays()))
                                    .toList());
                })
                .filter(slot -> !slot.candidates().isEmpty())
                .toList();
        return new LlmRoutineRequest(result, today);
    }

    public Optional<Slot> slot(MuscleGroup muscle) {
        return slots.stream().filter(s -> s.muscle() == muscle).findFirst();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }
}
