package com.fitness.backend.workout.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * 운동 기록 — 히스토리·캘린더·수정·삭제. 명세 6.6·6.8~6.12 + LOG-22.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutHistoryApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;
    private long benchPress;
    private long plank;

    @BeforeEach
    void setUp() throws Exception {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
        benchPress = exerciseIdOf("바벨 벤치프레스");
        plank = exerciseIdOf("플랭크");
    }

    // ── 히스토리 (6.6)

    @Test
    @DisplayName("히스토리는 세트를 담지 않고 개수와 종목 목록만 준다")
    void historyIsSummaryOnly() throws Exception {
        long sessionId = liveSessionWith(benchPress, plank);

        mvc.perform(get("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(sessionId))
                .andExpect(jsonPath("$.content[0].setCount").value(2))
                .andExpect(jsonPath("$.content[0].exerciseCount").value(2))
                // 이름만 주면 목록에서 종목 상세로 이어갈 수 없다(명세 6.6).
                .andExpect(jsonPath("$.content[0].exercises[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].exercises[0].nameKo").value("바벨 벤치프레스"))
                .andExpect(jsonPath("$.content[0].sets").doesNotExist());
    }

    @Test
    @DisplayName("종목으로 거르면 그 종목이 있는 세션만 나온다 — 세션 단위 필터다")
    void historyFiltersByExercise() throws Exception {
        long withBench = liveSessionWith(benchPress);
        complete(withBench);
        long withPlankOnly = createSession(LocalDate.now().minusDays(1), "BACKFILL");
        postSet(withPlankOnly, timeSet(plank, 60)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/workout-sessions")
                        .param("exerciseId", String.valueOf(benchPress))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(withBench));

        mvc.perform(get("/api/v1/workout-sessions")
                        .param("exerciseId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                // 없는 종목은 오류가 아니라 빈 페이지다.
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("상태로도 거를 수 있다")
    void historyFiltersByStatus() throws Exception {
        long draft = liveSessionWith(benchPress);

        mvc.perform(get("/api/v1/workout-sessions")
                        .param("status", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(draft));

        mvc.perform(get("/api/v1/workout-sessions")
                        .param("status", "DONE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ── 캘린더 (6.8)

    @Test
    @DisplayName("캘린더는 기록이 있는 날짜만 주고 진행 중 여부를 함께 준다")
    void calendarListsOnlyRecordedDays() throws Exception {
        liveSessionWith(benchPress);
        LocalDate today = LocalDate.now();

        mvc.perform(get("/api/v1/workout-sessions/calendar")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(today.getYear()))
                .andExpect(jsonPath("$.days[0].date").value(today.toString()))
                .andExpect(jsonPath("$.days[0].sessionCount").value(1))
                .andExpect(jsonPath("$.days[0].hasDraft").value(true));
    }

    // ── 세션 수정 (6.9)

    @Test
    @DisplayName("메모만 바꾸면 다른 값은 그대로 둔다")
    void patchOnlyTouchesSentFields() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        patchSession(sessionId, "{\"memo\":\"가슴 + 삼두\",\"durationOverrideSec\":4200}")
                .andExpect(status().isOk());

        patchSession(sessionId, "{\"memo\":\"컨디션 좋았음\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").value("컨디션 좋았음"))
                // 보내지 않은 보정값이 날아가면 안 된다.
                .andExpect(jsonPath("$.durationOverrideSec").value(4200));
    }

    @Test
    @DisplayName("빈 문자열과 0이 값을 지운다 — null은 '건드리지 않음'이라 쓸 수 없다")
    void blankAndZeroClearValues() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        patchSession(sessionId, "{\"memo\":\"지울 메모\",\"durationOverrideSec\":4200}")
                .andExpect(status().isOk());

        patchSession(sessionId, "{\"memo\":\"\",\"durationOverrideSec\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").doesNotExist())
                .andExpect(jsonPath("$.durationOverrideSec").doesNotExist());
    }

    @Test
    @DisplayName("보정값을 넣으면 effectiveDurationSec이 그 값이 된다")
    void overrideWinsOverCalculated() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        complete(sessionId);

        patchSession(sessionId, "{\"durationOverrideSec\":4200}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSec").isNumber())
                .andExpect(jsonPath("$.effectiveDurationSec").value(4200));
    }

    @Test
    @DisplayName("진행 중 기록의 날짜는 바꿀 수 없다 — 오늘 기록이라는 전제가 깨진다")
    void liveSessionDateIsFixed() throws Exception {
        long sessionId = liveSessionWith(benchPress);

        patchSession(sessionId, "{\"performedOn\":\"" + LocalDate.now().minusDays(2) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("지난 기록은 날짜를 정정할 수 있다")
    void backfillDateIsCorrectable() throws Exception {
        long sessionId = createSession(LocalDate.now().minusDays(3), "BACKFILL");
        LocalDate corrected = LocalDate.now().minusDays(5);

        patchSession(sessionId, "{\"performedOn\":\"" + corrected + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performedOn").value(corrected.toString()));
    }

    // ── 세트 수정 (6.11)

    @Test
    @DisplayName("세트 값을 고칠 수 있고, 측정 유형이 쓰지 않는 값은 무시한다")
    void setIsEditable() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        long setId = firstSetId(sessionId);

        mvc.perform(patch("/api/v1/workout-sessions/" + sessionId + "/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":72.5,\"reps\":8,\"durationSec\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(72.5))
                .andExpect(jsonPath("$.reps").value(8))
                // WEIGHT_REPS 종목이라 수행 시간은 담기지 않는다.
                .andExpect(jsonPath("$.durationSec").doesNotExist());
    }

    // ── 삭제 (6.10, 6.12, LOG-22)

    @Test
    @DisplayName("세션을 지우면 세트도 함께 사라진다")
    void deletingSessionRemovesSets() throws Exception {
        long sessionId = liveSessionWith(benchPress);

        mvc.perform(delete("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("세트 하나를 지워도 세션은 남는다")
    void deletingLastSetKeepsSession() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        long setId = firstSetId(sessionId);

        mvc.perform(delete("/api/v1/workout-sessions/" + sessionId + "/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises").isEmpty());
    }

    @Test
    @DisplayName("종목을 빼면 그 종목의 세트가 한 번에 사라지고 다른 종목은 남는다")
    void deletingExerciseRemovesItsSets() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        postSet(sessionId, weightSet(benchPress, "70.0", 9)).andExpect(status().isCreated());
        postSet(sessionId, timeSet(plank, 60)).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/workout-sessions/" + sessionId + "/exercises/" + benchPress)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("플랭크"));
    }

    @Test
    @DisplayName("기록에 없는 종목은 뺄 수 없다")
    void deletingAbsentExerciseIsNotFound() throws Exception {
        long sessionId = liveSessionWith(benchPress);

        mvc.perform(delete("/api/v1/workout-sessions/" + sessionId + "/exercises/" + plank)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 기록은 지울 수 없다")
    void cannotDeleteOthersSession() throws Exception {
        long sessionId = liveSessionWith(benchPress);
        String other = "Bearer " + authService
                .signUp("o" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", "hunter2hunter2", "타인")
                .tokens().accessToken();

        mvc.perform(delete("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
    }

    // ── 헬퍼

    /** 세션을 만들고 종목마다 세트를 하나씩 넣는다 */
    private long liveSessionWith(long... exerciseIds) throws Exception {
        long sessionId = createSession(LocalDate.now(), "LIVE");
        for (long exerciseId : exerciseIds) {
            String body = exerciseId == plank ? timeSet(exerciseId, 60) : weightSet(exerciseId, "70.0", 10);
            postSet(sessionId, body).andExpect(status().isCreated());
        }
        return sessionId;
    }

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

    private ResultActions patchSession(long sessionId, String body) throws Exception {
        return mvc.perform(patch("/api/v1/workout-sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long firstSetId(long sessionId) throws Exception {
        String body = mvc.perform(get("/api/v1/workout-sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"sets\":\\[\\{\"id\":(\\d+)").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("세트를 찾을 수 없다: " + body);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String weightSet(long exerciseId, String weightKg, int reps) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weightKg + ",\"reps\":" + reps + "}";
    }

    private String timeSet(long exerciseId, int durationSec) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"durationSec\":" + durationSec + "}";
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
