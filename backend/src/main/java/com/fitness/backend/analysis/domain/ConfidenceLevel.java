package com.fitness.backend.analysis.domain;

/**
 * 판정 신뢰도. API 명세서 8.2 {@code confidence} / 「기록 방식」 5.5.
 *
 * <p>기록이 적을 때 판정을 <b>숨기지 않고</b> 신뢰도만 낮춰 내려준다. 4주에 두 번
 * 운동한 사람에게 "가슴 부족"은 틀린 말이 아니지만 그 수치로 루틴을 바꾸라고 할
 * 근거는 못 된다. 화면이 그 차이를 표시할 수 있게 값으로 준다.
 */
public enum ConfidenceLevel {
    LOW, NORMAL
}
