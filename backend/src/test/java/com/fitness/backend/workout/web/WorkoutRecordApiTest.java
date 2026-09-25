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
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 기록 — 세션 생성·세트 저장·종료·조회. 명세 6.2~6.7.
 *
 * <p>종목은 시드에서 이름으로 찾는다. ID를 못박으면 시드 순서가 바뀔 때 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutRecordApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private String bearer;

    /** WEIGHT_REPS — 중량과 횟수가 모두 필요하다 */
    private long benchPress;

    /** TIME — 수행 시간만 받는다 */
    private long plank;

    @BeforeEach
    void setUp() throws Exception {
        bearer = signUp();
        benchPress = exerciseIdOf("바벨 벤치프레스");
        plank = exerciseIdOf("플랭크");
    }

    // ── 세션 생성 (6.2)

    @Test
    @DisplayName("세션을 만들면 DRAFT로 시작하고 세트는 비어 있다")
    void createSessionStartsAsDraft() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\",\"source\":\"LIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.source").value("LIVE"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.durationSec").doesNotExist())
                .andExpect(jsonPath("$.exercises").isEmpty())
                // 10월 routines 전까지는 항상 null이고 컬럼만 채워둔다(명세 6.2).
                .andExpect(jsonPath("$.routineId").doesNotExist());
    }

    @Test
    @DisplayName("진행 중인 세션이 있으면 새로 시작할 수 없다 — 이어쓰기로 유도한다")
    void liveSessionIsSingleton() throws Exception {
        startLiveSession();

        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\",\"source\":\"LIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_SESSION_EXISTS"));
    }

    @Test
    @DisplayName("진행 중 기록은 오늘 날짜로만 만든다")
    void liveSessionMustBeToday() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().minusDays(1) + "\",\"source\":\"LIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("미래 날짜로는 기록할 수 없다 — 미리 짜두는 운동은 계획이지 기록이 아니다")
    void futureDateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().plusDays(1) + "\",\"source\":\"BACKFILL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("지난 운동은 BACKFILL로 과거 날짜에 기록한다")
    void backfillAcceptsPastDate() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().minusDays(3) + "\",\"source\":\"BACKFILL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("BACKFILL"));
    }

    // ── 세트 저장 (6.4)

    @Test
    @DisplayName("세트를 저장하면 같은 종목 안에서 순번이 자동으로 붙는다")
    void setNoIsAssignedPerExercise() throws Exception {
        long sessionId = startLiveSession();

        postSet(sessionId, weightSet(benchPress, "70.0", 10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.exerciseName").value("바벨 벤치프레스"));

        postSet(sessionId, weightSet(benchPress, "70.0", 9))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(2));

        // 다른 종목은 다시 1번부터다.
        postSet(sessionId, timeSet(plank, 60))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(1));
    }

    @Test
    @DisplayName("같은 clientSetId로 다시 보내면 저장하지 않고 기존 세트를 그대로 준다")
    void sameClientSetIdIsIdempotent() throws Exception {
        long sessionId = startLiveSession();
        String clientSetId = UUID.randomUUID().toString();
        String body = "{\"clientSetId\":\"" + clientSetId + "\",\"exerciseId\":" + benchPress
                + ",\"weightKg\":70.0,\"reps\":10}";

        postSet(sessionId, body).andExpect(status().isCreated());

        // 값을 바꿔 보내도 기존 값이 유지된다 — 이 키는 "같은 완료 체크 한 번"을 뜻한다.
        postSet(sessionId, "{\"clientSetId\":\"" + clientSetId + "\",\"exerciseId\":" + benchPress
                + ",\"weightKg\":100.0,\"reps\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(70.0))
                .andExpect(jsonPath("$.reps").value(10));

        // 세트는 하나뿐이다. 중복 세트는 볼륨 과대 집계로 이어진다.
        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.exercises[0].sets.length()").value(1));
    }

    @Test
    @DisplayName("종목이 요구하는 값이 없으면 저장하지 않는다")
    void measureInputIsValidated() throws Exception {
        long sessionId = startLiveSession();

        // WEIGHT_REPS인데 횟수가 없다.
        postSet(sessionId, "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + benchPress
                + ",\"weightKg\":70.0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEASURE_INPUT"));

        // TIME인데 수행 시간이 없다.
        postSet(sessionId, "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + plank + ",\"reps\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEASURE_INPUT"));
    }

    @Test
    @DisplayName("측정 유형이 쓰지 않는 값은 담지 않는다 — 맨몸 종목에 중량이 실리면 볼륨이 부푼다")
    void unusedMeasureFieldsAreDropped() throws Exception {
        long sessionId = startLiveSession();

        postSet(sessionId, "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + plank
                + ",\"durationSec\":60,\"weightKg\":50.0,\"reps\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSec").value(60))
                .andExpect(jsonPath("$.weightKg").doesNotExist())
                .andExpect(jsonPath("$.reps").doesNotExist());
    }

    @Test
    @DisplayName("없는 종목으로는 세트를 저장할 수 없다")
    void unknownExerciseIsRejected() throws Exception {
        long sessionId = startLiveSession();

        postSet(sessionId, weightSet(999_999L, "70.0", 10))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("첫 세트가 저장되면 startedAt이 채워진다 — 그 전에는 화면이 경과 시간을 띄우지 않는다")
    void firstSetFillsStartedAt() throws Exception {
        long sessionId = startLiveSession();
        postSet(sessionId, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.startedAt").exists())
                // 진행 중에는 서버가 시간을 산출하지 않는다(명세 6.3).
                .andExpect(jsonPath("$.durationSec").doesNotExist());
    }

    // ── 종료 (6.3)

    @Test
    @DisplayName("종료하면 DONE이 되고 운동 시간이 산출된다")
    void completeCalculatesDuration() throws Exception {
        long sessionId = startLiveSession();
        postSet(sessionId, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.endedAt").exists())
                .andExpect(jsonPath("$.durationSec").isNumber())
                .andExpect(jsonPath("$.effectiveDurationSec").isNumber());
    }

    @Test
    @DisplayName("세트가 없으면 종료할 수 없다 — 삭제하도록 안내한다")
    void emptySessionCannotComplete() throws Exception {
        long sessionId = startLiveSession();

        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                // Spring 7에서 isUnprocessableEntity()가 deprecated라 코드로 직접 본다.
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("EMPTY_SESSION"));
    }

    @Test
    @DisplayName("이미 종료한 세션은 다시 종료할 수 없다")
    void completeIsNotRepeatable() throws Exception {
        long sessionId = startLiveSession();
        postSet(sessionId, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_COMPLETED"));
    }

    @Test
    @DisplayName("BACKFILL 세션은 운동 시간을 산출하지 않는다 — 저장 시각이 실제 운동 시각이 아니다")
    void backfillHasNoDuration() throws Exception {
        long sessionId = createSession(LocalDate.now().minusDays(2), "BACKFILL");
        postSet(sessionId, weightSet(benchPress, "60.0", 12)).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.durationSec").doesNotExist())
                .andExpect(jsonPath("$.effectiveDurationSec").doesNotExist());
    }

    // ── 조회 (6.5, 6.7)

    @Test
    @DisplayName("세션 상세는 종목별로 묶고 수행 순서대로 준다")
    void detailGroupsByExerciseInPerformedOrder() throws Exception {
        long sessionId = startLiveSession();
        postSet(sessionId, timeSet(plank, 60)).andExpect(status().isCreated());
        postSet(sessionId, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());
        postSet(sessionId, timeSet(plank, 45)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(2))
                // 먼저 수행한 플랭크가 앞이다.
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("플랭크"))
                .andExpect(jsonPath("$.exercises[0].measureType").value("TIME"))
                .andExpect(jsonPath("$.exercises[0].sets.length()").value(2))
                .andExpect(jsonPath("$.exercises[1].exerciseName").value("바벨 벤치프레스"));
    }

    @Test
    @DisplayName("진행 중 세션을 이어쓸 수 있고, 없으면 204다")
    void currentSessionIsResumable() throws Exception {
        mvc.perform(get("/api/v1/workout-sessions/current").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        long sessionId = startLiveSession();

        mvc.perform(get("/api/v1/workout-sessions/current").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("남의 세션은 볼 수 없다 — 있는지조차 드러나지 않는다")
    void othersSessionIsNotFound() throws Exception {
        long sessionId = startLiveSession();
        String otherUser = signUp();

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, otherUser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("기록은 로그인이 필요하다")
    void recordingRequiresAuth() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private String signUp() {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private long startLiveSession() throws Exception {
        return createSession(LocalDate.now(), "LIVE");
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

    private org.springframework.test.web.servlet.ResultActions postSet(long sessionId, String body) throws Exception {
        return mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/sets")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String weightSet(long exerciseId, String weightKg, int reps) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"weightKg\":" + weightKg + ",\"reps\":" + reps + "}";
    }

    private String timeSet(long exerciseId, int durationSec) {
        return "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId
                + ",\"durationSec\":" + durationSec + "}";
    }

    /**
     * 검색은 부분 일치라 "바벨 벤치프레스"에 "디클라인 바벨 벤치프레스"까지 걸린다.
     * 이름이 정확히 같은 종목을 골라낸다.
     */
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
