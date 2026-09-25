package com.fitness.backend.workout.domain;

/**
 * 세션 입력 경로. 부록 A {@code workout_sessions.source} (V1).
 *
 * <p>사용자에게 고르게 하지 않고 진입 경로로 정한다(명세 6.2) — 홈에서
 * "운동 시작"이면 {@code LIVE}, 캘린더에서 과거 날짜를 눌러 "기록 추가"면
 * {@code BACKFILL}이다.
 *
 * <p>둘을 나누는 이유는 <b>저장 시각을 믿을 수 있는가</b>이다. {@code LIVE}는
 * 세트를 완료 체크한 시각이 곧 운동한 시각이라 운동 시간을 산출할 수 있지만,
 * {@code BACKFILL}은 어제 운동을 오늘 입력한 것이라 산출값이 무의미하다.
 * 그래서 {@code BACKFILL}의 {@code durationSec}은 {@code null}로 두고
 * 사용자가 직접 넣은 보정값만 쓴다(「운동기록_방식_설계서」 4.3).
 */
public enum SessionSource {

    /** 운동하면서 실시간 기록. 저장 시각으로 운동 시간을 산출한다 */
    LIVE,

    /** 지난 운동을 나중에 입력. 운동 시간은 산출하지 않는다 */
    BACKFILL
}
