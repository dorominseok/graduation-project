package com.fitness.backend.workout.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GTG(틈틈이 하는 가벼운 세트, V24, 결정 기록 D-23). 회차 0이 그날의 GTG다 — 카드는 맨 뒤에 오고, 운동 시간·부위별 볼륨·
 * 회복·1RM·직전 기록·운동한 날 수에 넣지 않는다. 세트 수와 기록 목록에는 보인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class WorkoutGtgApiTest {

    private static final int GTG = 0;

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String me;
    private long bench;
    private long pullUp;

    @BeforeEach
    void setUp() {
        me = signUp();
        bench = builtinId("바벨 벤치프레스");
        pullUp = builtinId("풀업");
    }

    @Test
    @DisplayName("GTG 카드는 맨 뒤, 운동 시간·시작 시각에 넣지 않고 회차 시각·시간도 없다. 세트 수에는 들어간다")
    void gtgLastAndOutOfDuration() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, pullUp, 0, GTG);
        addSet(session, bench, 60, 1);
        addSet(session, bench, 60, 1);
        // GTG 세트는 다섯 시간 전 — 넣으면 그날 시작 시각이 이 시각이 된다
        OffsetDateTime early = OffsetDateTime.now(clock).minusHours(5);
        jdbc.update("update workout_sets set recorded_at = ? where session_id = ? and round_no = 0", early, session);

        String body = session(session);
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[*].round")).containsExactly(1, GTG);
        assertThat((List<Integer>) JsonPath.read(body, "$.rounds[*].round")).containsExactly(1, GTG);
        assertThat((Object) JsonPath.read(body, "$.rounds[1].startedAt")).isNull();
        assertThat((Object) JsonPath.read(body, "$.rounds[1].durationSec")).isNull();
        String startedAt = JsonPath.read(body, "$.startedAt");
        assertThat(startedAt).isEqualTo(JsonPath.read(body, "$.rounds[0].startedAt"));
        assertThat(OffsetDateTime.parse(startedAt)).isAfter(early.plusHours(1));

        String history = mvc.perform(get("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((Integer) JsonPath.read(history, "$.content[0].setCount")).isEqualTo(3);
    }

    @Test
    @DisplayName("GTG만 한 날만 있으면 운동 시간이 없다")
    void gtgOnlyDayHasNoDuration() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, pullUp, 0, GTG);
        addSet(session, pullUp, 0, GTG);

        String body = session(session);
        assertThat((Object) JsonPath.read(body, "$.startedAt")).isNull();
        assertThat((Object) JsonPath.read(body, "$.durationSec")).isNull();
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[0].sets[*].setNo")).containsExactly(1, 2);
    }

    @Test
    @DisplayName("부위별 볼륨·균형·회복·1RM 추이는 GTG 세트와 GTG만 한 날을 세지 않는다")
    void analysisIgnoresGtg() throws Exception {
        long day1 = openDay(LocalDate.now(clock).minusDays(2));
        addSet(day1, bench, 60, 1);
        addSet(day1, bench, 60, 1);

        String volume = read("/api/v1/analysis/muscle-volume");
        String balance = read("/api/v1/analysis/balance");
        String recovery = read("/api/v1/analysis/recovery");
        String trend = read("/api/v1/stats/one-rm-trend?exerciseId=" + bench);

        // 같은 날의 GTG 세트, 그리고 GTG만 한 다른 날(더 무거운 무게로)
        addSet(day1, bench, 40, GTG);
        addSet(day1, bench, 40, GTG);
        long day2 = openDay(LocalDate.now(clock).minusDays(1));
        addSet(day2, bench, 100, GTG);

        assertThat(read("/api/v1/analysis/muscle-volume")).isEqualTo(volume);
        assertThat(read("/api/v1/analysis/balance")).isEqualTo(balance);
        assertThat(read("/api/v1/analysis/recovery")).isEqualTo(recovery);
        assertThat(read("/api/v1/stats/one-rm-trend?exerciseId=" + bench)).isEqualTo(trend);
    }

    @Test
    @DisplayName("그날 강도(카드의 1RM)는 GTG 세트를 넣지 않는다")
    void intensityIgnoresGtg() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, bench, 100, GTG);
        addSet(session, bench, 60, 1);

        mvc.perform(get("/api/v1/stats/session-intensity/" + session).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].sets.length()").value(1));
    }

    @Test
    @DisplayName("직전 기록은 GTG를 빼고, gtg=true면 지난 GTG 세트")
    void lastPerformanceSeparatesGtg() throws Exception {
        long older = openDay(LocalDate.now(clock).minusDays(3));
        addSet(older, bench, 60, 1);
        long newer = openDay(LocalDate.now(clock).minusDays(2));
        addSet(newer, bench, 30, GTG);
        addSet(newer, bench, 30, GTG);

        mvc.perform(get("/api/v1/exercises/" + bench + "/last-performance").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(older))
                .andExpect(jsonPath("$.sets.length()").value(1))
                .andExpect(jsonPath("$.sets[0].weightKg").value(60));

        mvc.perform(get("/api/v1/exercises/" + bench + "/last-performance?gtg=true").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(newer))
                .andExpect(jsonPath("$.sets.length()").value(2))
                .andExpect(jsonPath("$.sets[0].weightKg").value(30));

        mvc.perform(get("/api/v1/exercises/" + pullUp + "/last-performance?gtg=true").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GTG 카드는 ?round=0으로 지운다 — 일반 회차는 그대로")
    void deleteGtgCard() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, bench, 60, 1);
        addSet(session, bench, 30, GTG);

        mvc.perform(delete("/api/v1/workout-sessions/" + session + "/exercises/" + bench + "?round=0")
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        assertThat((List<Integer>) JsonPath.read(session(session), "$.exercises[*].round")).containsExactly(1);
    }

    @Test
    @DisplayName("GTG만 있는 날에 다른 앱 기록을 가져오면 1회차로 — GTG는 회차 번호를 세지 않는다")
    void importAfterGtgGoesToRoundOne() throws Exception {
        LocalDate day = LocalDate.now(clock).minusDays(4);
        long session = openDay(day);
        addSet(session, pullUp, 0, GTG);
        mvc.perform(post("/api/v1/imports").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[{\"date\":\"" + day + "\",\"minutes\":30,\"exercises\":[{\"source\":\"벤치\","
                                + "\"exerciseId\":" + bench + ",\"remember\":false,\"sets\":[{\"kind\":\"SET\","
                                + "\"weightKg\":50,\"reps\":10}]}]}]}"))
                .andExpect(status().isCreated());
        assertThat((List<Integer>) JsonPath.read(session(session), "$.exercises[*].round")).containsExactly(1, GTG);
    }

    // ── 도우미

    private String signUp() {
        String email = "gtg" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private long builtinId(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = ?", Long.class, name);
    }

    private long openDay(LocalDate day) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addSet(long session, long exerciseId, int weight, int round) throws Exception {
        String weightJson = exerciseId == pullUp ? "" : ",\"weightKg\":" + weight;
        mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                                + weightJson + ",\"reps\":5,\"round\":" + round + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(round));
    }

    private String read(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String session(long session) throws Exception {
        return mvc.perform(get("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
