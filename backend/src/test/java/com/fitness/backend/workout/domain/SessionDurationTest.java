package com.fitness.backend.workout.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운동 시간 산출 — 15분 캡 · +90초 보정 · 4시간 상한. 「운동기록_방식_설계서」 4.2 / LOG-03. */
class SessionDurationTest {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 9, 2, 19, 0, 0, 0, ZoneOffset.ofHours(9));

    /** 기준 시각으로부터 분 단위 오프셋 목록을 세트 저장 시각으로 만든다. */
    private static List<OffsetDateTime> atMinutes(long... minutes) {
        return Arrays.stream(minutes).mapToObj(BASE::plusMinutes).toList();
    }

    @Test
    @DisplayName("세트 간격의 합에 마지막 세트 보정 90초를 더한다")
    void sumsGapsPlusBonus() {
        // 0분 → 4분 → 9분 : 간격 4 + 5 = 9분, + 90초
        Duration d = SessionDuration.of(atMinutes(0, 4, 9), DurationPolicy.DEFAULT);
        assertEquals(Duration.ofMinutes(9).plusSeconds(90), d);
    }

    @Test
    @DisplayName("세트가 하나뿐이면 간격이 없고 보정값만 남는다")
    void singleSetIsBonusOnly() {
        assertEquals(Duration.ofSeconds(90), SessionDuration.of(atMinutes(0), DurationPolicy.DEFAULT));
    }

    @Test
    @DisplayName("앱을 닫고 나갔다가 2시간 뒤 입력해도 그 간격은 15분으로 캡된다")
    void capsLongGap() {
        // 0분 → 5분 → 125분 : 5 + min(120, 15) = 20분, + 90초
        Duration d = SessionDuration.of(atMinutes(0, 5, 125), DurationPolicy.DEFAULT);
        assertEquals(Duration.ofMinutes(20).plusSeconds(90), d);
    }

    @Test
    @DisplayName("정확히 15분 간격은 캡되지 않는다")
    void exactCapIsNotTrimmed() {
        Duration d = SessionDuration.of(atMinutes(0, 15), DurationPolicy.DEFAULT);
        assertEquals(Duration.ofMinutes(15).plusSeconds(90), d);
    }

    @Test
    @DisplayName("캡을 통과한 값이 누적되어도 세션 상한 4시간을 넘지 않는다")
    void capsSessionTotal() {
        // 15분 간격 20개 = 300분(5시간) → 4시간으로 잘린다
        long[] minutes = new long[21];
        for (int i = 0; i <= 20; i++) {
            minutes[i] = i * 15L;
        }
        assertEquals(Duration.ofHours(4), SessionDuration.of(atMinutes(minutes), DurationPolicy.DEFAULT));
    }

    @Test
    @DisplayName("저장 시각이 뒤섞여 들어와도 정렬 후 계산한다")
    void ordersInput() {
        Duration shuffled = SessionDuration.of(atMinutes(9, 0, 4), DurationPolicy.DEFAULT);
        Duration ordered = SessionDuration.of(atMinutes(0, 4, 9), DurationPolicy.DEFAULT);
        assertEquals(ordered, shuffled);
    }

    @Test
    @DisplayName("세트가 없으면 0 — 종료는 422로 막히지만 함수 단독으로도 정의된다")
    void emptyIsZero() {
        assertEquals(Duration.ZERO, SessionDuration.of(List.of(), DurationPolicy.DEFAULT));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> SessionDuration.of(null, DurationPolicy.DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> SessionDuration.of(Arrays.asList(BASE, null), DurationPolicy.DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> new DurationPolicy(Duration.ofMinutes(-1), Duration.ZERO, Duration.ofHours(4)));
    }
}
