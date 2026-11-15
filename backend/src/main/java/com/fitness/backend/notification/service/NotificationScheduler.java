package com.fitness.backend.notification.service;

import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.analysis.service.AnalysisService;
import com.fitness.backend.analysis.web.AnalysisDtos.ChildResponse;
import com.fitness.backend.notification.domain.NotificationSchedule;
import com.fitness.backend.notification.domain.NotificationSettings;
import com.fitness.backend.notification.domain.NotificationType;
import com.fitness.backend.notification.push.PushProperties;
import com.fitness.backend.notification.repository.NotificationSettingsRepository;
import com.fitness.backend.notification.service.NotificationSender.Message;
import com.fitness.backend.routine.service.RoutineService;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 알림 — 운동 리마인더와 주간 요약(「알림_설계서」 2·3장). 30분마다(정각·30분) 돌며 지금 칸이 자기 시각인
 * 사람에게 보낸다. 서버는 한 대라 같은 칸을 두 번 보낼 일이 없고, 서버가 꺼져 있던 칸은 나중에 몰아 보내지 않는다.
 *
 * <pre>
 *   리마인더  고른 요일·시각 · 그날 기록이 없을 때 · 문구는 루틴을 만드는 규칙의 "왜 오늘 이 부위인지" ·
 *            그날 루틴이 비면(모두 회복 중 등) 보내지 않는다
 *   주간 요약 고른 요일·시각 · 이번 주(월요일~그날) 운동한 날 수와 더 채울 부위(부족·권장 이하에서 최대 둘)
 * </pre>
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final int MAX_LACKING = 2;

    private final NotificationSettingsRepository settingsRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final RoutineService routineService;
    private final AnalysisService analysisService;
    private final NotificationSender sender;
    private final PushProperties pushProperties;
    private final Clock clock;

    public NotificationScheduler(NotificationSettingsRepository settingsRepository,
                                 WorkoutSessionRepository sessionRepository,
                                 RoutineService routineService,
                                 AnalysisService analysisService,
                                 NotificationSender sender,
                                 PushProperties pushProperties,
                                 Clock clock) {
        this.settingsRepository = settingsRepository;
        this.sessionRepository = sessionRepository;
        this.routineService = routineService;
        this.analysisService = analysisService;
        this.sender = sender;
        this.pushProperties = pushProperties;
        this.clock = clock;
    }

    /** VAPID 키가 없으면(로컬·테스트) 돌지 않는다 — 보낼 수 없는데 루틴 계산만 하게 된다 */
    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Seoul")
    public void tick() {
        if (pushProperties.enabled()) {
            runAt(LocalDateTime.now(clock.withZone(NotificationSender.SEOUL)));
        }
    }

    /** 한 칸을 돈다. 한 사람에게서 실패해도 다음 사람은 보낸다 */
    public void runAt(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        DayOfWeek day = date.getDayOfWeek();
        LocalTime slot = NotificationSchedule.slot(now.toLocalTime());
        for (NotificationSettings settings : settingsRepository.remindersAt(slot)) {
            if (NotificationSchedule.includes(settings.getReminderDays(), day)) {
                safely(settings.getUserId(), "리마인더", () -> remind(settings.getUserId(), date));
            }
        }
        for (NotificationSettings settings : settingsRepository.summariesAt((short) day.getValue(), slot)) {
            safely(settings.getUserId(), "주간 요약", () -> summarize(settings.getUserId(), date));
        }
    }

    private void remind(Long userId, LocalDate date) {
        if (sessionRepository.countRecordedDays(userId, date, date) > 0) {
            return;   // 오늘 이미 운동했다
        }
        routineService.reminderReason(userId, date).ifPresent(reason ->
                sender.send(userId, NotificationType.REMINDER, new Message("오늘 운동할 날이에요", reason, "/", "reminder")));
    }

    private void summarize(Long userId, LocalDate date) {
        sender.send(userId, NotificationType.SUMMARY, new Message("이번 주 운동", summary(userId, date), "/analysis", "summary"));
    }

    /** 주간 요약 본문. 더 채울 부위는 부족 → 권장 이하, 같으면 주당 세트가 적은 순 */
    String summary(Long userId, LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int days = sessionRepository.countRecordedDays(userId, monday, date);
        if (days == 0) {
            return "이번 주는 운동 기록이 없어요. 다음 주에 다시 시작해 봐요.";
        }
        List<String> lacking = analysisService.muscleVolume(userId, null, date).tiers().stream()
                .flatMap(tier -> tier.children().stream())
                .filter(child -> child.verdict() == VolumeVerdict.INSUFFICIENT
                        || child.verdict() == VolumeVerdict.BELOW_RECOMMENDED)
                .sorted(Comparator.comparing((ChildResponse child) -> child.verdict().ordinal())
                        .thenComparing(ChildResponse::weeklySets))
                .limit(MAX_LACKING)
                .map(ChildResponse::label)
                .toList();
        String done = "이번 주 %d일 운동했어요.".formatted(days);
        return lacking.isEmpty()
                ? done + " 모든 부위를 충분히 채웠어요."
                : done + " 더 채우면 좋은 부위: " + String.join(", ", lacking);
    }

    private static void safely(Long userId, String what, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException e) {
            log.warn("{} 알림을 만들지 못했다: user={} — {}", what, userId, e.toString());
        }
    }
}
