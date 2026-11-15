package com.fitness.backend.routine.domain;

import com.fitness.backend.exercise.domain.Equipment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 부위별 후보 종목.
 *
 * <pre>
 *   1. 즐겨찾기
 *   2. 해 본 종목 — 최근에 자주 한 순, 최대 {@code historyMax}개
 *   3. 안 해 본 종목 — 입문 대표 종목(순위 순) 먼저. 그다음
 *      ① 흔한 종목(흔한 정도 순): 계열이 겹치지 않는 것 먼저, 자리가 남으면 겹쳐도
 *      ② 나머지(시드 순서): 계열이 겹치지 않는 것, 그 안에서 이미 들어간 후보와 기구가 다른 것 먼저.
 *         자리가 남으면 계열이 겹쳐도
 *   모두 합쳐 최대 {@code limit}개
 * </pre>
 *
 * <p>흔한 종목을 먼저 채우는 것은 종목이 261종으로 늘어서다. 시드 순서로 채우면 케이블 데드리프트·아웃타이
 * 머신이 레그 프레스보다 먼저 들어갔다. 흔한 종목 안에서는 기구를 가르지 않는다 — 대개 어느 헬스장에나
 * 있는 기구라서, 기구를 가르면 바벨 힙 쓰러스트 대신 맨몸 글루트 브릿지가 들어간다.
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
        // 디클라인 벤치프레스·친업이 먼저 나와, 입문 대표 종목을 앞에 둔다
        List<ExerciseOption> fresh = new ArrayList<>(byId.stream()
                .filter(o -> o.recentDays() == 0 && !o.favorite())
                .sorted(STARTER_FIRST)
                .toList());
        // 입문 대표 종목은 기구가 겹쳐도 넣는다. 해 본 종목이 없는 큰 근육을 이 둘로 나누는데(RuleComposer),
        // 기구가 다른 것을 먼저 고르면 랫풀다운 다음의 시티드 케이블 로우(둘 다 케이블)가 후보에서 밀린다
        fresh.stream().filter(o -> o.starterRank() != null).limit(Math.max(0, limit - chosen.size()))
                .toList().forEach(o -> {
                    chosen.add(o);
                    fresh.remove(o);
                });
        Set<String> usedGroups = new HashSet<>();
        Set<Equipment> usedEquipment = new HashSet<>();
        for (ExerciseOption o : chosen) {
            if (o.groupName() != null) {
                usedGroups.add(o.groupName());
            }
            usedEquipment.add(o.equipment());
        }
        List<ExerciseOption> common = new ArrayList<>(fresh.stream()
                .filter(o -> o.commonRank() != null)
                .sorted(Comparator.comparing(ExerciseOption::commonRank).thenComparing(ExerciseOption::id))
                .toList());
        fill(chosen, common, limit, usedGroups, usedEquipment, false);
        List<ExerciseOption> rest = new ArrayList<>(fresh.stream().filter(o -> o.commonRank() == null).toList());
        fill(chosen, rest, limit, usedGroups, usedEquipment, true);
        return chosen.stream().filter(Objects::nonNull).limit(limit).toList();
    }

    /**
     * {@code source}에서 하나씩 꺼내 {@code limit}까지 채운다. 계열이 겹치지 않는 것 먼저,
     * {@code spreadEquipment}면 그 안에서 기구가 다른 것 먼저. 자리가 남으면 계열이 겹쳐도 넣는다.
     */
    private static void fill(Set<ExerciseOption> chosen, List<ExerciseOption> source, int limit,
                             Set<String> usedGroups, Set<Equipment> usedEquipment, boolean spreadEquipment) {
        while (chosen.size() < limit) {
            Optional<ExerciseOption> spread = spreadEquipment
                    ? source.stream()
                            .filter(o -> o.groupName() == null || !usedGroups.contains(o.groupName()))
                            .filter(o -> !usedEquipment.contains(o.equipment()))
                            .findFirst()
                    : Optional.empty();
            ExerciseOption next = spread
                    .or(() -> source.stream()
                            .filter(o -> o.groupName() == null || !usedGroups.contains(o.groupName()))
                            .findFirst())
                    // 어깨 후면은 거의 모든 종목이 한 계열이라, 계열당 하나로 끊으면 후보가 하나뿐이다.
                    // 겹치는 계열을 막는 건 큰 부위에서 한 동작의 변형이 후보를 다 차지하지 않게 하려는 것이다
                    .or(() -> source.stream().findFirst())
                    .orElse(null);
            if (next == null) {
                return;
            }
            chosen.add(next);
            source.remove(next);
            if (next.groupName() != null) {
                usedGroups.add(next.groupName());
            }
            usedEquipment.add(next.equipment());
        }
    }
}
