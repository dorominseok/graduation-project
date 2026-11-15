package com.fitness.backend.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationScheduleTest {

    @Test
    @DisplayName("조용한 시간은 22:00부터 08:00 전까지")
    void quietHours() {
        assertTrue(NotificationSchedule.quiet(LocalTime.of(22, 0)));
        assertTrue(NotificationSchedule.quiet(LocalTime.of(23, 59)));
        assertTrue(NotificationSchedule.quiet(LocalTime.of(0, 0)));
        assertTrue(NotificationSchedule.quiet(LocalTime.of(7, 59)));
        assertFalse(NotificationSchedule.quiet(LocalTime.of(8, 0)));
        assertFalse(NotificationSchedule.quiet(LocalTime.of(21, 59)));
    }

    @Test
    @DisplayName("고를 수 있는 시각은 08:00~21:30, 30분 단위 — 조용한 시간과 겹치지 않는다")
    void selectableTimes() {
        assertTrue(NotificationSchedule.selectable(LocalTime.of(8, 0)));
        assertTrue(NotificationSchedule.selectable(LocalTime.of(19, 30)));
        assertTrue(NotificationSchedule.selectable(LocalTime.of(21, 30)));
        assertFalse(NotificationSchedule.selectable(LocalTime.of(7, 30)));
        assertFalse(NotificationSchedule.selectable(LocalTime.of(22, 0)));
        assertFalse(NotificationSchedule.selectable(LocalTime.of(19, 15)));
        assertFalse(NotificationSchedule.selectable(LocalTime.of(19, 0, 30)));
    }

    @Test
    @DisplayName("예약 작업의 칸은 정각·30분으로 내린다 — 몇 초 늦게 깨어나도 같은 칸")
    void slot() {
        assertEquals(LocalTime.of(19, 0), NotificationSchedule.slot(LocalTime.of(19, 0, 3)));
        assertEquals(LocalTime.of(19, 30), NotificationSchedule.slot(LocalTime.of(19, 44)));
    }

    @Test
    @DisplayName("요일은 월 1 · 화 2 · 수 4 … 일 64의 비트 묶음으로 오간다")
    void dayMask() {
        short mask = NotificationSchedule.mask(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        assertEquals(21, mask);
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), NotificationSchedule.days(mask));
        assertTrue(NotificationSchedule.includes(mask, DayOfWeek.WEDNESDAY));
        assertFalse(NotificationSchedule.includes(mask, DayOfWeek.SUNDAY));
        assertEquals(64, NotificationSchedule.mask(EnumSet.of(DayOfWeek.SUNDAY)));
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
                NotificationSchedule.ordered(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)));
    }
}
