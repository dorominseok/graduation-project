package com.fitness.backend.routine.llm.experiment;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.analysis.domain.MuscleGroup.CHEST;
import static com.fitness.backend.analysis.domain.MuscleGroup.CORE;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_FRONT;
import static com.fitness.backend.analysis.domain.MuscleGroup.DELT_REAR;
import static com.fitness.backend.analysis.domain.MuscleGroup.POSTERIOR;
import static com.fitness.backend.analysis.domain.MuscleGroup.QUADS;
import static com.fitness.backend.analysis.domain.MuscleGroup.TRICEPS;

import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.routine.domain.MuscleState;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import com.fitness.backend.routine.domain.SlotPlanner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오프라인 실험 케이스. 「루틴생성_로직_설계서」 11.5의 표에 즐겨찾기 케이스를 더했다.
 *
 * <p>상태(주당 세트·판정·마지막 운동일)는 손으로 정하고, 부위·세트·후보는 운영과 같은 규칙
 * (SlotPlanner·CandidatePicker)이 만든다 — 실험 입력이 운영 입력과 같은 길로 나온다.
 */
final class ExperimentCases {

    private ExperimentCases() {
    }

    /**
     * @param recent     최근 8주에 이 종목을 한 날 수 (이름 → 일). 들어 있으면 해 본 종목이다
     * @param favorites  즐겨찾기한 종목 이름
     */
    record Case(String id, String goal, int daysPerWeek, List<MuscleState> states, List<Imbalance> imbalances,
                Set<String> favorites, Map<String, Integer> recent) {
    }

    static List<Case> all(LocalDate today) {
        return List.of(
                new Case("01-no-history", "기록 없음 — 첫날 루틴(설계서 2.6)", 3,
                        states(today, VolumeVerdict.INSUFFICIENT, "0", null), List.of(), Set.of(), Map.of()),

                new Case("02-push-heavy", "밀기 편중 — 당기기(등·어깨 후면·이두) 보정, R3", 4,
                        states(today, VolumeVerdict.OPTIMAL, "12", -4,
                                s(CHEST, "14", VolumeVerdict.OPTIMAL, today, -3),
                                s(DELT_FRONT, "12", VolumeVerdict.OPTIMAL, today, -3),
                                s(TRICEPS, "11", VolumeVerdict.OPTIMAL, today, -3),
                                lesser(BACK, "2", today, -6),
                                lesser(DELT_REAR, "0", today, null),
                                lesser(BICEPS, "1", today, -6),
                                s(POSTERIOR, "9", VolumeVerdict.BELOW_RECOMMENDED, today, -4),
                                s(CORE, "4", VolumeVerdict.BELOW_RECOMMENDED, today, -5)),
                        List.of(new Imbalance(BalanceSide.PULL, BalanceSide.PUSH)), Set.of(),
                        Map.of("바벨 벤치프레스", 8, "덤벨 숄더프레스", 6, "케이블 푸시다운", 6, "바벨 스쿼트", 4,
                                "루마니안 데드리프트", 4, "랫풀다운: 와이드 그립", 2, "덤벨 컬", 2)),

                new Case("03-no-legs", "하체 미수행 — 하체 우선, 하루 총량을 상체로 채움", 4,
                        states(today, VolumeVerdict.OPTIMAL, "12", -3,
                                s(BACK, "12", VolumeVerdict.OPTIMAL, today, -6),
                                s(DELT_REAR, "10", VolumeVerdict.OPTIMAL, today, -5),
                                lesser(QUADS, "0", today, null),
                                lesser(POSTERIOR, "0", today, null),
                                s(CORE, "6", VolumeVerdict.BELOW_RECOMMENDED, today, -3)),
                        List.of(new Imbalance(BalanceSide.LOWER, BalanceSide.UPPER)), Set.of(),
                        Map.of("바벨 벤치프레스", 8, "랫풀다운: 와이드 그립", 8, "시티드 케이블 로우", 6,
                                "덤벨 숄더프레스", 6, "페이스풀", 4, "덤벨 컬", 6, "케이블 푸시다운", 6, "플랭크", 3)),

                new Case("04-rear-delt-gap", "어깨 후면만 0 — 후보가 3개뿐인 슬롯 + 최적 부위로 채우기", 3,
                        states(today, VolumeVerdict.OPTIMAL, "12", -3,
                                s(BACK, "12", VolumeVerdict.OPTIMAL, today, -5),
                                s(QUADS, "11", VolumeVerdict.OPTIMAL, today, -4),
                                s(DELT_REAR, "0", VolumeVerdict.INSUFFICIENT, today, null)),
                        List.of(), Set.of(),
                        Map.of("바벨 벤치프레스", 8, "바벨 로우", 6, "바벨 스쿼트", 6, "루마니안 데드리프트", 5,
                                "밀리터리 프레스", 5, "이지바 스컬크러셔", 4, "바벨 컬", 4, "플랭크", 4)),

                new Case("05-twice-a-week", "주 2회 — 부위당 5세트, 두 종목으로 나눌 여지", 2,
                        states(today, VolumeVerdict.BELOW_RECOMMENDED, "6", -7,
                                s(CHEST, "2", VolumeVerdict.INSUFFICIENT, today, -7),
                                s(BACK, "2", VolumeVerdict.INSUFFICIENT, today, -7),
                                s(QUADS, "0", VolumeVerdict.INSUFFICIENT, today, -10)),
                        List.of(), Set.of(),
                        Map.of("덤벨 벤치프레스", 3, "머신 체스트프레스", 2, "랫풀다운: 와이드 그립", 3,
                                "원암 덤벨 로우", 2, "레그 익스텐션", 2)),

                new Case("06-five-a-week", "주 5회 — 부위당 2세트로 아홉 부위, 처음 종목 몰림", 5,
                        states(today, VolumeVerdict.INSUFFICIENT, "2", -5), List.of(), Set.of(),
                        Map.of("바벨 벤치프레스", 3, "랫풀다운: 와이드 그립", 3, "바벨 스쿼트", 2)),

                new Case("07-yesterday-chest-back", "어제 가슴·등 — 회복 제외", 3,
                        states(today, VolumeVerdict.INSUFFICIENT, "1", -6,
                                s(CHEST, "3", VolumeVerdict.INSUFFICIENT, today, -1),
                                s(BACK, "3", VolumeVerdict.INSUFFICIENT, today, -1)),
                        List.of(), Set.of(),
                        Map.of("바벨 벤치프레스", 2, "랫풀다운: 와이드 그립", 2)),

                new Case("08-favorites", "즐겨찾기 다수 — 후보 순서를 따르는가", 3,
                        states(today, VolumeVerdict.INSUFFICIENT, "2", -6), List.of(),
                        Set.of("덤벨 플라이", "티바 로우", "레그 익스텐션", "라잉 레그컬"),
                        Map.of("덤벨 플라이", 3, "바벨 벤치프레스", 2, "티바 로우", 2)));
    }

    // ── 상태 만들기

    /** 아홉 부위를 같은 상태로 두고 일부만 바꾼다. {@code daysAgo}가 null이면 한 적 없음 */
    private static List<MuscleState> states(LocalDate today, VolumeVerdict verdict, String weekly, Integer daysAgo,
                                            MuscleState... overrides) {
        Map<MuscleGroup, MuscleState> map = new EnumMap<>(MuscleGroup.class);
        for (MuscleGroup m : SlotPlanner.SIZE_ORDER) {
            map.put(m, s(m, weekly, verdict, today, daysAgo));
        }
        for (MuscleState o : overrides) {
            map.put(o.muscle(), o);
        }
        return new ArrayList<>(map.values());
    }

    private static MuscleState s(MuscleGroup m, String weekly, VolumeVerdict verdict, LocalDate today, Integer daysAgo) {
        return new MuscleState(m, new BigDecimal(weekly), verdict, daysAgo == null ? null : today.plusDays(daysAgo),
                false);
    }

    /** 불균형의 적은 쪽. 판정은 부족 */
    private static MuscleState lesser(MuscleGroup m, String weekly, LocalDate today, Integer daysAgo) {
        return new MuscleState(m, new BigDecimal(weekly), VolumeVerdict.INSUFFICIENT,
                daysAgo == null ? null : today.plusDays(daysAgo), true);
    }
}
