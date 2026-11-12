package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 규칙으로 종목을 고른다. LLM이 꺼져 있거나 실패했을 때의 폴백이다. 항상 성공하고 검증이 필요 없다.
 *
 * <pre>
 *   슬롯마다 후보 1순위(즐겨찾기 → 자주 한 종목 → 새 종목)
 *   슬롯 세트가 6 이상이면 둘로 나눈다(앞 종목이 하나 더)
 *     - 해 본 종목이 있는 부위: 계열이 다른 다음 후보와
 *     - 해 본 종목이 없는 큰 근육(가슴·등·앞허벅지·뒤허벅지/둔근): 계열이 다른 입문 대표 종목과
 *     - 해 본 종목이 없는 작은 근육·코어: 나누지 않는다
 *   순서는 큰 근육 → 작은 근육, 코어 마지막
 * </pre>
 */
public final class RuleComposer {

    /** 큰 근육 → 작은 근육 순서가 아닐 때(LLM이 순서를 바꿨는데 설명이 검증에 걸린 경우)의 설명 */
    static final String NEUTRAL_COMMENT = "요즘 덜 한 부위를 채우는 구성이에요.";

    /**
     * 오늘 할 부위가 없을 때. 왜 없는지(회복 중인지, 과다 경계인지)는 {@link RoutineReason}이 따로 말한다 —
     * 여기서 "회복 중이라"고 하면 과다 경계 때문에 빈 날에는 틀린 말이 된다
     */
    public static final String REST_COMMENT = "오늘은 쉬어 가도 좋아요.";

    /** 슬롯 세트가 이 값 이상일 때만 두 종목으로 나눈다 — 나눠도 종목마다 3세트는 남는다 */
    public static final int SPLIT_FROM_SETS = 6;

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
            ExerciseOption second = splittable(slot.muscle(), slot.sets(), options)
                    ? partner(slot.muscle(), options).orElse(null)
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
     * 두 종목으로 나눌 수 있는지. LLM의 {@code split}과 L5도 이걸 쓴다.
     *
     * <p>해 본 종목이 하나도 없는 부위는 큰 근육만, 입문 대표 종목으로만 나눈다. 전부 나누면 처음 하는 사람의
     * 첫 루틴이 새 동작 여섯 개가 되어 동작마다 연습이 모자라고, 하나도 안 나누면 세 종목을 여섯 세트씩 해서
     * 루틴이 적어 보인다(실사용 설문). 큰 근육은 입문자가 먼저 익힐 종목이 둘씩 정해져 있다.
     */
    public static boolean splittable(MuscleGroup muscle, int sets, List<ExerciseOption> options) {
        if (sets < SPLIT_FROM_SETS || options.isEmpty()) {
            return false;
        }
        return tried(options) || partner(muscle, options).isPresent();
    }

    /** 해 본 종목이 하나라도 있는 부위인지 */
    public static boolean tried(List<ExerciseOption> options) {
        return options.stream().anyMatch(o -> !o.firstTime());
    }

    /** 큰 근육 — 가슴·등·앞허벅지·뒤허벅지/둔근 */
    public static boolean big(MuscleGroup muscle) {
        return rank(muscle) == 0;
    }

    /**
     * 후보 1순위와 짝지을 두 번째 종목. 계열이 달라야 한다. 해 본 종목이 없는 부위에서는 큰 근육일 때만,
     * 입문 대표 종목 중에서(순위 순) 고른다.
     */
    static Optional<ExerciseOption> partner(MuscleGroup muscle, List<ExerciseOption> options) {
        ExerciseOption first = options.get(0);
        Stream<ExerciseOption> rest = options.stream()
                .skip(1)
                .filter(o -> o.groupName() == null || !Objects.equals(o.groupName(), first.groupName()));
        if (tried(options)) {
            return rest.findFirst();
        }
        if (!big(muscle)) {
            return Optional.empty();
        }
        return rest.filter(o -> o.starterRank() != null).min(Comparator.comparing(ExerciseOption::starterRank));
    }

    /**
     * 조합 설명. 그날 실제로 담긴 부위로 문장을 고른다 — 코어가 없는 날에 "코어로
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
