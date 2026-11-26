package com.fitness.backend.notification.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 알림 시각 규칙. DB·스프링에 의존하지 않는 순수 계산이다.
 *
 * <pre>
 *   고를 수 있는 시각 00:00 ~ 23:30, 30분 단위
 *   요일 묶음     월 1 · 화 2 · 수 4 · 목 8 · 금 16 · 토 32 · 일 64
 * </pre>
 *
 * <p>조용한 시간(22:00~08:00 — 바로 보내는 알림을 버리고 그 시간은 고를 수 없게)은 2026-11-28에 뺐다(사용자 요청,
 * 결정 기록 D-17). 새벽에 확인할 수 없어서다. 다시 둔다면 사용자가 고르는 방식으로(「알림_설계서」 7장 다음 과제).
 */
public final class NotificationSchedule {

    public static final LocalTime EARLIEST = LocalTime.of(0, 0);
    public static final LocalTime LATEST = LocalTime.of(23, 30);
    private static final int STEP_MINUTES = 30;

    private NotificationSchedule() {
    }

    /** 리마인더·요약에 고를 수 있는 시각인지 */
    public static boolean selectable(LocalTime time) {
        return !time.isBefore(EARLIEST) && !time.isAfter(LATEST)
                && time.getMinute() % STEP_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    /** 예약 작업이 도는 칸(정각·30분)으로 내린다. 크론이 몇 초 늦게 깨어나도 같은 칸이다 */
    public static LocalTime slot(LocalTime time) {
        return LocalTime.of(time.getHour(), time.getMinute() - time.getMinute() % STEP_MINUTES);
    }

    public static short mask(Set<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return (short) mask;
    }

    public static Set<DayOfWeek> days(short mask) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & bit(day)) != 0) {
                days.add(day);
            }
        }
        return days;
    }

    public static boolean includes(short mask, DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    /** 요일 순서(월→일)로 */
    public static List<DayOfWeek> ordered(Set<DayOfWeek> days) {
        return days.stream().sorted().toList();
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
