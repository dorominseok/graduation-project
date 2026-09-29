package com.fitness.backend.stats.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * 통계 API — 추정 1RM 추이·세션 강도. 명세 7.1·7.2.
 *
 * <p>기대값은 명세의 검산 예시를 그대로 쓴다. {@code 70kg × 10}의 Epley 추정이
 * {@code 93.3}이고 {@code 60kg}이 그 64%라는 숫자는 명세 7.2에 적혀 있으므로,
 * 여기서 다른 값이 나오면 코드와 문서 중 한쪽이 틀린 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;
    private long benchPress;
    private long plank;

    @BeforeEach
    void setUp() throws Exception {
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
        benchPress = exerciseIdOf("바벨 벤치프레스");
        plank = exerciseIdOf("플랭크");
    }

    // ── 추정 1RM 추이 (7.1)

    @Test
    @DisplayName("하루에 여러 세트를 해도 점은 하나다 — 그날 추정값 중 최댓값")
    void oneDayYieldsSinglePoint() throws Exception {
        LocalDate day = LocalDate.now().minusDays(3);
        long session = backfillSession(day);
        postSet(session, weightSet(benchPress, "60.0", 12)).andExpect(status().isCreated());
        postSet(session, weightSet(benchPress, "80.0", 5)).andExpect(status().isCreated());
        complete(session);

        trend(benchPress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].date").value(day.toString()))
                // 80 × (1 + 5/30) = 93.3 이 60 × 1.4 = 84.0 보다 크다
                .andExpect(jsonPath("$.points[0].estimatedOneRm").value(93.3))
                .andExpect(jsonPath("$.points[0].basedOnSet.weightKg").value(80.0))
                .andExpect(jsonPath("$.points[0].basedOnSet.reps").value(5));
    }

    @Test
    @DisplayName("기록이 없는 날은 점을 만들지 않는다 — 선을 끊지 않고 이어 그리라는 뜻")
    void skipsDaysWithoutRecords() throws Exception {
        LocalDate older = LocalDate.now().minusDays(10);
        LocalDate newer = LocalDate.now().minusDays(2);
        doneSession(older, benchPress, "70.0", 10, 1);
        doneSession(newer, benchPress, "75.0", 10, 1);

        trend(benchPress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                // 사이의 7일은 비어 있지만 점으로도 0으로도 내려가지 않는다
                .andExpect(jsonPath("$.points[0].date").value(older.toString()))
                .andExpect(jsonPath("$.points[1].date").value(newer.toString()));
    }

    @Test
    @DisplayName("13회 이상 세트는 추정에서 뺀다 — 고반복 구간은 Epley 오차가 커진다")
    void excludesHighRepSets() throws Exception {
        doneSession(LocalDate.now().minusDays(1), benchPress, "50.0", 13, 1);

        trend(benchPress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    @DisplayName("종료하지 않은 기록은 추이에 들어가지 않는다")
    void excludesDraftSessions() throws Exception {
        long session = backfillSession(LocalDate.now().minusDays(1));
        postSet(session, weightSet(benchPress, "100.0", 5)).andExpect(status().isCreated());
        // 종료하지 않는다 — DRAFT 상태로 둔다

        trend(benchPress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    @DisplayName("중량을 쓰지 않는 종목은 1RM을 추정하지 않는다 — 0.0kg 그래프 대신 400")
    void rejectsNonWeightExercise() throws Exception {
        trend(plank)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 400")
    void rejectsInvertedRange() throws Exception {
        mvc.perform(get("/api/v1/stats/one-rm-trend")
                        .param("exerciseId", String.valueOf(benchPress))
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().minusDays(7).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    // ── 세션 강도 (7.2)

    @Test
    @DisplayName("강도는 그날 추정 1RM 대비 비율이다 — 명세 7.2의 검산 예시 그대로")
    void intensityMatchesSpecExample() throws Exception {
        long session = backfillSession(LocalDate.now().minusDays(1));
        postSet(session, warmupSet(benchPress, "60.0", 12)).andExpect(status().isCreated());
        postSet(session, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());
        postSet(session, weightSet(benchPress, "70.0", 9)).andExpect(status().isCreated());
        complete(session);

        intensity(session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].measureType").value("WEIGHT_REPS"))
                // 60×12 → 84.0, 70×10 → 93.3, 70×9 → 91.0 중 최댓값
                .andExpect(jsonPath("$.exercises[0].estimatedOneRm").value(93.3))
                // 워밍업도 강도를 표시한다 — 볼륨 집계에서만 빠진다
                .andExpect(jsonPath("$.exercises[0].sets[0].isWarmup").value(true))
                .andExpect(jsonPath("$.exercises[0].sets[0].intensityPct").value(64))
                .andExpect(jsonPath("$.exercises[0].sets[1].intensityPct").value(75))
                .andExpect(jsonPath("$.exercises[0].sets[2].intensityPct").value(75));
    }

    @Test
    @DisplayName("시간 종목은 추정 1RM도 강도도 없다")
    void timeExerciseHasNoIntensity() throws Exception {
        long session = backfillSession(LocalDate.now().minusDays(1));
        postSet(session, timeSet(plank, 60)).andExpect(status().isCreated());
        complete(session);

        intensity(session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].measureType").value("TIME"))
                .andExpect(jsonPath("$.exercises[0].estimatedOneRm").isEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].durationSec").value(60))
                .andExpect(jsonPath("$.exercises[0].sets[0].intensityPct").isEmpty());
    }

    @Test
    @DisplayName("남의 세션은 404 — 존재 여부조차 드러내지 않는다")
    void otherUsersSessionIsNotFound() throws Exception {
        long session = backfillSession(LocalDate.now().minusDays(1));
        postSet(session, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());
        complete(session);

        String other = "o" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String otherBearer = "Bearer "
                + authService.signUp(other, "hunter2hunter2", "지훈").tokens().accessToken();

        mvc.perform(get("/api/v1/stats/session-intensity/" + session)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 없이는 통계를 볼 수 없다")
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/stats/one-rm-trend").param("exerciseId", String.valueOf(benchPress)))
                .andExpect(status().isUnauthorized());
    }

    // ── 도우미

    private ResultActions trend(long exerciseId) throws Exception {
        return mvc.perform(get("/api/v1/stats/one-rm-trend")
                .param("exerciseId", String.valueOf(exerciseId))
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions intensity(long sessionId) throws Exception {
        return mvc.perform(get("/api/v1/stats/session-intensity/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private void doneSession(LocalDate day, long exerciseId, String weightKg, int reps, int sets)
            throws Exception {
        long session = backfillSession(day);
        for (int i = 0; i < sets; i++) {
            postSet(session, weightSet(exerciseId, weightKg, reps)).andExpect(status().isCreated());
        }
        complete(session);
    }

    /** {@code BACKFILL}이라야 과거 날짜로 만들 수 있고, 하루에 여러 개도 된다. */
    private long backfillSession(LocalDate performedOn) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + performedOn + "\",\"source\":\"BACKFILL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return firstId(body);
    }

    private void complete(long sessionId) throws Exception {
        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private ResultActions postSet(long sessionId, String body) throws Exception {
        return mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/sets")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String weightSet(long exerciseId, String weightKg, int reps) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weightKg + ",\"reps\":" + reps + "}";
    }

    private String warmupSet(long exerciseId, String weightKg, int reps) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weightKg + ",\"reps\":" + reps + ",\"isWarmup\":true}";
    }

    private String timeSet(long exerciseId, int durationSec) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"durationSec\":" + durationSec + "}";
    }

    private long exerciseIdOf(String nameKo) throws Exception {
        String body = mvc.perform(get("/api/v1/exercises").param("q", nameKo).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"id\":([0-9]+),\"nameKo\":\"" + Pattern.quote(nameKo) + "\"")
                .matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException(nameKo + " 종목이 시드에 없다");
        }
        return Long.parseLong(matcher.group(1));
    }

    private static long firstId(String json) {
        Matcher matcher = Pattern.compile("\"id\":([0-9]+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("id를 찾을 수 없다: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}
