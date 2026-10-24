package com.fitness.backend.routine.domain;

import com.fitness.backend.exercise.domain.Equipment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 부위별 후보 종목. 「루틴생성_로직_설계서」 4.1.
 *
 * <pre>
 *   1. 즐겨찾기
 *   2. 해 본 종목 — 최근에 자주 한 순, 최대 {@code historyMax}개
 *   3. 안 해 본 종목 — 같은 계열은 하나만, 이미 들어간 후보와 기구가 다른 것 먼저.
 *      그 안에서는 입문 대표 종목이 먼저, 나머지는 시드 순서
 *   모두 합쳐 최대 {@code limit}개
 * </pre>
 *
 * <p>익숙한 종목과 새 종목을 섞는다. 해 본 것만 주면 루틴이 매일 같고, 새 것만 주면 따라 하기
 * 어렵다. 후보를 작게 두는 것은 LLM에 넘길 목록(스키마 enum)을 작게 유지하기 위해서다.
 */
public final class CandidatePicker {

    /** 입문 대표 종목(순위 오름차순) → 나머지. 같으면 시드 순서 */
    static final Comparator<ExerciseOption> STARTER_FIRST = Comparator
            .comparing(ExerciseOption::starterRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ExerciseOption::id);

    private CandidatePicker() {
    }

    /**
     * @param pool 그 판정 부위에 속하는 종목 전부. 순서는 시드 순서(id)를 쓴다
     */
    public static List<ExerciseOption> pick(List<ExerciseOption> pool, int limit, int historyMax) {
        List<ExerciseOption> byId = pool.stream().sorted(Comparator.comparing(ExerciseOption::id)).toList();
        Set<ExerciseOption> chosen = new LinkedHashSet<>();

        byId.stream()
                .filter(ExerciseOption::favorite)
                .sorted(Comparator.comparingInt(ExerciseOption::recentDays).reversed()
                        .thenComparing(ExerciseOption::id))
                .forEach(chosen::add);

        byId.stream()
                .filter(o -> !o.favorite() && o.recentDays() > 0)
                .sorted(Comparator.comparingInt(ExerciseOption::recentDays).reversed()
                        .thenComparing(ExerciseOption::id))
                .limit(historyMax)
                .forEach(chosen::add);

        // 기록이 없는 사람의 첫 루틴이 여기서 정해진다. 시드 순서(영문 이름 순)대로 두면
        // 디클라인 벤치프레스·친업이 먼저 나와, 입문 대표 종목을 앞에 둔다(LOG-40)
        List<ExerciseOption> fresh = new ArrayList<>(byId.stream()
                .filter(o -> o.recentDays() == 0 && !o.favorite())
                .sorted(STARTER_FIRST)
                .toList());
        Set<String> usedGroups = new HashSet<>();
        Set<Equipment> usedEquipment = new HashSet<>();
        for (ExerciseOption o : chosen) {
            if (o.groupName() != null) {
                usedGroups.add(o.groupName());
            }
            usedEquipment.add(o.equipment());
        }
        while (chosen.size() < limit) {
            ExerciseOption next = fresh.stream()
                    .filter(o -> o.groupName() == null || !usedGroups.contains(o.groupName()))
                    .filter(o -> !usedEquipment.contains(o.equipment()))
                    .findFirst()
                    .orElseGet(() -> fresh.stream()
                            .filter(o -> o.groupName() == null || !usedGroups.contains(o.groupName()))
                            .findFirst()
                            .orElse(null));
            if (next == null) {
                break;
            }
            chosen.add(next);
            fresh.remove(next);
            if (next.groupName() != null) {
                usedGroups.add(next.groupName());
            }
            usedEquipment.add(next.equipment());
        }
        return chosen.stream().filter(Objects::nonNull).limit(limit).toList();
    }
}
