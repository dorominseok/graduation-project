package com.fitness.backend.exercise.domain;

import java.util.List;

/**
 * 종목 <b>탐색용</b> 분류 12종 (LOG-24).
 *
 * <p>화면의 부위 그리드가 이 단위다. {@link BodyPart}(표시 6종)와도, 분석의 판정 부위
 * 9종과도 다른 축이다 — 저쪽 둘은 집계·판정 기준이고 이쪽은 "사용자가 종목을 찾을 때
 * 떠올리는 묶음"이다. 6종으로는 팔 하나에 삼두·이두·전완이 뭉쳐 47종이 되어
 * 목록을 훑는 것 말고는 방법이 없다.
 *
 * <p>컬럼을 새로 두지 않고 {@code primary_muscle} 값을 묶어서 쓴다. 이미 있는 값으로
 * 정의되는 분류라 저장해두면 두 곳이 어긋날 수 있다.
 */
public enum BrowseCategory {

    CHEST("가슴", "chest"),
    BACK("등", "lats", "middle back"),
    /**
     * 어깨는 판정과 같이 전면·후면으로 나눈다(LOG-33). 하나로 두면 홈 약점에서 "어깨 후면
     * 부족"을 보고 종목을 추가하러 왔을 때 앞뒤가 섞인 13종에서 후면 3종을 직접 골라내야 한다.
     * 측면 레이즈는 판정과 같이 앞쪽에 둔다 — 밀기로 집계하는 분류다(분석 설계서 4.3). 그래서 이름이
     * "전면"이 아니라 "전·측면"이다 — 측면 삼각근 운동이 "전면" 아래 있으면 이름이 틀린다.
     */
    SHOULDERS_FRONT("어깨 전·측면", DeltRegion.FRONT, "shoulders"),
    SHOULDERS_REAR("어깨 후면", DeltRegion.REAR, "shoulders"),
    TRAPS("승모근", "traps"),
    TRICEPS("삼두", "triceps"),
    BICEPS("이두", "biceps"),
    FOREARMS("전완", "forearms"),
    ABS("복부", "abdominals"),
    LOWER_BACK("허리", "lower back"),
    GLUTES("엉덩이", "glutes", "abductors"),
    LEGS("하체", "quadriceps", "hamstrings"),
    CALVES("종아리", "calves");

    private final String label;
    private final DeltRegion deltRegion;
    private final List<String> primaryMuscles;

    BrowseCategory(String label, String... primaryMuscles) {
        this(label, null, primaryMuscles);
    }

    BrowseCategory(String label, DeltRegion deltRegion, String... primaryMuscles) {
        this.label = label;
        this.deltRegion = deltRegion;
        this.primaryMuscles = List.of(primaryMuscles);
    }

    public String label() {
        return label;
    }

    /** 이 분류에 속하는 {@code primary_muscle} 값들. 조회 조건으로 그대로 쓴다 */
    public List<String> primaryMuscles() {
        return primaryMuscles;
    }

    /** 어깨 전·측면·후면만 값이 있다. 나머지는 {@code null} — 조건을 걸지 않는다 */
    public DeltRegion deltRegion() {
        return deltRegion;
    }
}
