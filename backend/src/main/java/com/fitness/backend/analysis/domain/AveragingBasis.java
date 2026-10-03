package com.fitness.backend.analysis.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 주당 평균의 분모를 며칠로 잡을지. 분석 설계서 2.1 / LOG-31.
 *
 * <p>집계 구간은 28일인데 기록을 시작한 지 그보다 짧으면, 28일로 나누는 순간 아직
 * 오지도 않은 날들이 "운동 안 한 날"로 섞인다. 첫 주에 가슴을 12세트 한 사람이
 * {@code 12 ÷ 4 = 3세트 · 부족}으로 판정된다. 검증 참여자는 전원 기록 0에서 시작하므로
 * 이대로면 검증 첫 3주 동안 모든 판정이 실제보다 낮게 나온다.
 *
 * <p>그래서 분모를 <b>첫 기록일부터 기준일까지</b>로 줄인다. 두 가지를 일부러 정했다.
 * <ul>
 *   <li><b>기록이 있는 날이 아니라 지난 날</b>로 센다. 2주 쉬었다면 그 2주는 실제로
 *       안 한 기간이라 평균에 들어가야 한다. 기록 있는 주만 세면 쉰 기간이 지워진다.</li>
 *   <li><b>최솟값</b>을 둔다(기본 7일). 첫날 5세트를 하루로 나누면 "주 35세트 · 과다"가 된다.</li>
 * </ul>
 * 기록이 28일을 넘기면 지금까지와 똑같이 28일(= 4주)로 나눈다.
 */
public final class AveragingBasis {

    private AveragingBasis() {
    }

    /**
     * @param firstRecordOn 기준일 이전(포함) 첫 완료 기록일. 없으면 {@code null}
     * @param referenceDate 기준일
     * @param windowDays    집계 구간 길이(보통 28)
     * @param minDays       분모 최솟값(보통 7)
     * @return 주당 평균을 낼 때 나눌 일수. {@code [min(minDays, windowDays), windowDays]}
     */
    public static int days(LocalDate firstRecordOn, LocalDate referenceDate, int windowDays, int minDays) {
        if (referenceDate == null) {
            throw new IllegalArgumentException("기준일은 null일 수 없다");
        }
        if (windowDays < 1 || minDays < 1) {
            throw new IllegalArgumentException("일수는 1 이상이어야 한다: %d / %d".formatted(windowDays, minDays));
        }
        if (firstRecordOn == null) {
            // 기록이 없으면 세트도 0이라 무엇으로 나눠도 0이다. 구간 그대로 둔다
            return windowDays;
        }
        if (firstRecordOn.isAfter(referenceDate)) {
            throw new IllegalArgumentException("첫 기록일이 기준일 뒤다: " + firstRecordOn);
        }
        long elapsed = ChronoUnit.DAYS.between(firstRecordOn, referenceDate) + 1;
        long floor = Math.min(minDays, windowDays);
        return (int) Math.max(floor, Math.min(elapsed, windowDays));
    }
}
