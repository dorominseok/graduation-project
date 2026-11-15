package com.fitness.backend.notification.domain;

import com.fitness.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 알림 설정(「알림_설계서」 2장). 알림을 처음 켤 때(구독할 때) 기본값으로 만든다.
 *
 * <p>요일은 비트 묶음({@link NotificationSchedule#mask})으로, 시각은 30분 단위 {@link LocalTime}으로 둔다.
 * 값의 범위는 DB 제약과 {@link NotificationSchedule#selectable}이 같이 막는다.
 */
@Entity
@Getter
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings extends BaseTimeEntity {

    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(19, 0);
    public static final DayOfWeek DEFAULT_SUMMARY_DAY = DayOfWeek.SUNDAY;
    public static final LocalTime DEFAULT_SUMMARY_TIME = LocalTime.of(20, 0);

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;

    @Column(name = "reminder_days", nullable = false)
    private short reminderDays;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;

    @Column(name = "summary_enabled", nullable = false)
    private boolean summaryEnabled;

    /** ISO 요일 1(월) ~ 7(일) */
    @Column(name = "summary_day", nullable = false)
    private short summaryDay;

    @Column(name = "summary_time", nullable = false)
    private LocalTime summaryTime;

    @Column(name = "cheer_enabled", nullable = false)
    private boolean cheerEnabled;

    @Column(name = "group_enabled", nullable = false)
    private boolean groupEnabled;

    /**
     * 기본값 — 네 종류 모두 켜고, 리마인더는 {@code reminderDays}(부르는 쪽이 최근 기록에서 고른 요일) 저녁 7시,
     * 주간 요약은 일요일 저녁 8시.
     */
    public static NotificationSettings defaults(Long userId, Set<DayOfWeek> reminderDays) {
        NotificationSettings settings = new NotificationSettings();
        settings.userId = userId;
        settings.update(true, reminderDays, DEFAULT_REMINDER_TIME, true, DEFAULT_SUMMARY_DAY, DEFAULT_SUMMARY_TIME,
                true, true);
        return settings;
    }

    public void update(boolean reminderEnabled, Set<DayOfWeek> reminderDays, LocalTime reminderTime,
                       boolean summaryEnabled, DayOfWeek summaryDay, LocalTime summaryTime,
                       boolean cheerEnabled, boolean groupEnabled) {
        this.reminderEnabled = reminderEnabled;
        this.reminderDays = NotificationSchedule.mask(reminderDays);
        this.reminderTime = reminderTime;
        this.summaryEnabled = summaryEnabled;
        this.summaryDay = (short) summaryDay.getValue();
        this.summaryTime = summaryTime;
        this.cheerEnabled = cheerEnabled;
        this.groupEnabled = groupEnabled;
    }

    public Set<DayOfWeek> reminderDaySet() {
        return NotificationSchedule.days(reminderDays);
    }

    public DayOfWeek summaryDayOfWeek() {
        return DayOfWeek.of(summaryDay);
    }

    public boolean enabled(NotificationType type) {
        return switch (type) {
            case REMINDER -> reminderEnabled;
            case SUMMARY -> summaryEnabled;
            case CHEER -> cheerEnabled;
            case GROUP -> groupEnabled;
        };
    }
}
