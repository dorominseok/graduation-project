package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 규칙으로 종목을 고른다. 「루틴생성_로직_설계서」 7장 — LLM이 꺼져 있거나 실패했을 때의 폴백이다.
 * 항상 성공하고 검증이 필요 없다.
 *
 * <pre>
 *   슬롯마다 후보 1순위(즐겨찾기 → 자주 한 종목 → 새 종목)
 *   슬롯 세트가 5 이상이고 계열이 다른 후보가 있으면 둘로 나눈다(앞 종목이 하나 더)
 *   순서는 큰 근육 → 작은 근육, 코어 마지막
 * </pre>
 */
public final class RuleComposer {

    /** 큰 근육 → 작은 근육 순서가 아닐 때(LLM이 순서를 바꿨는데 설명이 검증에 걸린 경우)의 설명 */
    static final String NEUTRAL_COMMENT = "요즘 덜 한 부위를 채우는 구성이에요.";

    /** 오늘 할 부위가 없을 때 — 전부 회복 중이거나 이미 충분하다 */
    public static final String REST_COMMENT = "최근에 운동한 부위가 회복 중이라 오늘은 쉬어 가도 좋아요.";

    /**
     * 슬롯 세트가 이 값 이상일 때만 두 종목으로 나눈다. LLM의 L5도 이 값을 쓴다 — 4세트를 2+2로 나누면
     * 첫날 루틴이 새 동작 여덟 개가 되고 동작마다 연습이 모자란다(오프라인 실험 r1)
     */
    public static final int SPLIT_FROM_SETS = 5;

    private RuleComposer() {
    }

    public static List<PlannedItem> compose(List<RoutineSlot> slots, Map<MuscleGroup, List<ExerciseOption>> candidates) {
        List<RoutineSlot> ordered = slots.stream()
                .sorted(Comparator.comparingInt(s -> SlotPlanner.SIZE_ORDER.indexOf(s.muscle())))
                .toList();

        List<PlannedItem> items = new ArrayList<>();
        for (RoutineSlot slot : ordered) {
            List<ExerciseOption> options = candidates.getOrDefault(slot.muscle(), List.of());
            if (options.isEmpty()) {
                continue;
            }
            ExerciseOption first = options.get(0);
            ExerciseOption second = slot.sets() >= SPLIT_FROM_SETS
                    ? options.stream()
                            .skip(1)
                            .filter(o -> o.groupName() == null || !Objects.equals(o.groupName(), first.groupName()))
                            .findFirst()
                            .orElse(null)
                    : null;
            if (second == null) {
                items.add(new PlannedItem(slot.muscle(), first.id(), slot.sets()));
            } else {
                int[] split = split(slot.sets(), 2);
                items.add(new PlannedItem(slot.muscle(), first.id(), split[0]));
                items.add(new PlannedItem(slot.muscle(), second.id(), split[1]));
            }
        }
        return List.copyOf(items);
    }

    /**
     * 조합 설명(설계서 7.2). 그날 실제로 담긴 부위로 문장을 고른다 — 코어가 없는 날에 "코어로
     * 마무리"라고 말하지 않게. LLM 설명이 검증에 걸렸을 때도 이걸 쓰는데, 그때는 순서가 LLM의 것이라
     * 큰 근육 → 작은 근육 순서가 아니면 순서를 말하지 않는 문장으로 바꾼다.
     */
    public static String comment(List<PlannedItem> items) {
        if (items.isEmpty()) {
            return REST_COMMENT;
        }
        List<Integer> ranks = items.stream().map(item -> rank(item.muscle())).toList();
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) < ranks.get(i - 1)) {
                return NEUTRAL_COMMENT;
            }
        }
        boolean big = ranks.contains(0);
        boolean small = ranks.contains(1);
        boolean core = ranks.contains(2);
        if (big && (small || core)) {
            String finish = small && core ? "작은 근육과 코어로" : small ? "작은 근육으로" : "코어로";
            return "큰 근육부터 시작해서 " + finish + " 마무리하는 순서예요.";
        }
        if (small && core) {
            return "작은 근육부터 하고 코어로 마무리하는 순서예요.";
        }
        if (big) {
            return "큰 근육 위주로, 큰 부위부터 하는 순서예요.";
        }
        return small ? "작은 근육 위주로 짠 날이에요." : "코어를 채우는 날이에요.";
    }

    /** 0 큰 근육(가슴·등·앞허벅지·뒤허벅지/둔근), 1 작은 근육(어깨·팔), 2 코어 */
    private static int rank(MuscleGroup muscle) {
        return switch (muscle) {
            case CHEST, BACK, QUADS, POSTERIOR -> 0;
            case CORE -> 2;
            default -> 1;
        };
    }

    /** 세트를 n개로 고르게 나눈다. 앞쪽이 하나씩 더 갖는다 — LLM이 두 종목을 골랐을 때도 서버가 이렇게 나눈다 */
    public static int[] split(int sets, int parts) {
        int[] result = new int[parts];
        for (int i = 0; i < parts; i++) {
            result[i] = sets / parts + (i < sets % parts ? 1 : 0);
        }
        return result;
    }
}
