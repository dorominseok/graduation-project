package com.fitness.backend.workout.web;

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
 * 종목 프리필 — 직전 수행 기록. 명세 5.5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LastPerformanceApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;
    private long benchPress;

    @BeforeEach
    void setUp() throws Exception {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
        benchPress = exerciseIdOf("바벨 벤치프레스");
    }

    @Test
    @DisplayName("직전 수행 기록을 세트째로 준다 — 워밍업도 담고 구분만 한다")
    void returnsLastPerformedSets() throws Exception {
        long sessionId = createSession(LocalDate.now(), "LIVE");
        postSet(sessionId, set(benchPress, "60.0", 12, true)).andExpect(status().isCreated());
        postSet(sessionId, set(benchPress, "70.0", 10, false)).andExpect(status().isCreated());
        complete(sessionId);

        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseName").value("바벨 벤치프레스"))
                // 종목 조회를 한 번 더 하지 않도록 measureType을 함께 준다.
                .andExpect(jsonPath("$.measureType").value("WEIGHT_REPS"))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.sets.length()").value(2))
                .andExpect(jsonPath("$.sets[0].isWarmup").value(true))
                .andExpect(jsonPath("$.sets[1].weightKg").value(70.0))
                // 새 세션에 채워 넣을 참조값이라 세트 id는 담지 않는다.
                .andExpect(jsonPath("$.sets[0].id").doesNotExist());
    }

    @Test
    @DisplayName("처음 하는 종목이면 204다 — 오류가 아니라 정상 상태다")
    void noRecordIsNoContent() throws Exception {
        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("진행 중인 세션은 직전 기록이 아니다 — 끝난 기록만 본다")
    void draftSessionIsNotCounted() throws Exception {
        long sessionId = createSession(LocalDate.now(), "LIVE");
        postSet(sessionId, set(benchPress, "70.0", 10, false)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("나중에 입력한 과거 기록이 직전 기록을 밀어내지 않는다 — 저장 시각이 아니라 운동 날짜로 고른다")
    void backfillDoesNotOverrideNewerSession() throws Exception {
        // 오늘 운동을 먼저 끝낸다.
        long today = createSession(LocalDate.now(), "LIVE");
        postSet(today, set(benchPress, "80.0", 5, false)).andExpect(status().isCreated());
        complete(today);

        // 그 뒤에 일주일 전 운동을 사후 입력한다. 저장 시각은 이쪽이 더 최근이다.
        long past = createSession(LocalDate.now().minusDays(7), "BACKFILL");
        postSet(past, set(benchPress, "50.0", 15, false)).andExpect(status().isCreated());
        complete(past);

        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(today))
                .andExpect(jsonPath("$.sets[0].weightKg").value(80.0));
    }

    @Test
    @DisplayName("남의 기록은 보이지 않는다")
    void othersRecordIsNotVisible() throws Exception {
        long sessionId = createSession(LocalDate.now(), "LIVE");
        postSet(sessionId, set(benchPress, "70.0", 10, false)).andExpect(status().isCreated());
        complete(sessionId);

        String other = "Bearer " + authService
                .signUp("o" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", "hunter2hunter2", "타인")
                .tokens().accessToken();

        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("없는 종목이면 404다")
    void unknownExerciseIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/exercises/999999/last-performance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("프리필은 로그인이 필요하다 — 종목 목록과 달리 개인 기록이다")
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/v1/exercises/" + benchPress + "/last-performance"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private long createSession(LocalDate performedOn, String source) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + performedOn + "\",\"source\":\"" + source + "\"}"))
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

    private String set(long exerciseId, String weightKg, int reps, boolean warmup) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weightKg + ",\"reps\":" + reps + ",\"isWarmup\":" + warmup + "}";
    }

    private long exerciseIdOf(String nameKo) throws Exception {
        String body = mvc.perform(get("/api/v1/exercises").param("q", nameKo).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"id\":(\\d+),\"nameKo\":\"" + Pattern.quote(nameKo) + "\"")
                .matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException(nameKo + " 종목이 시드에 없다");
        }
        return Long.parseLong(matcher.group(1));
    }

    private static long firstId(String json) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("id를 찾을 수 없다: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}
