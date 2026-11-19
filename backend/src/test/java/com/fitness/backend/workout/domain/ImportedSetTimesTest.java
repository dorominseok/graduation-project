package com.fitness.backend.workout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가져온 세트의 저장 시각(「기록가져오기_설계서」 6장) */
class ImportedSetTimesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final OffsetDateTime LATER = OffsetDateTime.parse("2026-11-19T21:00:00+09:00");

    @Test
    @DisplayName("지난 빈 날은 그날 정오부터")
    void noonOfThatDay() {
        assertThat(ImportedSetTimes.start(DAY, SEOUL, null, 54, LATER))
                .isEqualTo(OffsetDateTime.parse("2026-11-17T12:00:00+09:00"));
    }

    @Test
    @DisplayName("그날 세트가 정오보다 늦게 있으면 그 1초 뒤부터 — 이어 붙인다. 오전 세트만 있으면 정오")
    void appendAfterExisting() {
        assertThat(ImportedSetTimes.start(DAY, SEOUL, OffsetDateTime.parse("2026-11-17T19:30:00+09:00"), 5, LATER))
                .isEqualTo(OffsetDateTime.parse("2026-11-17T19:30:01+09:00"));
        assertThat(ImportedSetTimes.start(DAY, SEOUL, OffsetDateTime.parse("2026-11-17T09:00:00+09:00"), 5, LATER))
                .isEqualTo(OffsetDateTime.parse("2026-11-17T12:00:00+09:00"));
    }

    @Test
    @DisplayName("오늘 오전에 오늘 일지를 가져오면 마지막 세트가 지금 1초 전에 온다")
    void notInTheFuture() {
        OffsetDateTime morning = OffsetDateTime.parse("2026-11-19T10:00:00+09:00");
        assertThat(ImportedSetTimes.start(LocalDate.of(2026, 11, 19), SEOUL, null, 5, morning))
                .isEqualTo(OffsetDateTime.parse("2026-11-19T09:59:55+09:00"));
    }

    @Test
    @DisplayName("방금 넣은 세트가 있으면 그 뒤에 — 순서가 지켜진다")
    void appendAfterJustRecorded() {
        OffsetDateTime morning = OffsetDateTime.parse("2026-11-19T10:00:00+09:00");
        OffsetDateTime justNow = OffsetDateTime.parse("2026-11-19T09:59:58+09:00");
        assertThat(ImportedSetTimes.start(LocalDate.of(2026, 11, 19), SEOUL, justNow, 5, morning))
                .isEqualTo(OffsetDateTime.parse("2026-11-19T09:59:59+09:00"));
    }
}
