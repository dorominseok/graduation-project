package com.fitness.backend.exercise.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 종목 조회·즐겨찾기. 명세 5.2~5.4.
 *
 * <p>종목 데이터는 {@code R__seed_exercises.sql}로 들어간 109종을 그대로 쓴다.
 * 시드가 바뀌어도 깨지지 않도록, 개수를 못박는 대신 관계와 형태를 검사한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseApiTest {

    /** 시드의 "바벨 벤치프레스". WEIGHT_REPS · CHEST · BARBELL. */
    private static final long BENCH_PRESS = 5L;

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;

    @BeforeEach
    void setUp() {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    // ── 목록 (5.2)

    @Test
    @DisplayName("토큰 없이도 종목 목록을 볼 수 있다 — 대신 isFavorite 키가 없다")
    void listIsPublicWithoutFavoriteFlag() throws Exception {
        mvc.perform(get("/api/v1/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].nameKo").exists())
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                // 비인증에서 false를 주면 "즐겨찾기가 아니다"로 읽혀 로그인 여부와 구분되지 않는다.
                .andExpect(jsonPath("$.content[0].isFavorite").doesNotExist());
    }

    @Test
    @DisplayName("토큰이 있으면 같은 목록에 isFavorite가 붙는다")
    void listIncludesFavoriteFlagWhenAuthenticated() throws Exception {
        mvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].isFavorite").exists())
                .andExpect(jsonPath("$.content[0].isFavorite").value(false));
    }

    @Test
    @DisplayName("q는 한글·영문 명칭 부분 일치로 찾는다")
    void searchesByName() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("q", "벤치프레스"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nameKo").value(org.hamcrest.Matchers.containsString("벤치프레스")))
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        mvc.perform(get("/api/v1/exercises").param("q", "bench press"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("부위별 종목 수는 페이지 envelope의 totalElements로 얻는다 (목업 부위 그리드)")
    void filtersByBodyPart() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("bodyPart", "CHEST").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.content[*].bodyPart")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("CHEST"))));
    }

    @Test
    @DisplayName("기구와 측정 유형으로도 거른다")
    void filtersByEquipmentAndMeasureType() throws Exception {
        mvc.perform(get("/api/v1/exercises")
                        .param("equipment", "BARBELL")
                        .param("measureType", "WEIGHT_REPS")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].equipment")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("BARBELL"))))
                .andExpect(jsonPath("$.content[*].measureType")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("WEIGHT_REPS"))));
    }

    // ── 단건 (5.3)

    @Test
    @DisplayName("단건 조회는 판정 근거 필드까지 함께 준다")
    void detailIncludesAnalysisFields() throws Exception {
        mvc.perform(get("/api/v1/exercises/{id}", BENCH_PRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BENCH_PRESS))
                .andExpect(jsonPath("$.bodyPart").value("CHEST"))
                // 판정 부위 9종 산출의 근거다(명세 8.1). bodyPart 6종과 다른 축이다.
                .andExpect(jsonPath("$.primaryMuscle").value("chest"))
                .andExpect(jsonPath("$.pushPull").value("PUSH"))
                .andExpect(jsonPath("$.measureType").value("WEIGHT_REPS"));
    }

    @Test
    @DisplayName("없는 종목은 404다")
    void detailNotFound() throws Exception {
        mvc.perform(get("/api/v1/exercises/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ── 즐겨찾기 (5.4)

    @Test
    @DisplayName("별표를 누르면 isFavorite가 true가 되고, 해제하면 돌아온다")
    void togglesFavorite() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/exercises/{id}", BENCH_PRESS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.isFavorite").value(true));

        mvc.perform(delete("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/exercises/{id}", BENCH_PRESS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.isFavorite").value(false));
    }

    @Test
    @DisplayName("별표는 멱등이다 — 연타해도 오류가 아니다")
    void favoriteIsIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS)
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNoContent());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS)
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("favorite=true는 별표한 종목만 준다")
    void filtersFavoriteOnly() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("favorite", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mvc.perform(put("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS)
                .header(HttpHeaders.AUTHORIZATION, bearer));

        mvc.perform(get("/api/v1/exercises").param("favorite", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(BENCH_PRESS))
                .andExpect(jsonPath("$.content[0].isFavorite").value(true));
    }

    @Test
    @DisplayName("비인증 요청의 favorite=true는 무시한다 — 기준이 될 사용자가 없다")
    void favoriteFilterIgnoredWhenAnonymous() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("없는 종목에 별표를 누르면 404다 — 해제도 마찬가지")
    void favoriteOnMissingExercise() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorite-exercises/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(delete("/api/v1/users/me/favorite-exercises/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("즐겨찾기는 로그인이 필요하다")
    void favoriteRequiresAuth() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorite-exercises/{id}", BENCH_PRESS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
