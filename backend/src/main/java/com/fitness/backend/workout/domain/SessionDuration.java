package com.fitness.backend.workout.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * 세트 저장 시각으로부터 하루의 운동 시간을 산출한다. 「운동기록_방식_설계서」 4.2 / LOG-03 / LOG-39.
 *
 * <pre>
 *   저장 시각을 정렬해, 간격이 boutGap(30분)을 넘는 곳에서 구간을 나눈다
 *   구간 시간      = (구간의 마지막 세트 − 첫 세트) + 90초
 *   하루 운동 시간 = 첫 세트가 그 날짜에 저장된 구간들의 합. 상한 4시간
 * </pre>
 *
 * <p>시작·종료 버튼이 없다. 세트를 체크하는 행위에서 시간이 저절로 나오고, 마지막 세트가 곧 끝이다.
 *
 * <p>"첫 세트가 그 날짜에 저장된 구간"만 세는 것이 사후 입력을 거른다. 어제 빠뜨린 세트를 오늘
 * 채우면 그 구간은 오늘 시작했으므로 어제 기록의 시간에 들어가지 않는다. 자정을 넘긴 구간은
 * 첫 세트가 그 날짜라 통째로 들어간다.
 */
public final class SessionDuration {

    private SessionDuration() {
    }

    /**
     * @param recordedAts 그날 기록의 전 세트 저장 시각. 순서는 무관하며 내부에서 정렬한다
     * @param performedOn 기록의 날짜
     * @param zone        저장 시각을 날짜로 바꿀 시간대
     * @return 운동 시간. 세트가 없거나 그 날짜에 시작한 구간이 없으면(사후 입력만 있으면) {@code null}
     */
    public static Duration of(List<OffsetDateTime> recordedAts, LocalDate performedOn, ZoneId zone,
                              DurationPolicy policy) {
        if (recordedAts == null || performedOn == null || zone == null || policy == null) {
            throw new IllegalArgumentException("세트 저장 시각·날짜·시간대·보정 규칙은 null일 수 없다");
        }
        List<OffsetDateTime> sorted = recordedAts.stream()
                .peek(t -> {
                    if (t == null) {
                        throw new IllegalArgumentException("세트 저장 시각에 null이 포함될 수 없다");
                    }
                })
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }

        Duration total = Duration.ZERO;
        boolean counted = false;
        int boutStart = 0;
        for (int i = 1; i <= sorted.size(); i++) {
            boolean boutEnds = i == sorted.size()
                    || Duration.between(sorted.get(i - 1), sorted.get(i)).compareTo(policy.boutGap()) > 0;
            if (!boutEnds) {
                continue;
            }
            OffsetDateTime first = sorted.get(boutStart);
            if (first.atZoneSameInstant(zone).toLocalDate().isEqual(performedOn)) {
                total = total.plus(Duration.between(first, sorted.get(i - 1))).plus(policy.lastSetBonus());
                counted = true;
            }
            boutStart = i;
        }
        if (!counted) {
            return null;
        }
        return total.compareTo(policy.dayCap()) > 0 ? policy.dayCap() : total;
    }
}
