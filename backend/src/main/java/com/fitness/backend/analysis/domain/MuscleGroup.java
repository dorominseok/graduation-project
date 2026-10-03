package com.fitness.backend.analysis.domain;

import com.fitness.backend.exercise.domain.DeltRegion;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 약점 판정 단위. API 명세서 8.1 / 「운동 분석 로직 설계서」 2.3 / LOG-09.
 *
 * <p><b>{@code BodyPart} 6종과 다른 축이다.</b> {@code BodyPart}는 종목을 고를 때
 * 쓰는 분류이고, 이쪽은 {@code primary_muscle}에서 유도한 판정 단위다. 둘을 섞으면
 * "팔 12세트 · 최적"이 삼두 0세트를 가린다 — LOG-09를 쓴 이유가 그것이다.
 *
 * <p>주동근에만 카운트한다(명세 8.2). 벤치프레스가 삼두를 쓰는 것은 맞지만 간접
 * 자극까지 세면 어느 부위든 늘 충분해 보여 판정이 무의미해진다.
 */
public enum MuscleGroup {

    CHEST("가슴", true),
    BACK("등", true),
    DELT_FRONT("어깨 전면", true),
    DELT_REAR("어깨 후면", true),
    TRICEPS("삼두", true),
    BICEPS("이두", true),
    QUADS("앞허벅지", true),
    POSTERIOR("뒤허벅지·둔근", true),
    CORE("코어", true),

    /** 아래 둘은 세트 수만 내려주고 부족 판정은 하지 않는다(분석 2.3). */
    CALVES("종아리", false),
    FOREARMS("전완", false);

    /** {@code primary_muscle} → 판정 부위. {@code shoulders}만 이 표에 없다 — 앞뒤를 갈라야 해서다. */
    private static final Map<String, MuscleGroup> BY_PRIMARY_MUSCLE = Map.ofEntries(
            Map.entry("chest", CHEST),
            Map.entry("lats", BACK),
            Map.entry("middle back", BACK),
            Map.entry("traps", BACK),
            Map.entry("lower back", BACK),
            Map.entry("triceps", TRICEPS),
            Map.entry("biceps", BICEPS),
            Map.entry("quadriceps", QUADS),
            Map.entry("abductors", QUADS),
            Map.entry("adductors", QUADS),
            Map.entry("hamstrings", POSTERIOR),
            Map.entry("glutes", POSTERIOR),
            Map.entry("abdominals", CORE),
            Map.entry("calves", CALVES),
            Map.entry("forearms", FOREARMS));

    private static final String SHOULDERS = "shoulders";

    private final String label;
    private final boolean judged;

    MuscleGroup(String label, boolean judged) {
        this.label = label;
        this.judged = judged;
    }

    /** 화면 표기용 한국어 라벨. */
    public String label() {
        return label;
    }

    /** 부족 판정 대상인지. {@code false}면 응답의 {@code displayOnly}로 간다. */
    public boolean judged() {
        return judged;
    }

    /**
     * 종목의 {@code primary_muscle}·{@code delt_region}으로 판정 부위를 정한다.
     *
     * <p>어깨인데 {@code deltRegion}이 없으면 <b>빈 값</b>을 준다. 전량
     * {@code DELT_FRONT}로 몰아넣는 폴백은 쓰지 않는다 — 그러면 전 사용자에게
     * "어깨(뒤) 부족"과 당기기 불균형 경고가 상시 뜬다(명세 8.1). 대신 집계에서
     * 빼고 {@code shoulderSplitResolved = false}로 신호한다.
     *
     * <p>현재 시드 데이터의 어깨 13종은 모두 값이 채워져 있어 정상 운영에서는
     * 빈 값이 나오지 않는다.
     */
    public static Optional<MuscleGroup> of(String primaryMuscle, DeltRegion deltRegion) {
        if (primaryMuscle == null) {
            return Optional.empty();
        }
        String key = primaryMuscle.trim().toLowerCase();
        if (SHOULDERS.equals(key)) {
            if (deltRegion == null) {
                return Optional.empty();
            }
            return Optional.of(deltRegion == DeltRegion.REAR ? DELT_REAR : DELT_FRONT);
        }
        return Optional.ofNullable(BY_PRIMARY_MUSCLE.get(key));
    }

    /**
     * 이 판정 부위에 속하는 {@code primary_muscle} 값들. 종목 조회 조건으로 쓴다.
     *
     * <p>{@link #of}의 역방향이다. 표를 따로 두지 않고 같은 표에서 뽑아 두 방향이
     * 어긋날 수 없게 한다.
     */
    public List<String> primaryMuscles() {
        if (this == DELT_FRONT || this == DELT_REAR) {
            return List.of(SHOULDERS);
        }
        return BY_PRIMARY_MUSCLE.entrySet().stream()
                .filter(e -> e.getValue() == this)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** 어깨 앞·뒤만 값이 있다. 나머지는 {@code null} — 조건을 걸지 않는다. */
    public DeltRegion deltRegion() {
        return switch (this) {
            case DELT_FRONT -> DeltRegion.FRONT;
            case DELT_REAR -> DeltRegion.REAR;
            default -> null;
        };
    }

    /** 어깨 종목인지. {@code shoulderSplitResolved} 계산에 쓴다. */
    public static boolean isShoulder(String primaryMuscle) {
        return primaryMuscle != null && SHOULDERS.equals(primaryMuscle.trim().toLowerCase());
    }
}
