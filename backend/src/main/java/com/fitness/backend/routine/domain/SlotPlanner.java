package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 오늘 할 부위와 세트를 정한다. 「루틴생성_로직_설계서」 2·3장.
 *
 * <p>DB·스프링에 의존하지 않는 순수 계산이다. 분석 판정(분석 2장)처럼 경계값을 단위 테스트로
 * 고정한다 — 루틴이 무엇을 근거로 나왔는지를 이 클래스 하나로 설명할 수 있어야 한다.
 *
 * <pre>
 *   후보   = 부족 · 권장 이하 (과다·최적 제외), 회복 중(오늘·어제 한 부위) 제외
 *   정렬   = 판정 → 불균형 적은 쪽 → 모자란 양(큰 순) → 마지막 운동일(오래된 순) → 크기 순
 *   세트   = 주 목표 ÷ 주당 운동 일수. 부족은 올림, 권장 이하는 내림, 2~6으로 자름
 *   담기   = 하루 상한(18)을 넘지 않는 부위만
 *   채우기 = 하한(12)에 못 미치면 최적 부위 중 가장 오래 쉰 부위부터
 * </pre>
 */
public final class SlotPlanner {

    /**
     * 모든 조건이 같을 때의 순서 — 큰 근육부터, 코어가 마지막(설계서 2.3의 4번).
     * 기록이 없는 사람에게는 이 순서가 첫 루틴을 정한다(2.6).
     */
    public static final List<MuscleGroup> SIZE_ORDER = List.of(
            MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.POSTERIOR,
            MuscleGroup.DELT_FRONT, MuscleGroup.DELT_REAR, MuscleGroup.TRICEPS, MuscleGroup.BICEPS,
            MuscleGroup.CORE);

    private SlotPlanner() {
    }

    /**
     * @param states      판정 부위 9종의 상태. 판정하지 않는 부위(종아리·전완)는 무시한다
     * @param daysPerWeek 주당 운동 일수. 모르면 부르는 쪽이 기본값을 넘긴다
     * @param today       루틴 날짜. 회복 제외의 기준
     */
    public static List<RoutineSlot> plan(List<MuscleState> states, double daysPerWeek, LocalDate today,
                                         RoutinePolicy policy) {
        if (daysPerWeek <= 0) {
            throw new IllegalArgumentException("주당 운동 일수는 0보다 커야 한다: " + daysPerWeek);
        }
        double base = policy.targetWeeklySets() / daysPerWeek;
        BigDecimal target = BigDecimal.valueOf(policy.targetWeeklySets());

        List<MuscleState> judged = states.stream()
                .filter(s -> s.muscle().judged())
                .filter(s -> !recovering(s, today, policy))
                .toList();

        List<MuscleState> lacking = judged.stream()
                .filter(s -> s.verdict() == VolumeVerdict.INSUFFICIENT
                        || s.verdict() == VolumeVerdict.BELOW_RECOMMENDED)
                .sorted(Comparator
                        .comparing((MuscleState s) -> s.verdict().ordinal())
                        .thenComparing(s -> !s.lesserSide())
                        .thenComparing(s -> target.subtract(s.weeklySets()), Comparator.reverseOrder())
                        .thenComparing(SlotPlanner::lastTrainedOrder)
                        .thenComparing(s -> SIZE_ORDER.indexOf(s.muscle())))
                .toList();

        List<RoutineSlot> slots = new ArrayList<>();
        int total = 0;
        for (MuscleState state : lacking) {
            int sets = state.verdict() == VolumeVerdict.INSUFFICIENT
                    ? clamp((int) Math.ceil(base - 1e-9), policy)
                    : clamp((int) Math.floor(base + 1e-9), policy);
            if (total + sets <= policy.dailyMaxSets()) {
                slots.add(new RoutineSlot(state.muscle(), sets, state.weeklySets(), state.verdict(), false));
                total += sets;
            }
        }

        if (total < policy.dailyMinSets()) {
            List<MuscleState> optimal = judged.stream()
                    .filter(s -> s.verdict() == VolumeVerdict.OPTIMAL)
                    .sorted(Comparator
                            .comparing(SlotPlanner::lastTrainedOrder)
                            .thenComparing(s -> SIZE_ORDER.indexOf(s.muscle())))
                    .toList();
            int fillSets = clamp(Math.min((int) Math.floor(base + 1e-9), policy.fillMaxSets()), policy);
            for (MuscleState state : optimal) {
                if (total >= policy.dailyMinSets()) {
                    break;
                }
                if (total + fillSets <= policy.dailyMaxSets()) {
                    slots.add(new RoutineSlot(state.muscle(), fillSets, state.weeklySets(), state.verdict(), true));
                    total += fillSets;
                }
            }
        }
        return List.copyOf(slots);
    }

    /** 오늘 포함 {@code recoveryDays}일 안에 한 부위. 48시간이면 오늘·어제 */
    static boolean recovering(MuscleState state, LocalDate today, RoutinePolicy policy) {
        LocalDate last = state.lastTrained();
        return last != null && policy.recoveryDays() > 0
                && !last.isBefore(today.minusDays(policy.recoveryDays() - 1L));
    }

    /** 오래된 순. 한 번도 안 한 부위가 가장 오래된 것이다 */
    private static LocalDate lastTrainedOrder(MuscleState state) {
        return state.lastTrained() == null ? LocalDate.MIN : state.lastTrained();
    }

    private static int clamp(int sets, RoutinePolicy policy) {
        return Math.max(policy.perMuscleMinSets(), Math.min(policy.perMuscleMaxSets(), sets));
    }
}
