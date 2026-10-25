package com.fitness.backend.routine.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
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
 * 오늘의 루틴 API.
 *
 * <p>부위·세트·후보를 고르는 규칙은 {@code routine.domain} 단위 테스트가 덮는다. 여기서 보는 것은
 * <b>배선</b>이다 — 분석 결과와 기록이 규칙에 제대로 들어가는지, 하루 하나·후보정·만족도의
 * 제약이 API에서 지켜지는지.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    private String bearer;
    private LocalDate today;
    private long benchPress;     // CHEST
    private long barbellCurl;    // BICEPS

    @BeforeEach
    void setUp() throws Exception {
        bearer = signUp();
        today = LocalDate.now(clock);
        benchPress = exerciseIdOf("바벨 벤치프레스");
        barbellCurl = exerciseIdOf("바벨 컬");
    }

    // ── 만들기

    @Test
    @DisplayName("기록이 없으면 주 5회로 보고 밀기 묶음을 6세트씩 — 첫날 18세트, 규칙이 만들고 신뢰도 낮음 안내가 붙는다")
    void newUserGetsPushDay() throws Exception {
        createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routineDate").value(today.toString()))
                .andExpect(jsonPath("$.referenceDate").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.generatedBy").value("RULE"))
                // 코어가 없는 날이라 코어를 말하지 않는다
                .andExpect(jsonPath("$.comment").value("큰 근육부터 시작해서 작은 근육으로 마무리하는 순서예요."))
                .andExpect(jsonPath("$.lowConfidence").value(true))
                .andExpect(jsonPath("$.totalSets").value(18))
                .andExpect(jsonPath("$.doneSets").value(0))
                // 처음 하는 종목뿐이라 나누지 않는다 — 부위마다 한 종목
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.items[1].muscleGroup").value("DELT_FRONT"))
                .andExpect(jsonPath("$.items[2].muscleGroup").value("TRICEPS"))
                .andExpect(jsonPath("$.items[0].plannedSets").value(6))
                // 해 본 적 없는 종목이라 [처음]이 붙고, 중량 종목은 8~12회다
                .andExpect(jsonPath("$.items[0].firstTime").value(true))
                .andExpect(jsonPath("$.items[0].targetMin").value(8))
                .andExpect(jsonPath("$.items[0].targetMax").value(12))
                .andExpect(jsonPath("$.items[0].basisVerdict").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.items[0].basisVerdictLabel").value("부족"))
                .andExpect(jsonPath("$.askFeedback").value(false));
    }

    @Test
    @DisplayName("하루에 하나 — 다시 누르면 같은 루틴을 200으로 준다")
    void oneRoutinePerDay() throws Exception {
        long first = idOf(createToday().andExpect(status().isCreated()));
        long again = idOf(createToday().andExpect(status().isOk()));

        assertEquals(first, again);
        mvc.perform(get("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first));
    }

    @Test
    @DisplayName("받기 전에는 오늘 루틴이 204다")
    void noRoutineYet() throws Exception {
        mvc.perform(get("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("오늘 이미 한 부위는 빠진다 — 판정은 어제 기준이어도 회복은 오늘까지 본다")
    void excludesMuscleTrainedToday() throws Exception {
        sets(today, benchPress, 3);

        createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[?(@.muscleGroup == 'CHEST')]").isEmpty())
                .andExpect(jsonPath("$.items[0].muscleGroup").value("BACK"))
                // 오늘 세트는 기준일(어제) 밖이라 판정에 안 들어간다
                .andExpect(jsonPath("$.items[0].basisWeeklySets").value(0.0));
    }

    @Test
    @DisplayName("가슴만 3세트 했으면 밀기에 치우쳐 적은 쪽인 당기기 묶음이 온다")
    void lesserSideBundleComes() throws Exception {
        sets(today.minusDays(3), benchPress, 3);

        createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[*].muscleGroup").value(
                        Matchers.contains("BACK", "DELT_REAR", "BICEPS")));
    }

    @Test
    @DisplayName("즐겨찾기한 종목이 후보 1순위가 된다")
    void favoriteComesFirst() throws Exception {
        // 입문 대표 종목(바벨 벤치프레스)보다 즐겨찾기가 먼저다
        long decline = exerciseIdOf("디클라인 덤벨 벤치프레스");
        mvc.perform(put("/api/v1/users/me/favorite-exercises/" + decline).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().is2xxSuccessful());

        createToday()
                .andExpect(jsonPath("$.items[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.items[0].exerciseId").value(decline))
                .andExpect(jsonPath("$.items[0].firstTime").value(true));
    }

    @Test
    @DisplayName("기록이 없으면 입문 대표 종목으로 짠다 — 벤치프레스 · 덤벨 숄더프레스 · 케이블 푸시다운")
    void newUserGetsStarterExercises() throws Exception {
        createToday()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[*].exerciseId").value(Matchers.contains(
                        (int) benchPress,
                        (int) exerciseIdOf("덤벨 숄더프레스"),
                        (int) exerciseIdOf("케이블 푸시다운"))));
    }

    @Test
    @DisplayName("오늘 전에 해 본 종목으로 바꾸면 [처음]이 떨어진다")
    void swapToDoneExerciseIsNotFirstTime() throws Exception {
        long routineId = idOf(createToday());
        long itemId = itemId(routineId, 0);
        String alternatives = mvc.perform(get("/api/v1/routines/" + routineId + "/items/" + itemId + "/alternatives")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long done = ((Number) JsonPath.read(alternatives, "$[0].exerciseId")).longValue();
        sets(today.minusDays(3), done, 3);   // 지난 날짜로 나중에 적은 기록

        updateItem(routineId, itemId, "{\"exerciseId\":" + done + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].exerciseId").value(done))
                .andExpect(jsonPath("$.items[0].firstTime").value(false));
    }

    @Test
    @DisplayName("루틴을 받으면 그날 기록에 루틴이 이어진다")
    void linksDayRecord() throws Exception {
        long routineId = idOf(createToday());

        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + today + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routineId").value(routineId));
    }

    @Test
    @DisplayName("체크한 본세트가 진행으로 잡히고, 계획보다 많이 해도 계획만큼만 센다")
    void doneSetsCappedByPlan() throws Exception {
        long routineId = idOf(createToday());
        long chestExercise = itemExerciseId(routineId, 0);

        sets(today, chestExercise, 8);

        routine(routineId)
                .andExpect(jsonPath("$.items[0].doneSets").value(8))
                .andExpect(jsonPath("$.doneSets").value(6))
                .andExpect(jsonPath("$.askFeedback").value(true));
    }

    // ── 후보정

    @Test
    @DisplayName("같은 부위 종목으로 바꾸고 세트를 조정한다 — 바꾼 표시가 남는다")
    void swapAndAdjust() throws Exception {
        long routineId = idOf(createToday());
        long itemId = itemId(routineId, 0);
        long before = itemExerciseId(routineId, 0);

        String alternatives = mvc.perform(get("/api/v1/routines/" + routineId + "/items/" + itemId + "/alternatives")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].exerciseId", not(Matchers.hasItem((int) before))))
                // 기구가 다른 종목이 먼저 온다
                .andExpect(jsonPath("$[0].sameEquipment").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long after = ((Number) JsonPath.read(alternatives, "$[0].exerciseId")).longValue();
        assertNotEquals(before, after);

        updateItem(routineId, itemId, "{\"exerciseId\":" + after + ",\"plannedSets\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].exerciseId").value(after))
                .andExpect(jsonPath("$.items[0].plannedSets").value(2))
                .andExpect(jsonPath("$.items[0].adjusted").value(true))
                .andExpect(jsonPath("$.items[1].adjusted").value(false))
                .andExpect(jsonPath("$.totalSets").value(14));
    }

    @Test
    @DisplayName("다른 부위 종목으로는 바꿀 수 없다 — 루틴의 근거가 무너진다")
    void swapToOtherMuscleRejected() throws Exception {
        long routineId = idOf(createToday());

        updateItem(routineId, itemId(routineId, 0), "{\"exerciseId\":" + barbellCurl + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("세트는 1~10만 받는다")
    void plannedSetsBounded() throws Exception {
        long routineId = idOf(createToday());

        updateItem(routineId, itemId(routineId, 0), "{\"plannedSets\":0}").andExpect(status().isBadRequest());
        updateItem(routineId, itemId(routineId, 0), "{\"plannedSets\":11}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빼기는 지우지 않고 표시만 한다 — 합계에서는 빠진다")
    void removeKeepsItem() throws Exception {
        long routineId = idOf(createToday());

        mvc.perform(delete("/api/v1/routines/" + routineId + "/items/" + itemId(routineId, 0))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].removed").value(true))
                .andExpect(jsonPath("$.items[0].adjusted").value(true))
                .andExpect(jsonPath("$.totalSets").value(12));
    }

    @Test
    @DisplayName("지난 루틴은 고칠 수 없다")
    void pastRoutineIsFrozen() throws Exception {
        long routineId = idOf(createToday());
        long itemId = itemId(routineId, 0);
        moveToYesterday(routineId);

        updateItem(routineId, itemId, "{\"plannedSets\":3}").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/routines/" + routineId + "/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 루틴은 있는지조차 알 수 없다 — 404")
    void othersRoutineIsNotFound() throws Exception {
        long routineId = idOf(createToday());
        String other = signUp();

        mvc.perform(get("/api/v1/routines/" + routineId).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/routines/" + routineId + "/feedback")
                        .header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isNotFound());
    }

    // ── 만족도

    @Test
    @DisplayName("만족도는 루틴당 한 번 — 좋았어요는 이유를 남기지 않고, 두 번째는 409")
    void feedbackOnce() throws Exception {
        long routineId = idOf(createToday());

        feedback(routineId, "{\"rating\":\"GOOD\",\"reason\":\"TOO_MUCH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.rating").value("GOOD"))
                .andExpect(jsonPath("$.feedback.reason").isEmpty())
                .andExpect(jsonPath("$.feedback.skipped").value(false));

        feedback(routineId, "{\"rating\":\"BAD\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("별로였어요는 이유를 남기고, 건너뛰어도 다시 묻지 않는다")
    void feedbackReasonAndSkip() throws Exception {
        long routineId = idOf(createToday());
        feedback(routineId, "{\"rating\":\"BAD\",\"reason\":\"TOO_MUCH\"}")
                .andExpect(jsonPath("$.feedback.reason").value("TOO_MUCH"));

        String other = signUp();
        bearer = other;
        long skipped = idOf(createToday());
        feedback(skipped, "{\"skipped\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.skipped").value(true));
        feedback(skipped, "{\"skipped\":true}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("만족도도 건너뛰기도 없으면 400")
    void feedbackNeedsSomething() throws Exception {
        long routineId = idOf(createToday());

        feedback(routineId, "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지난 루틴은 그 부위를 체크한 날에만 다음 방문에 묻고, 답하면 더 묻지 않는다")
    void feedbackPendingForPastRoutine() throws Exception {
        long routineId = idOf(createToday());
        long chestExercise = itemExerciseId(routineId, 0);
        moveToYesterday(routineId);

        // 체크하지 않은 날은 묻지 않는다
        pending().andExpect(status().isNoContent());

        sets(today.minusDays(1), chestExercise, 2);
        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routineId))
                // 홈 카드의 인라인 질문은 오늘 루틴만이다
                .andExpect(jsonPath("$.askFeedback").value(false));

        feedback(routineId, "{\"skipped\":true}").andExpect(status().isOk());
        pending().andExpect(status().isNoContent());
    }

    // ── 도우미

    private String signUp() {
        String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private ResultActions createToday() throws Exception {
        return mvc.perform(post("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions routine(long routineId) throws Exception {
        return mvc.perform(get("/api/v1/routines/" + routineId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private ResultActions pending() throws Exception {
        return mvc.perform(get("/api/v1/routines/feedback-pending").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions updateItem(long routineId, long itemId, String body) throws Exception {
        return mvc.perform(patch("/api/v1/routines/" + routineId + "/items/" + itemId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions feedback(long routineId, String body) throws Exception {
        return mvc.perform(put("/api/v1/routines/" + routineId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 오늘 만든 루틴을 어제 것으로 옮긴다. 하루를 실제로 기다릴 수 없어서다 */
    private void moveToYesterday(long routineId) {
        em.flush();
        jdbc.update("UPDATE routines SET routine_date = ?, reference_date = ? WHERE id = ?",
                today.minusDays(1), today.minusDays(2), routineId);
        em.clear();
    }

    private long itemId(long routineId, int index) throws Exception {
        return readLong(routine(routineId), "$.items[" + index + "].id");
    }

    private long itemExerciseId(long routineId, int index) throws Exception {
        return readLong(routine(routineId), "$.items[" + index + "].exerciseId");
    }

    private static long idOf(ResultActions result) throws Exception {
        return readLong(result, "$.id");
    }

    private static long readLong(ResultActions result, String path) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private void sets(LocalDate day, long exerciseId, int count) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long session = ((Number) JsonPath.read(body, "$.id")).longValue();
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                                    + ",\"weightKg\":40.0,\"reps\":10}"))
                    .andExpect(status().isCreated());
        }
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
}
