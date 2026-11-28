package com.fitness.backend.workout.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 하루 기록의 카드 순서(V18, V23). 카드는 (종목, 회차) 하나다. 원래 순서는 세트 저장 시각 순(그 카드의 첫 세트가 이른 순)이고,
 * 사용자가 순서를 바꿨으면 그 순서가 앞선다 — 정한 순서에 있는 카드가 그 순서대로 먼저, 없는 카드는 그 뒤에 원래 순서로.
 *
 * <p>순서는 {@code "종목id:회차"} 글자로 둔다({@link #key}).
 */
public final class ExerciseOrder {

    private ExerciseOrder() {
    }

    /** 카드 하나의 순서 열쇠 */
    public static String key(long exerciseId, int round) {
        return exerciseId + ":" + round;
    }

    /**
     * @param items  원래 순서로 놓인 카드들
     * @param keyOf  카드의 열쇠({@link #key})
     * @param order  사용자가 정한 순서. 없으면 원래 순서 그대로
     * @return 새 목록. {@code items}는 건드리지 않는다
     */
    public static <T> List<T> apply(List<T> items, Function<T, String> keyOf, List<String> order) {
        if (order == null || order.isEmpty() || items.size() < 2) {
            return items;
        }
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            rank.putIfAbsent(order.get(i), i);
        }
        List<T> sorted = new ArrayList<>(items);
        // 안정 정렬이라 정한 순서에 없는 카드끼리는 원래 순서가 지켜진다
        sorted.sort(Comparator.comparingInt(item -> rank.getOrDefault(keyOf.apply(item), Integer.MAX_VALUE)));
        return sorted;
    }

    /** 순서에 나온 종목 id를 처음 나온 차례대로. 회차를 묻지 않는 곳(히스토리 목록의 종목 이름)이 쓴다 */
    public static List<String> exerciseKeys(List<String> order) {
        if (order == null) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String key : order) {
            int colon = key.indexOf(':');
            ids.add(colon < 0 ? key : key.substring(0, colon));
        }
        return List.copyOf(ids);
    }
}
