package com.fitness.backend.notification.domain;

/** 알림 종류(「알림_설계서」 1장). 종류마다 켜고 끈다 */
public enum NotificationType {

    /** 사용자가 고른 요일·시각에, 그날 기록이 없을 때 */
    REMINDER(false),
    /** 사용자가 고른 요일·시각에 주 1회 */
    SUMMARY(false),
    /** 그룹 멤버가 내 기록에 응원했을 때 바로 */
    CHEER(true),
    /** 새 멤버(방장에게) · 방장 위임(새 방장에게) — 바로 */
    GROUP(true);

    private final boolean instant;

    NotificationType(boolean instant) {
        this.instant = instant;
    }

    /** 일이 생긴 순간 보내는 알림인지. 이런 알림만 조용한 시간에 버린다 — 예약 알림은 그 시간에 고를 수 없다 */
    public boolean instant() {
        return instant;
    }
}
