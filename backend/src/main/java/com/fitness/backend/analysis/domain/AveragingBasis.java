package com.fitness.backend.analysis.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 주당 평균의 분모 — 첫 기록일부터 기준일까지 지난 일수(최소 7일, 최대 집계 구간).
 *
 * <p>기록을 시작한 지 28일이 안 된 사람을 28일로 나누면 오지 않은 날이 "안 한 날"로 섞여
 * 판정이 실제보다 낮게 나온다. 기록이 있는 날이 아니라 지난 날로 세므로 쉰 기간은 분모에 들어간다.
 * 최솟값은 첫날 5세트가 "주 35세트 · 과다"가 되지 않게 둔다.
 */
public final class AveragingBasis {

    private AveragingBasis() {
    }

    /**
     * @param firstRecordOn 기준일 이전(포함) 첫 운동한 날. 없으면 {@code null}
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
