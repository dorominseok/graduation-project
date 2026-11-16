package com.fitness.backend.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.group.service.GroupService;
import com.fitness.backend.group.web.GroupDtos.GroupDetailResponse;
import com.fitness.backend.notification.push.EcKeys;
import com.fitness.backend.notification.push.PushGateway;
import com.fitness.backend.notification.repository.PushSubscriptionRepository;
import com.fitness.backend.notification.service.NotificationScheduler;
import com.fitness.backend.notification.service.NotificationSettingsService;
import com.fitness.backend.notification.web.NotificationDtos.Reminder;
import com.fitness.backend.notification.web.NotificationDtos.Summary;
import com.fitness.backend.notification.web.NotificationDtos.UpdateRequest;
import com.fitness.backend.routine.service.RoutineService;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayRequest;
import java.math.BigDecimal;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 알림이 실제로 나가는지 — 예약 작업(리마인더·주간 요약)과 그룹 일(응원·참여·방장 위임).
 *
 * <p>시계는 2026-11-16(월) 낮 12시로 고정하고, 보내는 대신 모으는 발송기를 쓴다. 그룹 알림은 커밋된 뒤에 나가므로
 * 이 테스트는 트랜잭션으로 감싸지 않는다(사용자는 매번 새로 만들어 서로 겹치지 않는다). 비동기 알림은 테스트에서는
 * 같은 스레드로 돌린다.
 */
@SpringBootTest
class NotificationDeliveryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        RecordingPushGateway recordingPushGateway() {
            return new RecordingPushGateway();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ZonedDateTime.of(MONDAY, LocalTime.NOON, SEOUL).toInstant(), SEOUL);
        }

        @Bean(name = "taskExecutor")
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired RecordingPushGateway gateway;
    @Autowired NotificationScheduler scheduler;
    @Autowired NotificationSettingsService settings;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired RoutineService routineService;
    @Autowired GroupService groupService;
    @Autowired WorkoutService workoutService;
    @Autowired AuthService authService;
    @Autowired ExerciseRepository exerciseRepository;

    @BeforeEach
    void clear() {
        gateway.clear();
    }

    // ── 운동 리마인더

    @Test
    @DisplayName("고른 요일·시각에 그날 기록이 없으면 리마인더 — 문구는 루틴을 만드는 규칙의 이유 문장")
    void reminderOnChosenDayAndTime() {
        Long user = signUp("민석");
        String device = subscribe(user);
        choose(user, true, List.of(DayOfWeek.MONDAY), LocalTime.of(19, 0), false);

        scheduler.runAt(MONDAY.atTime(19, 0));
        List<RecordingPushGateway.Sent> sent = gateway.to(device);
        assertEquals(1, sent.size());
        String reason = routineService.reminderReason(user, MONDAY).orElseThrow();
        assertTrue(sent.get(0).payload().contains("\"title\":\"오늘 운동할 날이에요\""), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("\"body\":\"" + reason + "\""), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("\"url\":\"/\""));

        scheduler.runAt(MONDAY.atTime(19, 30));
        scheduler.runAt(MONDAY.plusDays(1).atTime(19, 0));
        assertEquals(1, gateway.to(device).size(), "다른 시각·고르지 않은 요일에는 보내지 않는다");
    }

    @Test
    @DisplayName("그날 이미 운동했으면 리마인더를 보내지 않는다")
    void noReminderAfterTraining() {
        Long user = signUp("민석");
        String device = subscribe(user);
        choose(user, true, List.of(DayOfWeek.MONDAY), LocalTime.of(19, 0), false);
        sets(user, MONDAY, 3);

        scheduler.runAt(MONDAY.atTime(19, 0));
        assertEquals(0, gateway.to(device).size());
    }

    @Test
    @DisplayName("리마인더를 끄면 보내지 않는다")
    void reminderOff() {
        Long user = signUp("민석");
        String device = subscribe(user);
        choose(user, false, List.of(DayOfWeek.MONDAY), LocalTime.of(19, 0), false);

        scheduler.runAt(MONDAY.atTime(19, 0));
        assertEquals(0, gateway.to(device).size());
    }

    // ── 주간 요약

    @Test
    @DisplayName("주간 요약 — 이번 주 운동한 날 수와 더 채울 부위. 기록이 없는 주는 다른 문구")
    void weeklySummary() {
        Long trained = signUp("민석");
        String trainedDevice = subscribe(trained);
        chooseSummary(trained, DayOfWeek.MONDAY, LocalTime.of(20, 0));
        sets(trained, MONDAY, 3);

        Long idle = signUp("지수");
        String idleDevice = subscribe(idle);
        chooseSummary(idle, DayOfWeek.MONDAY, LocalTime.of(20, 0));

        scheduler.runAt(MONDAY.atTime(20, 0));
        String payload = gateway.to(trainedDevice).get(0).payload();
        assertTrue(payload.contains("\"title\":\"이번 주 운동\""), payload);
        assertTrue(payload.contains("이번 주 1일 운동했어요. 더 채우면 좋은 부위: "), payload);
        assertTrue(payload.contains("\"url\":\"/analysis\""), payload);
        assertTrue(gateway.to(idleDevice).get(0).payload().contains("이번 주는 운동 기록이 없어요."));
    }

    @Test
    @DisplayName("푸시 서버가 구독이 없다고 하면 그 기기를 지운다")
    void goneDeviceIsRemoved() {
        Long user = signUp("민석");
        String device = subscribe(user);
        choose(user, true, List.of(DayOfWeek.MONDAY), LocalTime.of(19, 0), false);
        gateway.goneFor(device);

        scheduler.runAt(MONDAY.atTime(19, 0));
        assertTrue(subscriptions.findByEndpoint(device).isEmpty());
    }

    // ── 그룹 소식 · 응원

    @Test
    @DisplayName("새 멤버는 방장에게, 응원은 받은 사람에게(다시 눌러도 한 번), 방장이 나가면 새 방장에게")
    void groupEvents() {
        Long owner = signUp("방장");
        Long member = signUp("지수");
        String ownerDevice = subscribe(owner);
        String memberDevice = subscribe(member);

        GroupDetailResponse group = groupService.create(owner, "새벽 헬스");
        groupService.join(member, group.inviteCode());
        assertEquals(1, gateway.to(ownerDevice).size());
        String joined = gateway.to(ownerDevice).get(0).payload();
        assertTrue(joined.contains("\"title\":\"새벽 헬스\""), joined);
        assertTrue(joined.contains("지수님이 그룹에 들어왔어요."), joined);
        assertTrue(joined.contains("\"url\":\"/group/" + group.id() + "\""), joined);

        Long session = sets(member, MONDAY, 1);
        groupService.cheer(owner, group.id(), session);
        groupService.cheer(owner, group.id(), session);
        List<RecordingPushGateway.Sent> cheers = gateway.to(memberDevice);
        assertEquals(1, cheers.size(), "같은 기록에 다시 눌러도 알림은 한 번");
        assertTrue(cheers.get(0).payload().contains("방장님이 11월 16일 운동에 응원했어요."), cheers.get(0).payload());

        groupService.leave(owner, group.id());
        List<RecordingPushGateway.Sent> toMember = gateway.to(memberDevice);
        assertEquals(2, toMember.size());
        assertTrue(toMember.get(1).payload().contains("이제 이 그룹의 방장이에요."), toMember.get(1).payload());
    }

    // ── 도우미

    private Long signUp(String nickname) {
        String email = "d" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return authService.signUp(email, "hunter2hunter2", nickname).user().getId();
    }

    /** 기기 하나를 등록하고 그 푸시 주소를 돌려준다 */
    private String subscribe(Long user) {
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        String p256dh = EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) EcKeys.generate().getPublic()));
        String auth = EcKeys.toBase64Url(UUID.randomUUID().toString().substring(0, 16).getBytes());
        settings.subscribe(user, endpoint, p256dh, auth);
        return endpoint;
    }

    private void choose(Long user, boolean reminderOn, List<DayOfWeek> days, LocalTime time, boolean summaryOn) {
        settings.update(user, new UpdateRequest(new Reminder(reminderOn, days, time),
                new Summary(summaryOn, DayOfWeek.SUNDAY, LocalTime.of(20, 0)), true, true));
    }

    private void chooseSummary(Long user, DayOfWeek day, LocalTime time) {
        settings.update(user, new UpdateRequest(new Reminder(false, List.of(DayOfWeek.MONDAY), LocalTime.of(19, 0)),
                new Summary(true, day, time), true, true));
    }

    /** 그날 기록을 열고 벤치프레스를 {@code count}세트 넣는다. 기록 id를 돌려준다 */
    private Long sets(Long user, LocalDate day, int count) {
        Long session = workoutService.openDay(user, new OpenDayRequest(day)).session().id();
        long bench = exerciseRepository.findAll().stream()
                .filter(e -> e.getNameKo().equals("바벨 벤치프레스")).findFirst().orElseThrow().getId();
        for (int i = 0; i < count; i++) {
            workoutService.addSet(user, session, new CreateSetRequest(UUID.randomUUID(), bench, BigDecimal.valueOf(40), 10,
                    null, false));
        }
        return session;
    }

    /** 발송기를 바꿔 끼웠는지 — 테스트가 진짜로 보내지 않게 */
    @Test
    @DisplayName("테스트는 보내지 않고 모으는 발송기를 쓴다")
    void usesRecordingGateway(@Autowired PushGateway pushGateway) {
        assertTrue(pushGateway instanceof RecordingPushGateway);
    }
}
