package com.fitness.backend.workout.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 가져온 세트의 저장 시각(「기록가져오기_설계서」 6장).
 *
 * <p>가져온 세트는 실제로 언제 했는지 모른다. 그날 정오부터 세트마다 1초씩 매긴다 — 순서가 지켜지고, 자정에서
 * 멀어 "자정 넘겨 이어지는 어제 기록" 판정(명세 6.1)에 끼지 않는다.
 * <ul>
 *   <li>정오가 아직 안 됐으면(오늘 일지를 오전에 가져올 때) 마지막 세트가 지금 1초 전에 오도록 당긴다.
 *   <li>그날 세트가 이미 있고 그 마지막이 이보다 늦으면 그 1초 뒤부터 — 이어 붙인다(결정 6).
 * </ul>
 */
public final class ImportedSetTimes {

    private ImportedSetTimes() {
    }

    /**
     * @param lastExisting 그날 이미 있는 마지막 세트의 저장 시각. 없으면 null
     * @param count        가져올 세트 수
     * @return 첫 세트의 저장 시각. {@code i}번째(0부터) 세트는 여기에 {@code i}초를 더한다
     */
    public static OffsetDateTime start(LocalDate date, ZoneId zone, OffsetDateTime lastExisting, int count,
                                       OffsetDateTime now) {
        OffsetDateTime start = date.atTime(LocalTime.NOON).atZone(zone).toOffsetDateTime();
        OffsetDateTime latest = now.minusSeconds(count);
        if (start.isAfter(latest)) {
            start = latest;
        }
        if (lastExisting != null && !lastExisting.isBefore(start)) {
            start = lastExisting.plusSeconds(1);
        }
        return start;
    }
}
