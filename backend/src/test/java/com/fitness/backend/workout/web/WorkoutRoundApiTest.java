package com.fitness.backend.workout.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
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
 * 하루 기록 안의 회차(V23, 결정 기록 D-22). 기록은 날짜마다 하나이고, 같은 종목도 회차가 다르면 카드가 따로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class WorkoutRoundApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String me;
    private long bench;
    private long squat;

    @BeforeEach
    void setUp() {
        me = signUp();
        bench = builtinId("바벨 벤치프레스");
        squat = builtinId("바벨 스쿼트");
    }

    @Test
    @DisplayName("같은 종목을 1·2회차에 하면 카드가 둘이고 세트 번호도 따로 센다. 회차마다 시각·시간이 온다")
    void sameExerciseTwoRounds() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, bench, 60, null);
        addSet(session, bench, 60, null);
        addSet(session, squat, 80, 1);
        addSet(session, bench, 40, 2);

        String body = session(session);
        assertThat((List<String>) JsonPath.read(body, "$.exercises[*].exerciseName"))
                .containsExactly("바벨 벤치프레스", "바벨 스쿼트", "바벨 벤치프레스");
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[*].round")).containsExactly(1, 1, 2);
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[0].sets[*].setNo")).containsExactly(1, 2);
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[2].sets[*].setNo")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(body, "$.rounds[*].round")).containsExactly(1, 2);
        assertThat((List<Object>) JsonPath.read(body, "$.rounds[*].startedAt")).doesNotContainNull();

        // 히스토리 목록의 종목은 한 번씩
        String history = mvc.perform(get("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((Integer) JsonPath.read(history, "$.content[0].exerciseCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(history, "$.content[0].setCount")).isEqualTo(4);
    }

    @Test
    @DisplayName("카드 순서는 (종목, 회차)로 주고받고, 응답은 회차 순으로 묶인다")
    void cardOrder() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, bench, 60, 1);
        addSet(session, squat, 80, 1);
        addSet(session, bench, 40, 2);

        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardOrder\":[{\"exerciseId\":" + bench + ",\"round\":2},{\"exerciseId\":" + squat
                                + ",\"round\":1},{\"exerciseId\":" + bench + ",\"round\":1}]}"))
                .andExpect(status().isOk())
                // 2회차 카드를 앞에 두어도 회차 순 — 1회차 안에서는 스쿼트가 먼저
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("바벨 스쿼트"))
                .andExpect(jsonPath("$.exercises[1].round").value(1))
                .andExpect(jsonPath("$.exercises[2].round").value(2))
                .andExpect(jsonPath("$.cardOrder[0].round").value(2))
                .andExpect(jsonPath("$.cardOrder[1].exerciseId").value(squat));

        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardOrder\":[{\"exerciseId\":" + bench + ",\"round\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("카드를 지우면 그 회차만 — 회차를 생략하면 1회차")
    void deleteCard() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        addSet(session, bench, 60, 1);
        addSet(session, bench, 40, 2);
        addSet(session, bench, 40, 2);

        mvc.perform(delete("/api/v1/workout-sessions/" + session + "/exercises/" + bench + "?round=2")
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        String body = session(session);
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[*].round")).containsExactly(1);

        mvc.perform(delete("/api/v1/workout-sessions/" + session + "/exercises/" + bench)
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        assertThat((List<?>) JsonPath.read(session(session), "$.exercises")).isEmpty();

        mvc.perform(delete("/api/v1/workout-sessions/" + session + "/exercises/" + bench + "?round=2")
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("직전 기록은 그날 마지막 회차의 세트")
    void lastPerformanceUsesLastRound() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(2));
        addSet(session, bench, 60, 1);
        addSet(session, bench, 60, 1);
        addSet(session, bench, 40, 2);

        mvc.perform(get("/api/v1/exercises/" + bench + "/last-performance").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets.length()").value(1))
                .andExpect(jsonPath("$.sets[0].weightKg").value(40));
    }

    @Test
    @DisplayName("회차는 1~20, 벗어나면 400")
    void roundRange() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(1));
        for (int round : new int[] {0, 21}) {
            mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(setJson(bench, 40, round)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("다른 앱 기록을 같은 날에 가져오면 다음 회차로, 빈 날이면 1회차로 들어간다")
    void importGoesToNextRound() throws Exception {
        LocalDate day = LocalDate.now(clock).minusDays(3);
        long session = openDay(day);
        addSet(session, bench, 60, null);
        importDay(day, bench, 50);
        assertThat((List<Integer>) JsonPath.read(session(session), "$.exercises[*].round")).containsExactly(1, 2);

        LocalDate empty = LocalDate.now(clock).minusDays(4);
        importDay(empty, bench, 50);
        long other = openDay(empty);
        assertThat((List<Integer>) JsonPath.read(session(other), "$.exercises[*].round")).containsExactly(1);
    }

    // ── 도우미

    private String signUp() {
        String email = "rd" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    private static String setJson(long exerciseId, int weight, Integer round) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weight + ",\"reps\":10" + (round == null ? "" : ",\"round\":" + round) + "}";
    }

    private void addSet(long session, long exerciseId, int weight, Integer round) throws Exception {
        mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content(setJson(exerciseId, weight, round)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(round == null ? 1 : round));
    }

    private void importDay(LocalDate day, long exerciseId, int weight) throws Exception {
        mvc.perform(post("/api/v1/imports").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[{\"date\":\"" + day + "\",\"minutes\":30,\"exercises\":[{\"source\":\"벤치\","
                                + "\"exerciseId\":" + exerciseId + ",\"remember\":false,\"sets\":[{\"kind\":\"SET\","
                                + "\"weightKg\":" + weight + ",\"reps\":10}]}]}]}"))
                .andExpect(status().isCreated());
    }

    private String session(long session) throws Exception {
        return mvc.perform(get("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
