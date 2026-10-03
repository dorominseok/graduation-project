package com.fitness.backend.analysis.web;

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
 * 분석 API — 부위별 볼륨 판정·균형 판정. 명세 8.2·8.3.
 *
 * <p>구간 분류·배지 결정 자체는 {@code analysis.domain} 단위 테스트가 이미 덮는다.
 * 여기서 보는 것은 <b>배선</b>이다 — 어떤 세트가 집계에 들어가고 빠지는지, 상위·하위
 * 2계층이 명세대로 조립되는지. 판정이 틀리는 실제 경로는 공식이 아니라 집계 조건이다.
 *
 * <p>{@code weeks = 1}을 자주 쓰는 것은 경계값 때문이다. 기본 4주에서 주당 10세트를
 * 만들려면 세트 40개를 넣어야 하는데, 1주로 두면 10개로 같은 경계를 밟는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;
    private long benchPress;      // chest        → CHEST
    private long militaryPress;   // shoulders(F) → DELT_FRONT
    private long facePull;        // shoulders(R) → DELT_REAR
    private long barbellCurl;     // biceps       → BICEPS
    private long calfRaise;       // calves       → 표시 전용

    @BeforeEach
    void setUp() throws Exception {
        String email = "a" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        bearer = "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
        benchPress = exerciseIdOf("바벨 벤치프레스");
        militaryPress = exerciseIdOf("밀리터리 프레스");
        facePull = exerciseIdOf("페이스풀");
        barbellCurl = exerciseIdOf("바벨 컬");
        calfRaise = exerciseIdOf("스탠딩 카프레이즈");
    }

    // ── 부위별 볼륨 (8.2)

    @Test
    @DisplayName("기록이 없어도 상위 6개 부위가 모두 0세트로 내려온다")
    void alwaysReturnsSixTiers() throws Exception {
        volume(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.length()").value(6))
                // 순서는 서버가 정한다 — 화면이 정렬하지 않아도 되게
                .andExpect(jsonPath("$.tiers[0].key").value("CHEST"))
                .andExpect(jsonPath("$.tiers[2].key").value("SHOULDERS"))
                .andExpect(jsonPath("$.tiers[5].key").value("CORE"))
                .andExpect(jsonPath("$.tiers[0].weeklySets").value(0.0))
                // 0세트는 "판정 불가"가 아니라 "부족"이다. 안 한 것도 정보다.
                .andExpect(jsonPath("$.tiers[0].verdict").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.tiers[0].verdictLabel").value("부족"))
                .andExpect(jsonPath("$.displayOnly.length()").value(2))
                .andExpect(jsonPath("$.displayOnly[0].key").value("CALVES"));
    }

    @Test
    @DisplayName("집계 구간은 기준일을 포함해 정확히 weeks × 7일이다")
    void periodIncludesReferenceDate() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        // 28일 구간의 첫날과 그 하루 전
        doneSession(reference.minusDays(27), benchPress, 1);
        doneSession(reference.minusDays(28), benchPress, 1);

        volume(4, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodFrom").value(reference.minusDays(27).toString()))
                .andExpect(jsonPath("$.periodTo").value(reference.toString()))
                // 하루 전 세트는 구간 밖이라 빠진다
                .andExpect(jsonPath("$.tiers[0].totalSets").value(1));
    }

    @Test
    @DisplayName("주당 10세트는 최적이다 — 경계는 최적에 포함한다")
    void tenSetsPerWeekIsOptimal() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, benchPress, 10);

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].key").value("CHEST"))
                .andExpect(jsonPath("$.tiers[0].weeklySets").value(10.0))
                .andExpect(jsonPath("$.tiers[0].totalSets").value(10))
                .andExpect(jsonPath("$.tiers[0].verdict").value("OPTIMAL"));
    }

    @Test
    @DisplayName("워밍업과 진행 중 기록은 볼륨에 세지 않는다")
    void excludesWarmupAndDraft() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);

        long done = backfillSession(reference);
        postSet(done, weightSet(benchPress)).andExpect(status().isCreated());
        postSet(done, weightSet(benchPress)).andExpect(status().isCreated());
        postSet(done, warmupSet(benchPress)).andExpect(status().isCreated());
        complete(done);

        // 종료하지 않은 세션 — 아직 "한 운동"이 아니다
        long draft = backfillSession(reference);
        postSet(draft, weightSet(benchPress)).andExpect(status().isCreated());

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].totalSets").value(2));
    }

    @Test
    @DisplayName("하위가 둘인 상위에는 판정 대신 배지가 붙는다 — 합계가 한쪽 0을 가리지 않게")
    void parentWithTwoChildrenGetsBadgeNotVerdict() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, militaryPress, 10);   // 어깨(앞)만 채운다

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[2].key").value("SHOULDERS"))
                .andExpect(jsonPath("$.tiers[2].hasChildren").value(true))
                // 어깨 합은 10세트라 "최적"이지만 그 라벨을 붙이지 않는다
                .andExpect(jsonPath("$.tiers[2].weeklySets").value(10.0))
                .andExpect(jsonPath("$.tiers[2].verdict").isEmpty())
                .andExpect(jsonPath("$.tiers[2].summaryBadge").value("PARTIAL_INSUFFICIENT"))
                .andExpect(jsonPath("$.tiers[2].summaryBadgeLabel").value("일부 부족"))
                .andExpect(jsonPath("$.tiers[2].children.length()").value(2))
                .andExpect(jsonPath("$.tiers[2].children[0].key").value("DELT_FRONT"))
                .andExpect(jsonPath("$.tiers[2].children[0].verdict").value("OPTIMAL"))
                .andExpect(jsonPath("$.tiers[2].children[1].key").value("DELT_REAR"))
                .andExpect(jsonPath("$.tiers[2].children[1].verdict").value("INSUFFICIENT"));
    }

    @Test
    @DisplayName("하위가 하나면 상위에 판정이 그대로 붙고 배지는 없다")
    void parentWithSingleChildCarriesVerdict() throws Exception {
        volume(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].hasChildren").value(false))
                .andExpect(jsonPath("$.tiers[0].verdict").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.tiers[0].summaryBadge").isEmpty())
                .andExpect(jsonPath("$.tiers[0].children.length()").value(1));
    }

    @Test
    @DisplayName("종아리는 판정하지 않고 세트 수만 내려준다")
    void calvesAreDisplayOnly() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, calfRaise, 3);

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOnly[0].key").value("CALVES"))
                .andExpect(jsonPath("$.displayOnly[0].totalSets").value(3))
                .andExpect(jsonPath("$.displayOnly[0].verdict").doesNotExist())
                // 하체 판정에는 들어가지 않는다 (8.1 — 판정 9종 밖)
                .andExpect(jsonPath("$.tiers[4].key").value("LEGS"))
                .andExpect(jsonPath("$.tiers[4].totalSets").value(0));
    }

    @Test
    @DisplayName("완료 세션이 임계보다 적으면 신뢰도가 낮다고 알린다 — 판정은 그대로 내려준다")
    void lowConfidenceWhenFewSessions() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, benchPress, 10);

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence.level").value("LOW"))
                .andExpect(jsonPath("$.confidence.doneSessionCount").value(1))
                .andExpect(jsonPath("$.confidence.threshold").value(6))
                .andExpect(jsonPath("$.confidence.message").isNotEmpty())
                // 신뢰도가 낮아도 판정 자체는 숨기지 않는다
                .andExpect(jsonPath("$.tiers[0].verdict").value("OPTIMAL"));
    }

    @Test
    @DisplayName("weeks가 범위를 벗어나면 400")
    void rejectsOutOfRangeWeeks() throws Exception {
        volume(0, null).andExpect(status().isBadRequest());
        volume(53, null).andExpect(status().isBadRequest());
    }

    // ── 기록이 4주 미만일 때의 분모 (LOG-31)

    @Test
    @DisplayName("첫 주에 12세트면 주 12세트다 — 28일로 나눠 '3세트 · 부족'이 되지 않는다")
    void newUserIsNotDilutedByEmptyWeeks() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference.minusDays(2), benchPress, 12);

        volume(null, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basisDays").value(7))
                .andExpect(jsonPath("$.basisFrom").value(reference.minusDays(6).toString()))
                .andExpect(jsonPath("$.tiers[0].totalSets").value(12))
                .andExpect(jsonPath("$.tiers[0].weeklySets").value(12.0))
                .andExpect(jsonPath("$.tiers[0].verdict").value("OPTIMAL"));
    }

    @Test
    @DisplayName("기록을 시작한 지 17일이면 17일로 나눈다")
    void basisIsElapsedDaysSinceFirstRecord() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference.minusDays(16), benchPress, 6);
        doneSession(reference, benchPress, 6);

        volume(null, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basisDays").value(17))
                // 12 × 7 ÷ 17 = 4.94
                .andExpect(jsonPath("$.tiers[0].weeklySets").value(4.9));

        balance(null, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basisDays").value(17))
                .andExpect(jsonPath("$.pairs[0].left.weeklySets").value(4.9));
    }

    @Test
    @DisplayName("구간보다 오래 기록했으면 지금까지처럼 28일(÷ 4)로 나눈다 — 쉰 기간도 평균에 들어간다")
    void longHistoryKeepsFullWindow() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        // 구간(28일) 밖의 첫 기록. 이 뒤로 3주를 쉬었다
        doneSession(reference.minusDays(40), barbellCurl, 3);
        doneSession(reference, benchPress, 12);

        volume(null, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basisDays").value(28))
                .andExpect(jsonPath("$.tiers[0].weeklySets").value(3.0))
                .andExpect(jsonPath("$.tiers[0].verdict").value("INSUFFICIENT"));
    }

    // ── 균형 (8.3)

    @Test
    @DisplayName("한쪽만 기록하면 비율을 낼 수 없지만 불균형인 것은 분명하다")
    void oneSidedRecordIsImbalanced() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, benchPress, 6);   // 밀기만

        balance(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs.length()").value(2))
                .andExpect(jsonPath("$.pairs[0].key").value("PUSH_PULL"))
                .andExpect(jsonPath("$.pairs[0].left.key").value("PUSH"))
                .andExpect(jsonPath("$.pairs[0].left.weeklySets").value(6.0))
                .andExpect(jsonPath("$.pairs[0].right.weeklySets").value(0.0))
                .andExpect(jsonPath("$.pairs[0].biggerSide").value("PUSH"))
                .andExpect(jsonPath("$.pairs[0].ratio").isEmpty())
                .andExpect(jsonPath("$.pairs[0].smallerSideZero").value(true))
                .andExpect(jsonPath("$.pairs[0].verdict").value("IMBALANCED"))
                // 근거가 되는 부위를 함께 준다
                .andExpect(jsonPath("$.pairs[0].left.components[0]").value("CHEST"));
    }

    @Test
    @DisplayName("2.0배까지는 정상이다 — 일반인의 밀기:당기기가 원래 1:1이 아니다")
    void twiceIsStillBalanced() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, benchPress, 6);     // 밀기 6
        doneSession(reference, barbellCurl, 3);    // 당기기 3

        balance(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratioThreshold").value(2.0))
                .andExpect(jsonPath("$.pairs[0].ratio").value(2.0))
                .andExpect(jsonPath("$.pairs[0].smallerSideZero").value(false))
                .andExpect(jsonPath("$.pairs[0].verdict").value("BALANCED"))
                .andExpect(jsonPath("$.pairs[0].verdictLabel").value("정상"));
    }

    @Test
    @DisplayName("양쪽 다 0이면 균형이 맞는 게 아니라 판정 불가다")
    void bothSidesZeroIsInsufficientData() throws Exception {
        balance(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs[0].verdict").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.pairs[0].verdictLabel").value("판정 불가"))
                .andExpect(jsonPath("$.pairs[0].ratio").isEmpty())
                .andExpect(jsonPath("$.pairs[0].smallerSideZero").value(false))
                .andExpect(jsonPath("$.pairs[0].biggerSide").isEmpty());
    }

    @Test
    @DisplayName("하체에는 종아리가 들어간다 — 빼면 하체가 과소평가된다")
    void lowerBodyIncludesCalves() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, calfRaise, 4);

        balance(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs[1].key").value("UPPER_LOWER"))
                .andExpect(jsonPath("$.pairs[1].right.key").value("LOWER"))
                .andExpect(jsonPath("$.pairs[1].right.weeklySets").value(4.0))
                .andExpect(jsonPath("$.pairs[1].biggerSide").value("LOWER"));
    }

    @Test
    @DisplayName("어깨 종목을 기록하면 앞뒤 구분이 선 것으로 표시된다")
    void shoulderSplitIsResolved() throws Exception {
        LocalDate reference = LocalDate.now().minusDays(1);
        doneSession(reference, facePull, 3);

        volume(1, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shoulderSplitResolved").value(true))
                .andExpect(jsonPath("$.tiers[2].children[1].key").value("DELT_REAR"))
                .andExpect(jsonPath("$.tiers[2].children[1].totalSets").value(3));
    }

    @Test
    @DisplayName("토큰 없이는 분석을 볼 수 없다")
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/analysis/muscle-volume")).andExpect(status().isUnauthorized());
    }

    // ── 도우미

    private ResultActions volume(Integer weeks, LocalDate referenceDate) throws Exception {
        return mvc.perform(withPeriod(get("/api/v1/analysis/muscle-volume"), weeks, referenceDate));
    }

    private ResultActions balance(Integer weeks, LocalDate referenceDate) throws Exception {
        return mvc.perform(withPeriod(get("/api/v1/analysis/balance"), weeks, referenceDate));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withPeriod(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Integer weeks, LocalDate referenceDate) {

        if (weeks != null) {
            builder = builder.param("weeks", String.valueOf(weeks));
        }
        if (referenceDate != null) {
            builder = builder.param("referenceDate", referenceDate.toString());
        }
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private void doneSession(LocalDate day, long exerciseId, int sets) throws Exception {
        long session = backfillSession(day);
        for (int i = 0; i < sets; i++) {
            postSet(session, weightSet(exerciseId)).andExpect(status().isCreated());
        }
        complete(session);
    }

    private long backfillSession(LocalDate performedOn) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + performedOn + "\",\"source\":\"BACKFILL\"}"))
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

    private String weightSet(long exerciseId) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":40.0,\"reps\":10}";
    }

    private String warmupSet(long exerciseId) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":20.0,\"reps\":10,\"isWarmup\":true}";
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

    private static long firstId(String json) {
        Matcher matcher = Pattern.compile("\"id\":([0-9]+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("id를 찾을 수 없다: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}
