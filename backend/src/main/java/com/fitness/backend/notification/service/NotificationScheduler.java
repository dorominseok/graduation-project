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
 *   리마인더  고른 요일·시각 · 그날 기록이 없을 때 · 제목 "오늘은 운동하는 날이에요"만, 본문 없음(2026-11-28 — 전엔 오늘 루틴의 이유 문장)
 *   주간 요약 고른 요일·시각 · 이번 주(월요일~그날) 운동한 날 수와 더 채울 부위(부족·권장 이하에서 최대 둘)
 *            — 지금은 멈춤({@link #SUMMARY_PAUSED}, 결정 기록 D-20). 화면에서도 숨겼다
 * </pre>
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final int MAX_LACKING = 2;

    /**
     * 주간 요약을 보내지 않는다(2026-11-28 사용자 요청, 결정 기록 D-20) — 아직 제대로 확인하지 못해 사용자에게 보이지 않게.
     * 설정 화면에서도 숨겨 끌 수 없으니, 켜 둔 사람에게도 가지 않게 여기서 막는다. 다시 켤 때는 이 값과 화면만 바꾼다.
     */
    public static final boolean SUMMARY_PAUSED = true;

    private final NotificationSettingsRepository settingsRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final AnalysisService analysisService;
    private final NotificationSender sender;
    private final PushProperties pushProperties;
    private final Clock clock;

    public NotificationScheduler(NotificationSettingsRepository settingsRepository,
                                 WorkoutSessionRepository sessionRepository,
                                 AnalysisService analysisService,
                                 NotificationSender sender,
                                 PushProperties pushProperties,
                                 Clock clock) {
        this.settingsRepository = settingsRepository;
        this.sessionRepository = sessionRepository;
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
        if (SUMMARY_PAUSED) {
            return;
        }
        for (NotificationSettings settings : settingsRepository.summariesAt((short) day.getValue(), slot)) {
            safely(settings.getUserId(), "주간 요약", () -> summarize(settings.getUserId(), date));
        }
    }

    /**
     * 고른 요일·시각에 "오늘은 운동하는 날"이라는 것만 알린다(2026-11-28 사용자 요청, D-20). 오늘 루틴의 이유 문장을 담던 것은
     * 뺐다 — 루틴은 홈에서 보면 되고, 알림은 최소한으로. 그날 이미 운동했으면 보내지 않는다.
     */
    private void remind(Long userId, LocalDate date) {
        if (sessionRepository.countRecordedDays(userId, date, date) > 0) {
            return;   // 오늘 이미 운동했다
        }
        // 본문은 빈 글자 — null이면 예전 서비스워커가 "null"이라고 띄운다
        sender.send(userId, NotificationType.REMINDER, new Message("오늘은 운동하는 날이에요", "", "/", "reminder"));
    }

    private void summarize(Long userId, LocalDate date) {
        sender.send(userId, NotificationType.SUMMARY, new Message("이번 주 운동", summary(userId, date), "/analysis", "summary"));
    }

    /** 주간 요약 본문. 더 채울 부위는 부족 → 권장 이하, 같으면 주당 세트가 적은 순 */
    public String summary(Long userId, LocalDate date) {
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
