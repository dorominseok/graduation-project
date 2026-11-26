package com.fitness.backend.notification.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.notification.push.EcKeys;
import jakarta.persistence.EntityManager;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 알림 설정·기기 등록 API. 테스트에는 VAPID 키가 없어 pushAvailable이 false다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired EntityManager entityManager;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = signUp();
    }

    @Test
    @DisplayName("설정을 저장한 적이 없으면 기본값 — 네 종류 켜짐, 리마인더는 기록이 없으니 월·수·금 19:00, 요약은 일요일 20:00")
    void defaults() throws Exception {
        settings()
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.pushAvailable").value(false))
                .andExpect(jsonPath("$.vapidPublicKey").doesNotExist())
                .andExpect(jsonPath("$.subscriptionCount").value(0))
                .andExpect(jsonPath("$.reminder.enabled").value(true))
                .andExpect(jsonPath("$.reminder.days", contains("MONDAY", "WEDNESDAY", "FRIDAY")))
                .andExpect(jsonPath("$.reminder.time").value("19:00"))
                .andExpect(jsonPath("$.summary.day").value("SUNDAY"))
                .andExpect(jsonPath("$.summary.time").value("20:00"))
                .andExpect(jsonPath("$.cheer").value(true))
                .andExpect(jsonPath("$.group").value(true));
    }

    @Test
    @DisplayName("저장하면 그대로 돌려주고 다음 조회에도 남는다")
    void update() throws Exception {
        save(body(true, "[\"TUESDAY\",\"THURSDAY\"]", "18:30", true, "SATURDAY", "09:00", false, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.reminder.days", contains("TUESDAY", "THURSDAY")))
                .andExpect(jsonPath("$.reminder.time").value("18:30"));
        settings()
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.summary.day").value("SATURDAY"))
                .andExpect(jsonPath("$.summary.time").value("09:00"))
                .andExpect(jsonPath("$.cheer").value(false));
    }

    @Test
    @DisplayName("시각은 30분 단위만(새벽도 된다), 리마인더를 켜면 요일이 하나 이상 있어야 한다")
    void validation() throws Exception {
        save(body(true, "[\"MONDAY\"]", "03:30", true, "SUNDAY", "23:30", true, true)).andExpect(status().isOk());
        // DB까지 내려 본다 — 테스트는 한 트랜잭션이라 그냥 두면 DB 제약(V14의 08:00~21:30)에 닿지 않아 #77에서 놓쳤다
        entityManager.flush();
        save(body(true, "[\"MONDAY\"]", "19:15", true, "SUNDAY", "20:00", true, true)).andExpect(status().isBadRequest());
        save(body(true, "[]", "19:00", true, "SUNDAY", "20:00", true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        save(body(false, "[]", "19:00", true, "SUNDAY", "20:00", true, true)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("리마인더 요일에 null이 섞이면 400 — 서버 오류(500)가 아니라")
    void rejectsNullDay() throws Exception {
        save(body(true, "[\"MONDAY\",null]", "19:00", true, "SUNDAY", "20:00", true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("기기를 등록하면 설정이 기본값으로 저장되고, 같은 주소를 다시 보내도 하나다")
    void subscribe() throws Exception {
        String endpoint = endpoint();
        String body = subscription(endpoint, p256dh(), auth());
        subscribe(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionCount").value(1))
                .andExpect(jsonPath("$.saved").value(true));
        subscribe(body).andExpect(jsonPath("$.subscriptionCount").value(1));
    }

    @Test
    @DisplayName("같은 브라우저로 다른 계정이 등록하면 그 기기는 새 계정으로 옮겨 간다")
    void sameBrowserMovesToAnotherAccount() throws Exception {
        String endpoint = endpoint();
        subscribe(subscription(endpoint, p256dh(), auth())).andExpect(jsonPath("$.subscriptionCount").value(1));
        String first = bearer;
        bearer = signUp();
        subscribe(subscription(endpoint, p256dh(), auth())).andExpect(jsonPath("$.subscriptionCount").value(1));
        bearer = first;
        settings().andExpect(jsonPath("$.subscriptionCount").value(0));
    }

    @Test
    @DisplayName("https가 아닌 주소나 길이가 맞지 않는 키는 받지 않는다")
    void rejectsBadSubscription() throws Exception {
        subscribe(subscription("http://push.example.com/x", p256dh(), auth())).andExpect(status().isBadRequest());
        subscribe(subscription(endpoint(), "AAAA", auth())).andExpect(status().isBadRequest());
        subscribe(subscription(endpoint(), p256dh(), "AAAA")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("길이는 맞아도 P-256 곡선 위의 점이 아닌 공개키는 받지 않는다 — 보낼 때마다 암호화가 실패하게 된다")
    void rejectsKeyOffTheCurve() throws Exception {
        subscribe(subscription(endpoint(), offCurveKey(), auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        settings().andExpect(jsonPath("$.subscriptionCount").value(0));
    }

    @Test
    @DisplayName("브라우저 회사의 푸시 서버가 아닌 주소는 받지 않는다 — 서버가 아무 주소로나 요청을 보내게 되지 않도록")
    void rejectsUnknownPushHost() throws Exception {
        for (String endpoint : new String[] {
                "https://127.0.0.1:9/hook",
                "https://10.0.0.5/internal",
                "https://169.254.169.254/latest/meta-data/",
                "https://postgres:5432/",
                "https://attacker.example/collect",
                "https://fcm.googleapis.com.attacker.example/fcm/send/x",
                "https://user:pw@fcm.googleapis.com/fcm/send/x",
                "https://fcm.googleapis.com:8443/fcm/send/x",
        }) {
            subscribe(subscription(endpoint, p256dh(), auth()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        settings().andExpect(jsonPath("$.subscriptionCount").value(0));
    }

    @Test
    @DisplayName("크롬·파이어폭스·사파리·엣지의 푸시 주소는 받는다")
    void acceptsBrowserPushHosts() throws Exception {
        String[] endpoints = {
                "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(),
                "https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID(),
                "https://web.push.apple.com/" + UUID.randomUUID(),
                "https://wns2-pn1p.notify.windows.com/w/?token=" + UUID.randomUUID(),
        };
        for (String endpoint : endpoints) {
            subscribe(subscription(endpoint, p256dh(), auth())).andExpect(status().isOk());
        }
        settings().andExpect(jsonPath("$.subscriptionCount").value(endpoints.length));
    }

    @Test
    @DisplayName("한 계정에 기기는 10개까지 — 이미 등록한 기기를 다시 보내는 것은 된다")
    void limitsDevicesPerAccount() throws Exception {
        String first = endpoint();
        subscribe(subscription(first, p256dh(), auth())).andExpect(status().isOk());
        for (int i = 0; i < 9; i++) {
            subscribe(subscription(endpoint(), p256dh(), auth())).andExpect(status().isOk());
        }
        subscribe(subscription(endpoint(), p256dh(), auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        subscribe(subscription(first, p256dh(), auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionCount").value(10));
    }

    @Test
    @DisplayName("등록 해제는 내 기기만 지운다")
    void unsubscribe() throws Exception {
        String endpoint = endpoint();
        subscribe(subscription(endpoint, p256dh(), auth()));
        mvc.perform(delete("/api/v1/users/me/push-subscriptions").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                .andExpect(status().isNoContent());
        settings().andExpect(jsonPath("$.subscriptionCount").value(0));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/v1/users/me/notification-settings")).andExpect(status().isUnauthorized());
    }

    private ResultActions settings() throws Exception {
        return mvc.perform(get("/api/v1/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/v1/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions subscribe(String body) throws Exception {
        return mvc.perform(post("/api/v1/users/me/push-subscriptions").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(boolean reminderOn, String days, String reminderTime, boolean summaryOn, String summaryDay,
                               String summaryTime, boolean cheer, boolean group) {
        return """
                {"reminder":{"enabled":%s,"days":%s,"time":"%s"},
                 "summary":{"enabled":%s,"day":"%s","time":"%s"},
                 "cheer":%s,"group":%s}
                """.formatted(reminderOn, days, reminderTime, summaryOn, summaryDay, summaryTime, cheer, group);
    }

    private static String subscription(String endpoint, String p256dh, String auth) {
        return "{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint, p256dh, auth);
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private static String p256dh() {
        KeyPair pair = EcKeys.generate();
        return EcKeys.toBase64Url(EcKeys.encode((ECPublicKey) pair.getPublic()));
    }

    /** 0x04 ‖ x=1 ‖ y=1 — 65바이트지만 y² ≠ x³ − 3x + b 라 곡선 위에 없다 */
    private static String offCurveKey() {
        byte[] point = new byte[65];
        point[0] = 0x04;
        point[32] = 1;
        point[64] = 1;
        return EcKeys.toBase64Url(point);
    }

    private static String auth() {
        return EcKeys.toBase64Url(UUID.randomUUID().toString().substring(0, 16).getBytes());
    }

    private String signUp() {
        String email = "n" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }
}
