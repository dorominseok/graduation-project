package com.fitness.backend.workout.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 하루 기록의 종목 순서(V18). 원래 순서는 세트 저장 시각 순(그 종목의 첫 세트가 이른 순)이고, 사용자가 순서를
 * 바꿨으면 그 순서가 앞선다 — 정한 순서에 있는 종목이 그 순서대로 먼저, 없는 종목은 그 뒤에 원래 순서로.
 */
public final class ExerciseOrder {

    private ExerciseOrder() {
    }

    /**
     * @param items  원래 순서로 놓인 종목들
     * @param order  사용자가 정한 순서(종목 id). 없으면 원래 순서 그대로
     * @return 새 목록. {@code items}는 건드리지 않는다
     */
    public static <T> List<T> apply(List<T> items, Function<T, Long> idOf, List<Long> order) {
        if (order == null || order.isEmpty() || items.size() < 2) {
            return items;
        }
        Map<Long, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            rank.putIfAbsent(order.get(i), i);
        }
        List<T> sorted = new ArrayList<>(items);
        // 안정 정렬이라 정한 순서에 없는 종목끼리는 원래 순서가 지켜진다
        sorted.sort(Comparator.comparingInt(item -> rank.getOrDefault(idOf.apply(item), Integer.MAX_VALUE)));
        return sorted;
    }
}
