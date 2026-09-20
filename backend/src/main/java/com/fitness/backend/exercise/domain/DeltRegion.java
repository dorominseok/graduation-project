package com.fitness.backend.exercise.domain;

/**
 * 어깨 앞·뒤 구분. {@code primary_muscle = 'shoulders'}인 종목만 값을 갖는다.
 *
 * <p>이 컬럼이 있는 이유는 어깨를 하나로 묶으면 후면 삼각근 부족이 전면에 가려
 * 드러나지 않기 때문이다(명세 8.1, LOG-09). 화면 분류에는 쓰지 않고 판정에만 쓴다.
 */
public enum DeltRegion {
    FRONT, REAR
}
