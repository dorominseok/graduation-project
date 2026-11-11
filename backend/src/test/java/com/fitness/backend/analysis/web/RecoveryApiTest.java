package com.fitness.backend.analysis.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.JsonPathResultMatchers;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회복 상태 API. 4단계 경계는 {@code RecoveryEvaluationTest}가 덮고, 여기서는 기록이 판정에 제대로
 * 들어가는지(날짜·워밍업·사용자·4주 범위)와 응답 모양을 본다. 루틴 제외의 경우 나누기는 {@code RoutineExclusionTest}가 덮는다. 설정은 기본값(회복 2일, 많이 함 6세트).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecoveryApiTest {

    private static final String[] ORDER = {
            "CHEST", "BACK", "DELT_FRONT", "DELT_REAR", "TRICEPS", "BICEPS",
            "QUADS", "POSTERIOR", "CORE", "CALVES", "FOREARMS"};

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired Clock clock;

    private String bearer;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        bearer = signUp();
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("기록이 없으면 11개 부위가 모두 회복됨으로 순서대로 온다")
    void noRecords() throws Exception {
        recovery()
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.recoveryDays").value(2))
                .andExpect(jsonPath("$.heavySets").value(6))
                .andExpect(jsonPath("$.muscles", hasSize(11)))
                .andExpect(jsonPath("$.muscles[*].key", contains(ORDER)))
                .andExpect(jsonPath("$.muscles[*].state", everyItem(is("RECOVERED"))))
                .andExpect(jsonPath("$.muscles[*].lastTrainedOn", everyItem(nullValue())))
                .andExpect(jsonPath("$.muscles[*].routineExcluded", everyItem(is(false))))
                .andExpect(jsonPath("$.muscles[*].routineExclusionReason", everyItem(nullValue())))
                .andExpect(jsonPath("$.muscles[9].judged").value(false));
    }

    @Test
    @DisplayName("날짜와 최근 세트로 4단계를 나눈다 — 워밍업·4주 밖·다른 사용자의 기록은 세지 않는다")
    void statesFromRecords() throws Exception {
        sets(bearer, today.minusDays(1), exerciseIdOf("바벨 벤치프레스"), 6, false);   // 가슴: 어제 6세트
        sets(bearer, today, exerciseIdOf("바벨 컬"), 3, false);                         // 이두: 오늘 3세트
        sets(bearer, today, exerciseIdOf("바벨 컬"), 4, true);                          //       + 워밍업 4
        sets(bearer, today.minusDays(2), exerciseIdOf("친업"), 10, false);              // 등: 그제
        sets(bearer, today.minusDays(3), exerciseIdOf("루마니안 데드리프트"), 5, false); // 뒤허벅지: 사흘 전
        sets(bearer, today.minusDays(28), exerciseIdOf("바벨 스쿼트"), 5, false);        // 앞허벅지: 4주 밖
        sets(bearer, today, exerciseIdOf("바벨 리어델트 로우"), 2, false);                // 어깨 후면: 오늘

        String other = signUp();
        sets(other, today, exerciseIdOf("루마니안 데드리프트"), 9, false);

        recovery()
                .andExpect(muscle("CHEST", "state").value("HIGH"))
                .andExpect(muscle("CHEST", "recentSets").value(6))
                .andExpect(muscle("CHEST", "daysSince").value(1))
                .andExpect(muscle("CHEST", "lastTrainedOn").value(today.minusDays(1).toString()))
                .andExpect(muscle("BICEPS", "state").value("MEDIUM"))
                .andExpect(muscle("BICEPS", "recentSets").value(3))
                .andExpect(muscle("BACK", "state").value("LOW"))
                .andExpect(muscle("BACK", "recentSets").value(0))
                .andExpect(muscle("POSTERIOR", "state").value("RECOVERED"))
                .andExpect(muscle("POSTERIOR", "daysSince").value(3))
                .andExpect(muscle("QUADS", "state").value("RECOVERED"))
                .andExpect(muscle("DELT_REAR", "state").value("MEDIUM"))
                .andExpect(muscle("DELT_FRONT", "state").value("RECOVERED"));

        String body = recovery().andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Object quadsLast = ((List<?>) JsonPath.read(body, "$.muscles[?(@.key=='QUADS')].lastTrainedOn")).get(0);
        assertNull(quadsLast, "4주(28일) 밖의 기록은 마지막 운동일로 잡지 않는다");
    }

    @Test
    @DisplayName("오늘 루틴에서 빠지는 부위와 이유 — 회복 중인 부위의 묶음은 회복된 부위까지, 코어·종아리는 그 부위만")
    void routineExclusion() throws Exception {
        sets(bearer, today.minusDays(1), exerciseIdOf("바벨 벤치프레스"), 4, false);   // 가슴: 어제
        sets(bearer, today.minusDays(2), exerciseIdOf("케이블 푸시다운"), 3, false);    // 삼두: 그제(거의 회복)
        sets(bearer, today.minusDays(2), exerciseIdOf("친업"), 4, false);              // 등: 그제 — 당기기는 회복 중인 부위가 없다
        sets(bearer, today, exerciseIdOf("케이블 크런치"), 3, false);                    // 코어: 오늘
        sets(bearer, today, exerciseIdOf("스탠딩 카프레이즈"), 3, false);                // 종아리: 오늘 — 루틴이 고르지 않는 부위

        recovery()
                .andExpect(muscle("CHEST", "routineExcluded").value(true))
                .andExpect(muscle("CHEST", "routineExclusionReason").value("SELF_RECOVERING"))
                .andExpect(muscle("CHEST", "routineExcludedBy", hasSize(0)))
                // 삼두는 회복 기간이 지났고 어깨 전·측면은 기록이 없지만, 가슴과 같은 밀기 묶음이라 같이 빠진다
                .andExpect(muscle("TRICEPS", "state").value("LOW"))
                .andExpect(muscle("TRICEPS", "routineExcluded").value(true))
                .andExpect(muscle("TRICEPS", "routineExclusionReason").value("BUNDLE_RECOVERING"))
                .andExpect(muscle("TRICEPS", "routineExcludedBy", contains("CHEST")))
                .andExpect(muscle("DELT_FRONT", "state").value("RECOVERED"))
                .andExpect(muscle("DELT_FRONT", "routineExclusionReason").value("BUNDLE_RECOVERING"))
                .andExpect(muscle("DELT_FRONT", "routineExcludedBy", contains("CHEST")))
                .andExpect(muscle("BACK", "state").value("LOW"))
                .andExpect(muscle("BACK", "routineExcluded").value(false))
                .andExpect(muscle("BACK", "routineExclusionReason").value(nullValue()))
                .andExpect(muscle("BICEPS", "routineExcluded").value(false))
                .andExpect(muscle("QUADS", "routineExcluded").value(false))
                .andExpect(muscle("CORE", "routineExclusionReason").value("SELF_RECOVERING"))
                // 종아리는 회복 중이어도 빠진다고 하지 않는다
                .andExpect(muscle("CALVES", "state").value("MEDIUM"))
                .andExpect(muscle("CALVES", "routineExcluded").value(false))
                .andExpect(muscle("CALVES", "routineExclusionReason").value(nullValue()));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/v1/analysis/recovery")).andExpect(status().isUnauthorized());
    }

    private ResultActions recovery() throws Exception {
        return mvc.perform(get("/api/v1/analysis/recovery").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private static JsonPathResultMatchers muscle(String key, String field) {
        return jsonPath("$.muscles[" + Arrays.asList(ORDER).indexOf(key) + "]." + field);
    }

    private static ResultMatcher muscle(String key, String field, org.hamcrest.Matcher<?> matcher) {
        return jsonPath("$.muscles[" + Arrays.asList(ORDER).indexOf(key) + "]." + field, matcher);
    }

    private String signUp() {
        String email = "rc" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private void sets(String auth, LocalDate day, long exerciseId, int count, boolean warmup) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long session = ((Number) JsonPath.read(body, "$.id")).longValue();
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                                    + ",\"weightKg\":40.0,\"reps\":10,\"isWarmup\":" + warmup + "}"))
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
