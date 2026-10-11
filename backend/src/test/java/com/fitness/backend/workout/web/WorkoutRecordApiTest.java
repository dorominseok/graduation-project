package com.fitness.backend.workout.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.workout.domain.WorkoutSet;
import com.fitness.backend.workout.repository.WorkoutSetRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * 운동 기록 — 그날 기록 열기·세트 저장·조회. 명세 6.2~6.5, LOG-39.
 *
 * <p>종목은 시드에서 이름으로 찾는다. ID를 못박으면 시드 순서가 바뀔 때 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutRecordApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired WorkoutSetRepository setRepository;

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

    // ── 그날 기록 열기 (6.2)

    @Test
    @DisplayName("그날 첫 기록은 201로 만들어지고 세트는 비어 있다")
    void openCreatesEmptyDay() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performedOn").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.durationSec").doesNotExist())
                .andExpect(jsonPath("$.exercises").isEmpty())
                // 종료가 없어 상태 필드도 없다
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.source").doesNotExist())
                // 10월 routines 전까지는 항상 null이고 컬럼만 채워둔다(명세 6.2).
                .andExpect(jsonPath("$.routineId").doesNotExist());
    }

    @Test
    @DisplayName("같은 날을 다시 열면 같은 기록이 200으로 온다 — 하루에 기록은 하나다")
    void reopeningGivesSameDay() throws Exception {
        long first = openToday();
        postSet(first, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());

        // 오후에 두 번째로 운동하러 와서 다시 열어도 아침 기록이 그대로 있다
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.exercises[0].sets.length()").value(1));
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘 기록을 연다")
    void omittedDateIsToday() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performedOn").value(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("어제 마지막 세트가 30분 안이면 자정을 넘긴 운동이라 어제 기록을 연다")
    void lateNightWorkoutContinuesYesterday() throws Exception {
        long yesterday = openDay(LocalDate.now().minusDays(1));
        saveSetAt(yesterday, OffsetDateTime.now().minusMinutes(10));

        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yesterday));
    }

    @Test
    @DisplayName("어제 마지막 세트가 30분보다 오래됐으면 오늘 기록을 연다")
    void oldYesterdayDoesNotContinue() throws Exception {
        long yesterday = openDay(LocalDate.now().minusDays(1));
        saveSetAt(yesterday, OffsetDateTime.now().minusMinutes(40));

        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performedOn").value(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("미래 날짜로는 기록할 수 없다 — 미리 짜두는 운동은 계획이지 기록이 아니다")
    void futureDateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("지난 날짜도 연다 — 빠뜨린 기록을 채운다")
    void pastDateOpens() throws Exception {
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().minusDays(3) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performedOn").value(LocalDate.now().minusDays(3).toString()));
    }

    // ── 세트 저장 (6.4)

    @Test
    @DisplayName("세트를 저장하면 같은 종목 안에서 순번이 자동으로 붙는다")
    void setNoIsAssignedPerExercise() throws Exception {
        long sessionId = openToday();

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
        long sessionId = openToday();
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
        long sessionId = openToday();

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
        long sessionId = openToday();

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
        long sessionId = openToday();

        postSet(sessionId, weightSet(999_999L, "70.0", 10))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("체크하는 대로 시작 시각과 운동 시간이 나온다 — 종료를 기다리지 않는다")
    void timesFollowCheckedSets() throws Exception {
        long sessionId = openToday();
        postSet(sessionId, weightSet(benchPress, "70.0", 10)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.endedAt").exists())
                // 세트 하나는 마지막 세트 보정 90초다
                .andExpect(jsonPath("$.durationSec").value(90))
                .andExpect(jsonPath("$.effectiveDurationSec").value(90));
    }

    @Test
    @DisplayName("지난 날짜에 오늘 채운 세트는 운동 시간에 들어가지 않는다 — 저장 시각이 실제 운동 시각이 아니다")
    void backfilledSetsHaveNoDuration() throws Exception {
        long sessionId = openDay(LocalDate.now().minusDays(2));
        postSet(sessionId, weightSet(benchPress, "60.0", 12)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.durationSec").doesNotExist())
                .andExpect(jsonPath("$.effectiveDurationSec").doesNotExist());
    }

    // ── 수정 (6.9)

    @Test
    @DisplayName("기록이 있는 날짜로는 옮길 수 없다 — 하루에 기록은 하나다")
    void cannotMoveOntoRecordedDay() throws Exception {
        openDay(LocalDate.now().minusDays(1));
        long twoDaysAgo = openDay(LocalDate.now().minusDays(2));

        mvc.perform(patch("/api/v1/workout-sessions/" + twoDaysAgo)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_ALREADY_RECORDED"));

        // 빈 날짜로는 옮긴다
        mvc.perform(patch("/api/v1/workout-sessions/" + twoDaysAgo)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now().minusDays(5) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performedOn").value(LocalDate.now().minusDays(5).toString()));
    }

    // ── 조회 (6.5)

    @Test
    @DisplayName("기록 상세는 종목별로 묶고 수행 순서대로 준다")
    void detailGroupsByExerciseInPerformedOrder() throws Exception {
        long sessionId = openToday();
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
    @DisplayName("남의 기록은 볼 수 없다 — 있는지조차 드러나지 않는다")
    void othersSessionIsNotFound() throws Exception {
        long sessionId = openToday();
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

    @Test
    @DisplayName("세트 번호는 클라이언트가 정할 수 없다 — 보내도 서버가 매긴 순번이 나온다")
    void setNoIsServerAssigned() throws Exception {
        long sessionId = openToday();

        // 요청에 setNo를 실어도 무시된다. 받아주면 같은 번호를 두 번 보내
        // 1세트가 두 줄이 되고, 화면이 세트를 셀 근거가 없어진다.
        postSet(sessionId, "{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":"
                + benchPress + ",\"weightKg\":60.0,\"reps\":12,\"setNo\":9}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(1));

        postSet(sessionId, weightSet(benchPress, "70.0", 10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(2));
    }

    @Test
    @DisplayName("루틴 번호는 아직 받지 않는다 — routines 테이블이 없어 참조 없는 값이 남는다")
    void routineIdIsNotAccepted() throws Exception {
        // 보내도 저장되지 않는다. 응답의 routineId는 컬럼 값 그대로 null이다.
        mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now() + "\",\"routineId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routineId").isEmpty());
    }

    private long openToday() throws Exception {
        return openDay(LocalDate.now());
    }

    private long openDay(LocalDate performedOn) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + performedOn + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return firstId(body);
    }

    /** 저장 시각을 정해 세트를 넣는다. API로 넣으면 저장 시각이 늘 지금이다 */
    private void saveSetAt(long sessionId, OffsetDateTime recordedAt) {
        setRepository.saveAndFlush(WorkoutSet.record(sessionId, benchPress, (short) 1, new BigDecimal("60.00"),
                (short) 10, null, false, recordedAt, UUID.randomUUID()));
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
