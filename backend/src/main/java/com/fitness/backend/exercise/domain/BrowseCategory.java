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
    SHOULDERS("어깨", "shoulders"),
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
    private final List<String> primaryMuscles;

    BrowseCategory(String label, String... primaryMuscles) {
        this.label = label;
        this.primaryMuscles = List.of(primaryMuscles);
    }

    public String label() {
        return label;
    }

    /** 이 분류에 속하는 {@code primary_muscle} 값들. 조회 조건으로 그대로 쓴다 */
    public List<String> primaryMuscles() {
        return primaryMuscles;
    }
}
