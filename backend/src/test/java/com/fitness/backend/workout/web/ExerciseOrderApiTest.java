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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 기록의 종목 순서(V18, 「운동기록_방식_설계서」 3.2). 원래는 첫 세트가 이른 순이고, 사용자가 정한 순서가
 * 있으면 그것이 앞선다 — 상세·히스토리 모두.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class ExerciseOrderApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String me;
    private long session;
    private long bench;
    private long squat;
    private long pullup;

    @BeforeEach
    void setUp() throws Exception {
        me = signUp();
        bench = builtinId("바벨 벤치프레스");
        squat = builtinId("바벨 스쿼트");
        pullup = builtinId("중량 풀업");
        session = openDay(me, LocalDate.now(clock).minusDays(3));
        // 벤치 → 스쿼트 → 풀업 순으로 첫 세트
        addSet(bench);
        addSet(squat);
        addSet(pullup);
        addSet(bench);
    }

    @Test
    @DisplayName("정한 순서가 없으면 첫 세트가 이른 순, exerciseOrder는 빈 배열")
    void defaultOrder() throws Exception {
        String body = session(me);
        assertThat(names(body)).containsExactly("바벨 벤치프레스", "바벨 스쿼트", "중량 풀업");
        assertThat((List<?>) JsonPath.read(body, "$.exerciseOrder")).isEmpty();
    }

    @Test
    @DisplayName("순서를 정하면 상세와 히스토리가 그 순서다. 응답에 정한 순서가 실린다")
    void customOrder() throws Exception {
        reorder(List.of(pullup, bench, squat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("중량 풀업"));

        String body = session(me);
        assertThat(names(body)).containsExactly("중량 풀업", "바벨 벤치프레스", "바벨 스쿼트");
        assertThat(((List<Number>) JsonPath.read(body, "$.exerciseOrder")).stream().map(Number::longValue))
                .containsExactly(pullup, bench, squat);
        // 세트 순서(setNo·저장 시각)는 그대로다
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[1].sets[*].setNo")).containsExactly(1, 2);

        String history = mvc.perform(get("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<String>) JsonPath.read(history, "$.content[0].exercises[*].nameKo"))
                .containsExactly("중량 풀업", "바벨 벤치프레스", "바벨 스쿼트");
    }

    @Test
    @DisplayName("정한 순서에 없는 종목은 뒤에 원래 순서로, 세트 없는 종목의 id는 걸러진다. 겹친 id는 앞의 것만")
    void partialOrder() throws Exception {
        long plank = builtinId("플랭크");
        reorder(List.of(plank, pullup, pullup)).andExpect(status().isOk());
        assertThat(names(session(me))).containsExactly("중량 풀업", "바벨 벤치프레스", "바벨 스쿼트");

        // 세트 없던 플랭크에 세트가 생기면 정한 자리(맨 앞)에 온다
        mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + plank
                                + ",\"durationSec\":60}"))
                .andExpect(status().isCreated());
        String body = session(me);
        assertThat(names(body)).containsExactly("플랭크", "중량 풀업", "바벨 벤치프레스", "바벨 스쿼트");
        assertThat(((List<Number>) JsonPath.read(body, "$.exerciseOrder")).stream().map(Number::longValue))
                .containsExactly(plank, pullup);
    }

    @Test
    @DisplayName("빈 배열이면 원래 순서로 돌아가고, 보내지 않으면 그대로 둔다")
    void resetAndKeep() throws Exception {
        reorder(List.of(squat, pullup, bench)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"하체 먼저\"}"))
                .andExpect(status().isOk());
        assertThat(names(session(me))).containsExactly("바벨 스쿼트", "중량 풀업", "바벨 벤치프레스");

        reorder(List.of()).andExpect(status().isOk());
        String body = session(me);
        assertThat(names(body)).containsExactly("바벨 벤치프레스", "바벨 스쿼트", "중량 풀업");
        assertThat((List<?>) JsonPath.read(body, "$.exerciseOrder")).isEmpty();
    }

    @Test
    @DisplayName("종목을 통째로 지우면 순서에서도 빠진다 — 다시 넣으면 맨 뒤")
    void deleteExerciseDropsFromOrder() throws Exception {
        reorder(List.of(pullup, squat, bench)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/workout-sessions/" + session + "/exercises/" + pullup)
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        addSet(pullup);

        String body = session(me);
        assertThat(names(body)).containsExactly("바벨 스쿼트", "바벨 벤치프레스", "중량 풀업");
        assertThat(((List<Number>) JsonPath.read(body, "$.exerciseOrder")).stream().map(Number::longValue))
                .containsExactly(squat, bench);
    }

    @Test
    @DisplayName("100개를 넘거나 null이 섞이면 400, 남의 기록은 404")
    void validation() throws Exception {
        String tooMany = IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exerciseOrder\":[" + tooMany + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exerciseOrder\":[" + bench + ",null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, signUp())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exerciseOrder\":[" + bench + "]}"))
                .andExpect(status().isNotFound());
    }

    // ── 도우미

    private String signUp() {
        String email = "eo" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private long builtinId(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = ?", Long.class, name);
    }

    private long openDay(String auth, LocalDate day) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addSet(long exerciseId) throws Exception {
        mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                                + ",\"weightKg\":40,\"reps\":10}"))
                .andExpect(status().isCreated());
    }

    private ResultActions reorder(List<Long> order) throws Exception {
        String ids = order.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON).content("{\"exerciseOrder\":[" + ids + "]}"));
    }

    private String session(String auth) throws Exception {
        return mvc.perform(get("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> names(String body) {
        return JsonPath.read(body, "$.exercises[*].exerciseName");
    }
}
