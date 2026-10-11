package com.fitness.backend.workout.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 하루 운동 시간 산출 — 30분 넘는 공백에서 구간을 나누고, 구간마다 +90초, 하루 4시간 상한.
 * 「운동기록_방식_설계서」 4.2 / LOG-03 / LOG-39.
 */
class SessionDurationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 2);
    /** 그날 아침 7시 */
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 9, 2, 7, 0, 0, 0, ZoneOffset.ofHours(9));

    /** 기준 시각으로부터 분 단위 오프셋 목록을 세트 저장 시각으로 만든다. */
    private static List<OffsetDateTime> atMinutes(long... minutes) {
        return Arrays.stream(minutes).mapToObj(BASE::plusMinutes).toList();
    }

    private static Duration of(List<OffsetDateTime> recordedAts) {
        return SessionDuration.of(recordedAts, DAY, SEOUL, DurationPolicy.DEFAULT);
    }

    @Test
    @DisplayName("한 구간이면 첫 세트부터 마지막 세트까지에 마지막 세트 보정 90초를 더한다")
    void singleBout() {
        // 0분 → 4분 → 9분
        assertEquals(Duration.ofMinutes(9).plusSeconds(90), of(atMinutes(0, 4, 9)));
    }

    @Test
    @DisplayName("세트가 하나뿐이면 보정값만 남는다")
    void singleSetIsBonusOnly() {
        assertEquals(Duration.ofSeconds(90), of(atMinutes(0)));
    }

    @Test
    @DisplayName("아침·저녁 두 번 운동하면 두 구간을 더하고 사이 공백은 넣지 않는다")
    void twoBoutsSkipTheGap() {
        // 07:00~07:50 한 번, 19:00~19:40 한 번. 사이 11시간은 빠진다
        Duration d = of(atMinutes(0, 25, 50, 720, 740, 760));
        assertEquals(Duration.ofMinutes(50 + 40).plusSeconds(90 * 2), d);
    }

    @Test
    @DisplayName("정확히 30분 간격은 같은 구간이다 — 넘어야 나뉜다")
    void exactGapStaysInBout() {
        assertEquals(Duration.ofMinutes(30).plusSeconds(90), of(atMinutes(0, 30)));
        assertEquals(Duration.ofSeconds(90 * 2), of(atMinutes(0, 31)));
    }

    @Test
    @DisplayName("다른 날 시작한 구간은 세지 않는다 — 어제 기록에 오늘 채운 세트는 시간에 들어가지 않는다")
    void boutsStartedOnAnotherDayAreBackfill() {
        // 그날 아침 운동 10분 + 다음 날 저녁에 몰아 넣은 세트 3개
        OffsetDateTime nextEvening = BASE.plusDays(1).plusHours(12);
        List<OffsetDateTime> mixed = List.of(BASE, BASE.plusMinutes(10),
                nextEvening, nextEvening.plusSeconds(20), nextEvening.plusSeconds(40));
        assertEquals(Duration.ofMinutes(10).plusSeconds(90), of(mixed));
    }

    @Test
    @DisplayName("사후 입력만 있으면 시간이 없다")
    void backfillOnlyHasNoDuration() {
        OffsetDateTime nextDay = BASE.plusDays(1);
        assertNull(of(List.of(nextDay, nextDay.plusSeconds(30))));
    }

    @Test
    @DisplayName("자정을 넘긴 구간은 첫 세트가 그 날짜라 통째로 들어간다")
    void boutCrossingMidnightCounts() {
        OffsetDateTime lateNight = OffsetDateTime.of(2026, 9, 2, 23, 40, 0, 0, ZoneOffset.ofHours(9));
        List<OffsetDateTime> sets = List.of(lateNight, lateNight.plusMinutes(20), lateNight.plusMinutes(40));
        assertEquals(Duration.ofMinutes(40).plusSeconds(90), of(sets));
    }

    @Test
    @DisplayName("날짜는 기록의 시간대로 가른다 — UTC로 보면 전날인 아침 운동도 그날이다")
    void datesAreInRecordZone() {
        // 서울 아침 7시는 UTC로 전날 22시다
        OffsetDateTime utc = BASE.withOffsetSameInstant(ZoneOffset.UTC);
        assertEquals(Duration.ofSeconds(90), of(List.of(utc)));
    }

    @Test
    @DisplayName("하루 상한 4시간을 넘지 않는다")
    void capsDayTotal() {
        // 20분 간격으로 5시간
        long[] minutes = new long[16];
        for (int i = 0; i < minutes.length; i++) {
            minutes[i] = i * 20L;
        }
        assertEquals(Duration.ofHours(4), of(atMinutes(minutes)));
    }

    @Test
    @DisplayName("저장 시각이 뒤섞여 들어와도 정렬 후 계산한다")
    void ordersInput() {
        assertEquals(of(atMinutes(0, 4, 9)), of(atMinutes(9, 0, 4)));
    }

    @Test
    @DisplayName("세트가 없으면 시간이 없다")
    void emptyHasNoDuration() {
        assertNull(of(List.of()));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionDuration.of(null, DAY, SEOUL, DurationPolicy.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> of(Arrays.asList(BASE, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new DurationPolicy(Duration.ofMinutes(-1), Duration.ZERO, Duration.ofHours(4)));
    }
}
