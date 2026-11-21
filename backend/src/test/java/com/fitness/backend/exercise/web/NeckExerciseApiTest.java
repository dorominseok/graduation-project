package com.fitness.backend.exercise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 부위 "목"(2026-11-20 사용자 요청, V19). 종목 13종·계열 5개, 분석에서는 전완·종아리처럼 세트 수만 보이고
 * 약점 판정·균형 판정·루틴 후보·회복 지도에는 들어가지 않는다. 밀기/당기기는 종목 정보의 "동작" 표시에만 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class NeckExerciseApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String me;

    @BeforeEach
    void setUp() {
        me = "Bearer " + authService.signUp("neck" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hunter2hunter2", "민석").tokens().accessToken();
    }

    @Test
    @DisplayName("부위 그리드에 목(13종)이 있고, 계열은 넥 컬·넥 익스텐션·넥 사이드 벤드·넥 트위스트·넥 브릿지")
    void neckCategoryAndGroups() throws Exception {
        String categories = body(get("/api/v1/exercises/categories"));
        assertThat(categories).contains("\"category\":\"NECK\",\"label\":\"목\",\"count\":13");

        String groups = body(get("/api/v1/exercises/groups").param("category", "NECK"));
        List<Map<String, Object>> rows = JsonPath.read(groups, "$");
        assertThat(rows).extracting(row -> row.get("groupName") + ":" + row.get("count"))
                .containsExactlyInAnyOrder("넥 브릿지:6", "넥 컬:2", "넥 익스텐션:2", "넥 사이드 벤드:2", "넥 트위스트:1");
    }

    @Test
    @DisplayName("종목 값 — 표시 부위 NECK, 주동근 neck, 기구 맨몸, 기록 방식은 변형마다, 동작은 앞쪽 밀기·뒤쪽 당기기·옆·회전 없음")
    void exerciseValues() throws Exception {
        assertExercise("넥 컬", "PUSH", "BODYWEIGHT_REPS");
        assertExercise("중량 넥 컬", "PUSH", "WEIGHTED_BODYWEIGHT");
        assertExercise("넥 익스텐션", "PULL", "BODYWEIGHT_REPS");
        assertExercise("넥 사이드 벤드", "NONE", "BODYWEIGHT_REPS");
        assertExercise("넥 트위스트", "NONE", "BODYWEIGHT_REPS");
        assertExercise("전면 넥 브릿지 버티기", "PUSH", "TIME");
        assertExercise("중량 후면 넥 브릿지", "PULL", "WEIGHTED_BODYWEIGHT");
    }

    @Test
    @DisplayName("분석 — 목 세트는 '판정 제외 · 참고'에만, 약점 판정(상위 6)과 밀기/당기기 균형에는 들어가지 않는다")
    void analysisDisplayOnly() throws Exception {
        LocalDate day = LocalDate.now(clock).minusDays(1);
        long session = openDay(day);
        addSets(session, idOf("넥 컬"), "\"reps\":15", 6);           // 밀기 표시
        addSets(session, idOf("넥 익스텐션"), "\"reps\":15", 3);     // 당기기 표시

        String volume = body(get("/api/v1/analysis/muscle-volume").param("weeks", "1")
                .param("referenceDate", day.toString()).header(HttpHeaders.AUTHORIZATION, me));
        assertThat((List<String>) JsonPath.read(volume, "$.displayOnly[*].key")).contains("NECK");
        assertThat(((Number) ((List<?>) JsonPath.read(volume, "$.displayOnly[?(@.key == 'NECK')].totalSets")).get(0))
                .intValue()).isEqualTo(9);
        assertThat((List<Number>) JsonPath.read(volume, "$.tiers[*].weeklySets"))
                .allSatisfy(sets -> assertThat(sets.doubleValue()).isZero());

        mvc.perform(get("/api/v1/analysis/balance").param("weeks", "1").param("referenceDate", day.toString())
                        .header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs[0].key").value("PUSH_PULL"))
                .andExpect(jsonPath("$.pairs[0].left.weeklySets").value(0.0))
                .andExpect(jsonPath("$.pairs[0].right.weeklySets").value(0.0));
    }

    @Test
    @DisplayName("회복 상태에는 목이 없다(회복 지도 그림에 자리가 없다), 오늘 루틴 후보에도 목 종목이 없다")
    void notInRecoveryOrRoutine() throws Exception {
        long session = openDay(LocalDate.now(clock).minusDays(3));
        long neckCurl = idOf("넥 컬");
        addSets(session, neckCurl, "\"reps\":15", 3);
        // 즐겨찾기해도 루틴 후보가 되지 않는다
        mvc.perform(put("/api/v1/users/me/favorite-exercises/" + neckCurl).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/analysis/recovery").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muscles[*].key", not(hasItem("NECK"))));

        List<Long> neckIds = jdbc.queryForList("select id from exercises where primary_muscle = 'neck'", Long.class);
        assertThat(neckIds).hasSize(13);
        String routine = mvc.perform(post("/api/v1/routines/today").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> items = JsonPath.read(routine, "$.items[*].exerciseId");
        assertThat(items).isNotEmpty().noneMatch(id -> neckIds.contains(id.longValue()));
    }

    @Test
    @DisplayName("내 종목을 목에 만들면 주동근 neck, 표시 부위 NECK, 동작 없음 — 목 칸 수에 들어간다")
    void customExerciseInNeck() throws Exception {
        mvc.perform(post("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameKo\":\"넥 하네스 컬\",\"category\":\"NECK\",\"equipment\":\"BODYWEIGHT\","
                                + "\"measureType\":\"WEIGHTED_BODYWEIGHT\",\"groupName\":\"넥 컬\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryMuscle").value("neck"))
                .andExpect(jsonPath("$.bodyPart").value("NECK"))
                .andExpect(jsonPath("$.pushPull").value("NONE"))
                .andExpect(jsonPath("$.groupName").value("넥 컬"));
        assertThat(body(get("/api/v1/exercises/categories").header(HttpHeaders.AUTHORIZATION, me)))
                .contains("\"category\":\"NECK\",\"label\":\"목\",\"count\":14");
    }

    // ── 도우미

    private void assertExercise(String name, String pushPull, String measureType) throws Exception {
        mvc.perform(get("/api/v1/exercises/" + idOf(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyPart").value("NECK"))
                .andExpect(jsonPath("$.primaryMuscle").value("neck"))
                .andExpect(jsonPath("$.equipment").value("BODYWEIGHT"))
                .andExpect(jsonPath("$.pushPull").value(pushPull))
                .andExpect(jsonPath("$.measureType").value(measureType));
    }

    private long idOf(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = ?", Long.class, name);
    }

    private long openDay(LocalDate day) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addSets(long session, long exerciseId, String values, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId + ","
                                    + values + "}"))
                    .andExpect(status().isCreated());
        }
    }

    private String body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
