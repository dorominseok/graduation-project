package com.fitness.backend.exercise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 종목(「내종목_설계서」 7장 서버 완료 기준). 만들기·고치기·지우기, 보이는 범위(남의 것은 어디서나 404),
 * 이름 규칙·상한, 분석에 들어가는지, 루틴에서 빠지는지, 탈퇴.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomExerciseApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    private String me;
    private String other;

    @BeforeEach
    void setUp() {
        me = signUp();
        other = signUp();
    }

    // ── 만들기

    @Test
    @DisplayName("만들면 201, mine=true, 고른 부위가 기본 종목과 같은 칸으로 저장된다")
    void createStoresTargetLikeBuiltins() throws Exception {
        create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", "레터럴 레이즈"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.nameKo").value("케이블 Y 레이즈"))
                .andExpect(jsonPath("$.primaryMuscle").value("shoulders"))
                .andExpect(jsonPath("$.deltRegion").value("FRONT"))
                .andExpect(jsonPath("$.bodyPart").value("SHOULDERS"))
                .andExpect(jsonPath("$.groupName").value("레터럴 레이즈"))
                .andExpect(jsonPath("$.nameEn").doesNotExist());

        create(me, body("벨트 스쿼트", "LEGS", "FRONT", "MACHINE", "WEIGHT_REPS", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryMuscle").value("quadriceps"))
                .andExpect(jsonPath("$.groupName").doesNotExist());
        create(me, body("노르딕 컬", "LEGS", "BACK", "BODYWEIGHT", "BODYWEIGHT_REPS", null))
                .andExpect(jsonPath("$.primaryMuscle").value("hamstrings"));
    }

    @Test
    @DisplayName("기본 종목의 mine은 false다")
    void builtinIsNotMine() throws Exception {
        mvc.perform(get("/api/v1/exercises/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(false));
    }

    @Test
    @DisplayName("이름·부위·기구·기록 방식이 없거나, 하체인데 앞뒤가 없거나, 그 부위에 없는 계열이면 400")
    void createValidation() throws Exception {
        create(me, body(" ", "CHEST", null, "CABLE", "WEIGHT_REPS", null)).andExpect(status().isBadRequest());
        create(me, body("가".repeat(51), "CHEST", null, "CABLE", "WEIGHT_REPS", null))
                .andExpect(status().isBadRequest());
        create(me, body("케이블 무엇", null, null, "CABLE", "WEIGHT_REPS", null)).andExpect(status().isBadRequest());
        create(me, body("케이블 무엇", "CHEST", null, null, "WEIGHT_REPS", null)).andExpect(status().isBadRequest());
        create(me, body("케이블 무엇", "CHEST", null, "CABLE", null, null)).andExpect(status().isBadRequest());
        create(me, body("하체 무엇", "LEGS", null, "MACHINE", "WEIGHT_REPS", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 레터럴 레이즈는 어깨 전·측면의 계열이다 — 가슴에는 없다
        create(me, body("가슴 무엇", "CHEST", null, "CABLE", "WEIGHT_REPS", "레터럴 레이즈"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기본 종목 이름, 내 다른 종목 이름과 같으면 409 — 띄어쓰기 여러 칸·앞뒤 공백은 같은 이름으로 본다")
    void nameRules() throws Exception {
        create(me, body("바벨 벤치프레스", "CHEST", null, "BARBELL", "WEIGHT_REPS", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_NAME_TAKEN"));
        create(me, body("인클라인 Y 레이즈", "SHOULDERS_FRONT", null, "DUMBBELL", "WEIGHT_REPS", null))
                .andExpect(status().isCreated());
        create(me, body("  인클라인   Y 레이즈 ", "SHOULDERS_FRONT", null, "DUMBBELL", "WEIGHT_REPS", null))
                .andExpect(status().isConflict());
        // 다른 사람은 같은 이름으로 만들 수 있다 — 이름은 사람마다 따로
        create(other, body("인클라인 Y 레이즈", "SHOULDERS_FRONT", null, "DUMBBELL", "WEIGHT_REPS", null))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("한 계정에 100개까지 — 101번째는 409")
    void limit() throws Exception {
        for (int i = 0; i < 100; i++) {
            create(me, body("내 종목 " + i, "CHEST", null, "CABLE", "WEIGHT_REPS", null)).andExpect(status().isCreated());
        }
        create(me, body("내 종목 100", "CHEST", null, "CABLE", "WEIGHT_REPS", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("비로그인은 만들 수 없다(401), 조회는 기본 종목만")
    void anonymous() throws Exception {
        long mine = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        mvc.perform(post("/api/v1/exercises").contentType(MediaType.APPLICATION_JSON)
                        .content(body("비로그인 종목", "CHEST", null, "CABLE", "WEIGHT_REPS", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/exercises").param("q", "케이블 Y 레이즈")).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/exercises/" + mine)).andExpect(status().isNotFound());
    }

    // ── 보이는 범위

    @Test
    @DisplayName("만든 사람에게는 검색·분류 목록·계열 안에 보이고 분류·계열 수에 들어간다")
    void visibleToOwner() throws Exception {
        long frontBefore = categoryCount(me, "SHOULDERS_FRONT");
        long lateralBefore = groupCount(me, "SHOULDERS_FRONT", "레터럴 레이즈");
        long inGroup = idOf(create(me, body("핀 머신 사이드 레터럴 레이즈", "SHOULDERS_FRONT", null, "MACHINE",
                "WEIGHT_REPS", "레터럴 레이즈")));
        long ungrouped = idOf(create(me, body("인클라인 Y 레이즈", "SHOULDERS_FRONT", null, "DUMBBELL",
                "WEIGHT_REPS", null)));

        assertThat(categoryCount(me, "SHOULDERS_FRONT")).isEqualTo(frontBefore + 2);
        assertThat(groupCount(me, "SHOULDERS_FRONT", "레터럴 레이즈")).isEqualTo(lateralBefore + 1);
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, me).param("q", "레이즈")
                        .param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) inGroup)))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) ungrouped)));
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, me)
                        .param("category", "SHOULDERS_FRONT").param("group", "레터럴 레이즈").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) inGroup)));
    }

    @Test
    @DisplayName("계열 없이 만든 내 종목은 계열 목록 끝의 mine 칸 하나로 모이고, ungroupedMine으로 목록을 본다")
    void ungroupedMineRow() throws Exception {
        String before = groups(me, "SHOULDERS_FRONT");
        assertThat((List<?>) JsonPath.read(before, "$[?(@.mine == true)]")).isEmpty();

        long ungrouped = idOf(create(me, body("인클라인 Y 레이즈", "SHOULDERS_FRONT", null, "DUMBBELL",
                "WEIGHT_REPS", null)));
        create(me, body("핀 머신 사이드 레터럴 레이즈", "SHOULDERS_FRONT", null, "MACHINE", "WEIGHT_REPS", "레터럴 레이즈"));

        String after = groups(me, "SHOULDERS_FRONT");
        List<Map<String, Object>> rows = JsonPath.read(after, "$");
        Map<String, Object> last = rows.get(rows.size() - 1);
        assertThat(last.get("mine")).isEqualTo(true);
        assertThat(last.get("groupName")).isNull();
        assertThat(((Number) last.get("count")).longValue()).isEqualTo(1L);
        // 다른 사람에게는 그 칸이 없다
        assertThat((List<?>) JsonPath.read(groups(other, "SHOULDERS_FRONT"), "$[?(@.mine == true)]")).isEmpty();

        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, me)
                        .param("category", "SHOULDERS_FRONT").param("ungroupedMine", "true"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ungrouped));
    }

    @Test
    @DisplayName("남의 내 종목은 목록·분류 수에 없고, 단건·즐겨찾기·세트 저장·직전 기록·종목 기록·1RM·고치기·지우기 모두 404")
    void hiddenFromOthers() throws Exception {
        long frontBefore = categoryCount(other, "SHOULDERS_FRONT");
        long mine = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));

        assertThat(categoryCount(other, "SHOULDERS_FRONT")).isEqualTo(frontBefore);
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, other).param("q", "케이블 Y 레이즈"))
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/exercises/" + mine).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/me/favorite-exercises/" + mine).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exercises/" + mine + "/last-performance").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exercises/" + mine + "/history").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stats/one-rm-trend").header(HttpHeaders.AUTHORIZATION, other)
                        .param("exerciseId", String.valueOf(mine)))
                .andExpect(status().isNotFound());
        long session = openDay(other, LocalDate.now(clock));
        addSet(other, session, mine).andExpect(status().isNotFound());
        update(other, mine, body("가로채기", "CHEST", null, "CABLE", "WEIGHT_REPS", null))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/exercises/" + mine).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기본 종목은 고치거나 지울 수 없다(404)")
    void builtinIsReadOnly() throws Exception {
        update(me, 5L, body("바꾼 이름", "CHEST", null, "BARBELL", "WEIGHT_REPS", null))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/exercises/5").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNotFound());
    }

    // ── 고치기·지우기

    @Test
    @DisplayName("고치면 이름·부위가 바뀐다. 다른 내 종목 이름으로는 409")
    void updateChangesTarget() throws Exception {
        long id = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        create(me, body("다른 종목", "CHEST", null, "CABLE", "WEIGHT_REPS", null));

        update(me, id, body("케이블 리어 Y 레이즈", "SHOULDERS_REAR", null, "CABLE", "WEIGHT_REPS", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameKo").value("케이블 리어 Y 레이즈"))
                .andExpect(jsonPath("$.deltRegion").value("REAR"));
        update(me, id, body("다른 종목", "SHOULDERS_REAR", null, "CABLE", "WEIGHT_REPS", null))
                .andExpect(status().isConflict());
        // 같은 이름 그대로 다른 값만 바꾸는 것은 된다
        update(me, id, body("케이블 리어 Y 레이즈", "SHOULDERS_REAR", null, "DUMBBELL", "WEIGHT_REPS", null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("기록이 없으면 지운다 — 다시 같은 이름으로 만들면 새 종목")
    void deleteWithoutSets() throws Exception {
        long id = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        mvc.perform(delete("/api/v1/exercises/" + id).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        em.flush(); // 테스트가 한 트랜잭션이라 JPA 변경을 DB에 내려야 JdbcTemplate이 본다
        assertThat(jdbc.queryForObject("select count(*) from exercises where id = ?", Long.class, id)).isZero();
        long again = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        assertThat(again).isNotEqualTo(id);
    }

    @Test
    @DisplayName("기록이 있으면 숨긴다 — 목록·단건·세트 저장에서 빠지고 지난 기록의 이름은 남는다. 같은 이름으로 만들면 되살아난다")
    void deleteWithSetsHidesAndRestores() throws Exception {
        long id = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        LocalDate today = LocalDate.now(clock);
        long session = openDay(me, today);
        addSet(me, session, id).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/exercises/" + id).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select hidden_at is not null from exercises where id = ?", Boolean.class, id))
                .isTrue();
        mvc.perform(get("/api/v1/exercises/" + id).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, me).param("q", "케이블 Y 레이즈"))
                .andExpect(jsonPath("$.content").isEmpty());
        addSet(me, session, id).andExpect(status().isNotFound());
        // 지난 기록의 이름은 그대로
        mvc.perform(get("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("케이블 Y 레이즈"));

        long restored = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_REAR", null, "DUMBBELL", "WEIGHT_REPS", null)));
        assertThat(restored).isEqualTo(id);
        mvc.perform(get("/api/v1/exercises/" + id).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deltRegion").value("REAR"));
    }

    // ── 다른 기능과의 관계

    @Test
    @DisplayName("내 종목 세트는 그 부위의 회복 상태로 세어진다")
    void countedInRecovery() throws Exception {
        long id = idOf(create(me, body("케이블 크로스 무엇", "CHEST", null, "CABLE", "WEIGHT_REPS", null)));
        LocalDate today = LocalDate.now(clock);
        long session = openDay(me, today);
        addSet(me, session, id).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/analysis/recovery").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muscles[0].key").value("CHEST"))
                .andExpect(jsonPath("$.muscles[0].lastTrainedOn").value(today.toString()));
    }

    @Test
    @DisplayName("루틴 후보·교체 후보에 내 종목이 안 나온다 — 즐겨찾기하고 기록해도. 교체로 넣으면 400")
    void excludedFromRoutine() throws Exception {
        String[][] perMuscle = {
                {"CHEST", null}, {"BACK", null}, {"SHOULDERS_FRONT", null}, {"SHOULDERS_REAR", null},
                {"TRICEPS", null}, {"BICEPS", null}, {"LEGS", "FRONT"}, {"LEGS", "BACK"}, {"ABS", null}};
        List<Long> customIds = new ArrayList<>();
        long session = openDay(me, LocalDate.now(clock).minusDays(5));
        for (String[] target : perMuscle) {
            long id = idOf(create(me, body("내 " + target[0] + " " + target[1], target[0], target[1], "CABLE",
                    "WEIGHT_REPS", null)));
            customIds.add(id);
            mvc.perform(put("/api/v1/users/me/favorite-exercises/" + id).header(HttpHeaders.AUTHORIZATION, me))
                    .andExpect(status().isNoContent());
            addSet(me, session, id).andExpect(status().isCreated());
        }

        String routine = mvc.perform(post("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> itemExercises = JsonPath.read(routine, "$.items[*].exerciseId");
        assertThat(itemExercises).isNotEmpty();
        assertThat(itemExercises).noneMatch(id -> customIds.contains(id.longValue()));

        long routineId = ((Number) JsonPath.read(routine, "$.id")).longValue();
        long itemId = ((Number) JsonPath.read(routine, "$.items[0].id")).longValue();
        String itemMuscle = JsonPath.read(routine, "$.items[0].muscleGroup");
        String alternatives = mvc.perform(get("/api/v1/routines/" + routineId + "/items/" + itemId + "/alternatives")
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> alternativeIds = JsonPath.read(alternatives, "$[*].exerciseId");
        assertThat(alternativeIds).noneMatch(id -> customIds.contains(id.longValue()));

        long sameMuscleCustom = customIds.get(List.of("CHEST", "BACK", "DELT_FRONT", "DELT_REAR", "TRICEPS",
                "BICEPS", "QUADS", "POSTERIOR", "CORE").indexOf(itemMuscle));
        mvc.perform(patch("/api/v1/routines/" + routineId + "/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":" + sameMuscleCustom + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 종목과 그 기록이 있어도 탈퇴하면 함께 지워진다")
    void withdrawRemovesCustomExercises() throws Exception {
        long id = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        long session = openDay(me, LocalDate.now(clock));
        addSet(me, session, id).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"hunter2hunter2\"}"))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from exercises where id = ?", Long.class, id)).isZero();
    }

    @Test
    @DisplayName("다른 사람의 목록·검색 결과에는 남의 내 종목이 섞이지 않는다")
    void notMixedIntoOthersSearch() throws Exception {
        long mine = idOf(create(me, body("케이블 Y 레이즈", "SHOULDERS_FRONT", null, "CABLE", "WEIGHT_REPS", null)));
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, other)
                        .param("category", "SHOULDERS_FRONT").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) mine))));
    }

    // ── 도우미

    private String signUp() {
        String email = "cx" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private static String body(String name, String category, String legSide, String equipment, String measureType,
                               String groupName) {
        return "{\"nameKo\":" + quote(name) + ",\"category\":" + quote(category) + ",\"legSide\":" + quote(legSide)
                + ",\"equipment\":" + quote(equipment) + ",\"measureType\":" + quote(measureType)
                + ",\"groupName\":" + quote(groupName) + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private ResultActions create(String auth, String json) throws Exception {
        return mvc.perform(post("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(String auth, long id, String json) throws Exception {
        return mvc.perform(put("/api/v1/exercises/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long categoryCount(String auth, String category) throws Exception {
        String body = mvc.perform(get("/api/v1/exercises/categories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> counts = JsonPath.read(body, "$[?(@.category == '" + category + "')].count");
        return counts.get(0).longValue();
    }

    private String groups(String auth, String category) throws Exception {
        return mvc.perform(get("/api/v1/exercises/groups").header(HttpHeaders.AUTHORIZATION, auth)
                        .param("category", category))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long groupCount(String auth, String category, String groupName) throws Exception {
        List<Number> counts = JsonPath.read(groups(auth, category), "$[?(@.groupName == '" + groupName + "')].count");
        return counts.get(0).longValue();
    }

    private long openDay(String auth, LocalDate day) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions addSet(String auth, long session, long exerciseId) throws Exception {
        return mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                        + ",\"weightKg\":20.0,\"reps\":10}"));
    }
}
