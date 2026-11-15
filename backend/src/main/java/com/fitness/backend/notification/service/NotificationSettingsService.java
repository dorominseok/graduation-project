package com.fitness.backend.notification.service;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.notification.domain.NotificationSchedule;
import com.fitness.backend.notification.domain.NotificationSettings;
import com.fitness.backend.notification.domain.PushSubscription;
import com.fitness.backend.notification.push.EcKeys;
import com.fitness.backend.notification.push.PushGateway;
import com.fitness.backend.notification.repository.NotificationSettingsRepository;
import com.fitness.backend.notification.repository.PushSubscriptionRepository;
import com.fitness.backend.notification.web.NotificationDtos.Reminder;
import com.fitness.backend.notification.web.NotificationDtos.SettingsResponse;
import com.fitness.backend.notification.web.NotificationDtos.Summary;
import com.fitness.backend.notification.web.NotificationDtos.UpdateRequest;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정과 구독(「알림_설계서」 2·4장).
 *
 * <p>설정은 알림을 처음 켤 때(구독할 때) 기본값으로 만든다. 그 전에는 저장하지 않고 기본값을 보여 준다 —
 * 리마인더 요일은 최근 4주에 두 번 이상 운동한 요일, 없으면 월·수·금.
 */
@Service
@Transactional(readOnly = true)
public class NotificationSettingsService {

    /** 최근 이 일수 동안 */
    private static final int SUGGEST_WINDOW_DAYS = 28;
    /** 같은 요일에 이만큼 운동했으면 그 요일을 기본으로 고른다 */
    private static final int SUGGEST_MIN_TIMES = 2;
    private static final Set<DayOfWeek> FALLBACK_DAYS =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    /** 브라우저가 주는 키의 길이 — P-256 공개키(압축하지 않은 점)와 인증 비밀 */
    private static final int P256DH_BYTES = 65;
    private static final int AUTH_BYTES = 16;

    private final NotificationSettingsRepository settingsRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final PushGateway gateway;
    private final Clock clock;

    public NotificationSettingsService(NotificationSettingsRepository settingsRepository,
                                       PushSubscriptionRepository subscriptionRepository,
                                       WorkoutSessionRepository sessionRepository,
                                       PushGateway gateway,
                                       Clock clock) {
        this.settingsRepository = settingsRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.sessionRepository = sessionRepository;
        this.gateway = gateway;
        this.clock = clock;
    }

    public SettingsResponse get(Long userId) {
        return settingsRepository.findById(userId)
                .map(settings -> toResponse(userId, settings, true))
                .orElseGet(() -> toResponse(userId, NotificationSettings.defaults(userId, suggestedDays(userId)), false));
    }

    @Transactional
    public SettingsResponse update(Long userId, UpdateRequest request) {
        Reminder reminder = request.reminder();
        Summary summary = request.summary();
        requireSelectable(reminder.time(), "리마인더");
        requireSelectable(summary.time(), "주간 요약");
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        days.addAll(reminder.days());
        if (reminder.enabled() && days.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "리마인더를 받을 요일을 하나 이상 골라 주세요.");
        }
        NotificationSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> NotificationSettings.defaults(userId, suggestedDays(userId)));
        settings.update(reminder.enabled(), days, reminder.time(), summary.enabled(), summary.day(), summary.time(),
                request.cheer(), request.group());
        return toResponse(userId, settingsRepository.save(settings), true);
    }

    /**
     * 기기(브라우저) 하나를 등록한다. 같은 푸시 주소가 이미 있으면 주인과 키만 바꾼다 — 같은 브라우저로 다른 계정에
     * 로그인한 경우다. 처음 켜는 사람이면 설정을 기본값으로 만든다.
     */
    @Transactional
    public SettingsResponse subscribe(Long userId, String endpoint, String p256dh, String auth) {
        requireHttps(endpoint);
        requireKey(p256dh, P256DH_BYTES, "p256dh");
        requireKey(auth, AUTH_BYTES, "auth");
        try {
            EcKeys.publicKey(EcKeys.fromBase64Url(p256dh));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "브라우저 공개키(p256dh)가 올바르지 않습니다.");
        }
        subscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.reassign(userId, p256dh, auth),
                () -> subscriptionRepository.save(PushSubscription.of(userId, endpoint, p256dh, auth)));
        NotificationSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> settingsRepository.save(NotificationSettings.defaults(userId, suggestedDays(userId))));
        return toResponse(userId, settings, true);
    }

    /** 내 기기만 지운다. 없으면 아무 일도 하지 않는다(로그아웃·권한 해제가 겹쳐 두 번 와도 된다) */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId().equals(userId))
                .ifPresent(subscriptionRepository::delete);
    }

    /** 최근 4주에 두 번 이상 운동한 요일. 없으면 월·수·금 */
    Set<DayOfWeek> suggestedDays(Long userId) {
        LocalDate today = LocalDate.now(clock.withZone(NotificationSender.SEOUL));
        Map<DayOfWeek, Integer> times = new EnumMap<>(DayOfWeek.class);
        for (LocalDate day : sessionRepository.findRecordedDays(userId, today.minusDays(SUGGEST_WINDOW_DAYS - 1L), today)) {
            times.merge(day.getDayOfWeek(), 1, Integer::sum);
        }
        Set<DayOfWeek> days = new HashSet<>();
        times.forEach((day, count) -> {
            if (count >= SUGGEST_MIN_TIMES) {
                days.add(day);
            }
        });
        return days.isEmpty() ? FALLBACK_DAYS : days;
    }

    private SettingsResponse toResponse(Long userId, NotificationSettings settings, boolean saved) {
        String publicKey = gateway.publicKey();
        return new SettingsResponse(publicKey != null, publicKey, subscriptionRepository.countByUserId(userId), saved,
                new Reminder(settings.isReminderEnabled(), NotificationSchedule.ordered(settings.reminderDaySet()),
                        settings.getReminderTime()),
                new Summary(settings.isSummaryEnabled(), settings.summaryDayOfWeek(), settings.getSummaryTime()),
                settings.isCheerEnabled(), settings.isGroupEnabled());
    }

    private static void requireSelectable(LocalTime time, String what) {
        if (!NotificationSchedule.selectable(time)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    what + " 시각은 08:00~21:30 사이에서 30분 단위로 골라 주세요. (밤 10시~아침 8시에는 알림을 보내지 않아요)");
        }
    }

    private static void requireHttps(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "푸시 주소가 올바르지 않습니다.");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "푸시 주소는 https여야 합니다.");
        }
    }

    private static void requireKey(String value, int bytes, String name) {
        try {
            if (EcKeys.fromBase64Url(value).length == bytes) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // 아래에서 같은 오류로
        }
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "브라우저 키(" + name + ")가 올바르지 않습니다.");
    }
}
