package com.fitness.backend.exercise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.exercise.domain.BrowseCategory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    @DisplayName("기본 정렬은 가나다순이다 — DB 기본 콜레이션이 한글을 글자 수 순으로 놓는 것을 V5가 고친다")
    void listIsSortedByKoreanName() throws Exception {
        String body = mvc.perform(get("/api/v1/exercises").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\"nameKo\":\"([^\"]+)\"").matcher(body);
        while (m.find()) {
            names.add(m.group(1));
        }
        // String 기본 비교가 코드포인트순이고, 한글 음절은 그 순서가 곧 가나다순이다
        assertThat(names).isNotEmpty().isSorted();
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

    // ── 3단 탐색 (5.6·5.7, LOG-24)

    /** 응답 본문에서 {@code "이름":숫자} 꼴의 값을 모두 더한다. */
    private static long sumOf(String field, String body) {
        Matcher m = Pattern.compile("\"" + field + "\":([0-9]+)").matcher(body);
        long sum = 0;
        while (m.find()) {
            sum += Long.parseLong(m.group(1));
        }
        return sum;
    }

    private String getBody(String url, String... params) throws Exception {
        var request = get(url);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("분류가 시드 종목을 하나도 빠뜨리거나 겹치지 않고 나눠 갖는다")
    void categoriesPartitionEveryExercise() throws Exception {
        String categories = mvc.perform(get("/api/v1/exercises/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // enum 상수가 하나도 빠지지 않는다
                .andExpect(jsonPath("$.length()").value(BrowseCategory.values().length))
                .andExpect(jsonPath("$[0].label").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 합이 전체와 같으면 누락도 중복도 없다는 뜻이다.
        assertThat(sumOf("count", categories))
                .isEqualTo(sumOf("totalElements", getBody("/api/v1/exercises", "size", "1")));
    }

    @Test
    @DisplayName("계열이 그 분류의 종목을 남김없이 나눠 갖는다 — group_name이 빈 종목이 없다")
    void groupsPartitionTheCategory() throws Exception {
        for (BrowseCategory category : BrowseCategory.values()) {
            String groups = getBody("/api/v1/exercises/groups", "category", category.name());
            String inCategory = getBody("/api/v1/exercises", "category", category.name(), "size", "1");

            assertThat(sumOf("count", groups))
                    .as("분류 %s의 계열 합계", category)
                    .isEqualTo(sumOf("totalElements", inCategory));
        }
    }

    @Test
    @DisplayName("계열로 좁히면 그 계열 종목만 나온다")
    void groupFilterNarrowsToOneGroup() throws Exception {
        String groups = getBody("/api/v1/exercises/groups", "category", "CHEST");
        Matcher first = Pattern.compile("\"groupName\":\"([^\"]+)\"").matcher(groups);
        assertThat(first.find()).isTrue();
        String groupName = first.group(1);

        mvc.perform(get("/api/v1/exercises")
                        .param("category", "CHEST")
                        .param("group", groupName)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].groupName")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(groupName))));
    }

    @Test
    @DisplayName("계열 목록은 대표 종목의 id와 영문명을 함께 준다 — 카드 그림 파일명이 영문명 슬러그다")
    void groupsCarryRepresentative() throws Exception {
        mvc.perform(get("/api/v1/exercises/groups").param("category", "CHEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].groupName").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))))
                .andExpect(jsonPath("$[*].representativeId").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                // 영문명이 비면 그림이 통째로 안 뜬다. 계열 전부를 확인한다.
                .andExpect(jsonPath("$[*].representativeNameEn").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))));
    }

    @Test
    @DisplayName("종목 목록 응답에 계열 이름이 담긴다")
    void listExposesGroupName() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].groupName").isNotEmpty());
    }

    @Test
    @DisplayName("계열 목록은 분류를 반드시 받아야 한다")
    void groupsRequireCategory() throws Exception {
        mvc.perform(get("/api/v1/exercises/groups"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("없는 분류를 보내면 400이다 — 조용히 빈 목록을 주지 않는다")
    void unknownCategoryIsRejected() throws Exception {
        mvc.perform(get("/api/v1/exercises/groups").param("category", "NECK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/exercises").param("category", "NECK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ── 판정 부위로 조회 (LOG-29) — 홈의 약점에서 그 부위 종목으로 바로 간다

    @Test
    @DisplayName("판정 부위로 거르면 어깨 뒤쪽만 나온다 — 탐색 분류의 어깨는 앞뒤가 섞여 있다")
    void muscleGroupSplitsShoulders() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("muscleGroup", "DELT_REAR").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].deltRegion")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REAR"))));
    }

    @Test
    @DisplayName("뒤허벅지·둔근은 탐색 분류 둘(하체·엉덩이)에 걸쳐 있어도 한 번에 나온다")
    void muscleGroupSpansCategories() throws Exception {
        mvc.perform(get("/api/v1/exercises").param("muscleGroup", "POSTERIOR").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(7))
                .andExpect(jsonPath("$.content[*].primaryMuscle").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.isOneOf("hamstrings", "glutes"))));
    }

    @Test
    @DisplayName("분류와 판정 부위를 같이 주면 둘 다 맞는 종목만, 겹치지 않으면 빈 목록이다")
    void muscleGroupIntersectsCategory() throws Exception {
        mvc.perform(get("/api/v1/exercises")
                        .param("category", "LEGS").param("muscleGroup", "POSTERIOR").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].primaryMuscle")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("hamstrings"))));

        mvc.perform(get("/api/v1/exercises").param("category", "CHEST").param("muscleGroup", "DELT_REAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("어깨는 탐색에서도 전면 10종·후면 3종으로 나뉜다 — 약점의 '어깨 후면'과 같은 단위다")
    void shoulderCategoriesSplitByDeltRegion() throws Exception {
        String categories = getBody("/api/v1/exercises/categories");
        assertThat(categories).contains("\"category\":\"SHOULDERS_FRONT\",\"label\":\"어깨 전·측면\",\"count\":10");
        assertThat(categories).contains("\"category\":\"SHOULDERS_REAR\",\"label\":\"어깨 후면\",\"count\":3");

        mvc.perform(get("/api/v1/exercises").param("category", "SHOULDERS_REAR").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].deltRegion")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REAR"))));
    }

    @Test
    @DisplayName("어깨 전·측면 분류에 어깨 후면 판정 부위를 같이 주면 겹치는 종목이 없다")
    void conflictingDeltRegionsYieldNothing() throws Exception {
        mvc.perform(get("/api/v1/exercises")
                        .param("category", "SHOULDERS_FRONT").param("muscleGroup", "DELT_REAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mvc.perform(get("/api/v1/exercises")
                        .param("category", "SHOULDERS_REAR").param("muscleGroup", "DELT_REAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }
}
