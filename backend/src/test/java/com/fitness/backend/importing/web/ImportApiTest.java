package com.fitness.backend.importing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.importing.BodyCalendarSamples;
import com.fitness.backend.importing.domain.CuratedNames;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 기록 가져오기(「기록가져오기_설계서」 8장 서버 완료 기준). 샘플 두 개의 미리보기, 이름 맞추기 순서, 저장 규칙
 * (이어 붙이기·두 번 넣기·운동 시간·짝 기억·남의 종목·미래 날짜), 분석과 "이어지는 어제" 판정.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class ImportApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper om;
    @Autowired Clock clock;
    @Autowired CuratedNames curatedNames;
    @Autowired ExerciseRepository exerciseRepository;

    private String me;
    private String other;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        me = signUp();
        other = signUp();
        today = LocalDate.now(clock);
    }

    // ── 미리보기

    @Test
    @DisplayName("샘플 두 개 — 날짜 순, N분, 짝 표·이름으로 맞춤, 덤벨 컬은 비슷한 종목, 월 슬라이드는 미등록 운동, 유산소 없음")
    void previewSamples() throws Exception {
        LocalDate d1 = today.minusDays(3);
        LocalDate d2 = today.minusDays(2);
        String body = preview(me, BodyCalendarSamples.first(d1) + "\n" + BodyCalendarSamples.second(d2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value(d1.toString()))
                .andExpect(jsonPath("$.days[0].minutes").value(110))
                .andExpect(jsonPath("$.days[1].minutes").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(statusAndName(body, 0)).containsExactly(
                "월 슬라이드=UNREGISTERED:null",
                "풀업: 맨몸=MATCHED:풀업",
                "랫풀다운: 오버핸드=MATCHED:랫풀다운: 와이드 그립",
                "랫풀다운: 언더핸드=MATCHED:랫풀다운: 언더그립",
                "벤치 프레스: 바벨=MATCHED:바벨 벤치프레스",
                "숄더 프레스: 스탠딩, 원암, 덤벨=MATCHED:원암 덤벨 숄더프레스",
                "덤벨 컬: 번갈아가며, 인클라인=SIMILAR:인클라인 덤벨 컬",
                "트라이셉스 익스텐션: 스탠딩, 덤벨=MATCHED:덤벨 오버헤드 익스텐션",
                "푸시다운=MATCHED:케이블 푸시다운",
                "페이스 풀: 케이블=MATCHED:페이스풀",
                "사이드 레터럴 레이즈: 핀 머신=MATCHED:머신 레터럴 레이즈");
        assertThat(statusAndName(body, 1)).containsExactly(
                "플랭크: 기본=MATCHED:플랭크",
                "풀업: 중량=MATCHED:중량 풀업",
                "벤치 프레스: 바벨=MATCHED:바벨 벤치프레스");

        // 미등록 운동은 만들기 창을 채울 짐작값, 맞춘 종목은 짐작값 없음
        assertThat((String) JsonPath.read(body, "$.days[0].exercises[0].guess.equipment")).isEqualTo("BODYWEIGHT");
        assertThat((String) JsonPath.read(body, "$.days[0].exercises[0].guess.measureType"))
                .isEqualTo("BODYWEIGHT_REPS");
        assertThat((Object) JsonPath.read(body, "$.days[0].exercises[1].guess")).isNull();
        // 세트는 글 그대로(맨몸 kg 포함), 담을 수 있는 기록 방식
        assertThat(((Number) JsonPath.read(body, "$.days[0].exercises[1].sets[0].weightKg")).intValue()).isEqualTo(81);
        assertThat((List<String>) JsonPath.read(body, "$.days[0].exercises[1].allowedMeasureTypes"))
                .containsExactly("WEIGHT_REPS", "BODYWEIGHT_REPS", "WEIGHTED_BODYWEIGHT");
        assertThat((List<String>) JsonPath.read(body, "$.days[1].exercises[0].allowedMeasureTypes"))
                .containsExactly("TIME");
        assertThat((List<String>) JsonPath.read(body, "$.days[1].exercises[2].sets[*].kind"))
                .containsExactly("WARMUP", "DROP", "SET", "SET");
    }

    @Test
    @DisplayName("짝 표의 대상은 모두 기본 종목에 있다")
    void curatedTargetsExist() {
        assertThat(curatedNames.rows()).hasSize(17);
        assertThat(curatedNames.rows().values())
                .allSatisfy(target -> assertThat(exerciseRepository.existsByOwnerIdIsNullAndNameKo(target))
                        .as(target).isTrue());
    }

    @Test
    @DisplayName("이름이 똑같음 — 원래 이름 전체, 또는 수식어가 기본·스탠딩·맨몸뿐이면 : 앞 이름. 띄어쓰기는 무시")
    void sameName() throws Exception {
        assertThat(matchOf(me, "랫풀다운:와이드그립", "1세트 40kg 10회")).isEqualTo("MATCHED:랫풀다운: 와이드 그립");
        assertThat(matchOf(me, "푸시업: 맨몸", "1세트 20회")).isEqualTo("MATCHED:푸시업");
        assertThat(matchOf(me, "덤벨 컬: 스탠딩", "1세트 10kg 12회")).isEqualTo("MATCHED:덤벨 컬");
    }

    @Test
    @DisplayName("내 종목도 이름이 똑같으면 맞는다. 남의 내 종목은 후보가 아니다")
    void customByName() throws Exception {
        assertThat(matchOf(me, "월 슬라이드", "1세트 30회")).isEqualTo("UNREGISTERED:null");
        createCustom(other, "월 슬라이드", "BODYWEIGHT", "BODYWEIGHT_REPS");
        assertThat(matchOf(me, "월 슬라이드", "1세트 30회")).isEqualTo("UNREGISTERED:null");

        createCustom(me, "월 슬라이드", "BODYWEIGHT", "BODYWEIGHT_REPS");
        String body = preview(me, BodyCalendarSamples.one(today.minusDays(1), "월 슬라이드", "1세트 30회"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(body, "$.days[0].exercises[0].status")).isEqualTo("MATCHED");
        assertThat((Boolean) JsonPath.read(body, "$.days[0].exercises[0].exercise.mine")).isTrue();
    }

    @Test
    @DisplayName("계열 + 수식어 → 비슷한 종목. 수식어가 많이 든 종목, 비기면 흔한 종목")
    void similarByGroup() throws Exception {
        // 얼터네이트 덤벨 컬과 인클라인 덤벨 컬이 수식어 하나씩으로 비긴다 → 흔한 쪽
        assertThat(matchOf(me, "덤벨 컬: 번갈아가며, 인클라인", "1세트 10kg 12회")).isEqualTo("SIMILAR:인클라인 덤벨 컬");
        assertThat(matchOf(me, "케이블 컬: 원암", "1세트 10kg 12회")).isEqualTo("SIMILAR:원암 케이블 컬");
        assertThat(matchOf(me, "카프 레이즈: 스탠딩, 바벨", "1세트 60kg 15회")).isEqualTo("SIMILAR:바벨 스탠딩 카프레이즈");
    }

    @Test
    @DisplayName("실제 일지(2026-11-20) — 짝 표로 머신 숄더프레스, 실패 세트는 해낸 횟수의 본세트로 들어간다")
    void failureSetSavedAsWorkingSet() throws Exception {
        LocalDate day = today.minusDays(1);
        String text = day + " 바디캘린더 473번째 운동 일지\n\n어깨1\n\n자유 운동 1분 3세트 0.8톤\n\n"
                + "숄더 프레스: 앉아서, 핀 머신\n1세트 35kg 7회\n2세트 35kg 10회\n실패 35kg 5/10회\n";
        String previewBody = preview(me, text)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].exercises[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.nameKo").value("머신 숄더프레스"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<String>) JsonPath.read(previewBody, "$.days[0].exercises[0].sets[*].kind"))
                .containsExactly("SET", "SET", "FAIL");

        String saved = save(me, requestFromPreview(previewBody, source -> {
            throw new AssertionError("미등록 운동이 없어야 한다: " + source);
        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].added").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String body = session(me, ((Number) JsonPath.read(saved, "$.days[0].sessionId")).longValue());
        assertThat((List<Integer>) JsonPath.read(body, "$.exercises[0].sets[*].reps")).containsExactly(7, 10, 5);
        assertThat((List<Boolean>) JsonPath.read(body, "$.exercises[0].sets[*].isWarmup"))
                .containsExactly(false, false, false);
    }

    @Test
    @DisplayName("바꿔 읽는 수식어 — 앉아서 → 시티드, 원판 머신 → 머신, T바 → 티바")
    void modifierWords() throws Exception {
        assertThat(matchOf(me, "숄더 프레스: 앉아서, 바벨", "1세트 40kg 10회")).isEqualTo("SIMILAR:시티드 바벨 숄더프레스");
        assertThat(matchOf(me, "숄더 프레스: 원판 머신", "1세트 40kg 10회")).isEqualTo("SIMILAR:머신 숄더프레스");
        assertThat(matchOf(me, "로우: T바, 스탠딩", "1세트 40kg 10회")).isEqualTo("SIMILAR:스탠딩 티바 로우");
    }

    @Test
    @DisplayName("기록 방식이 안 맞는 후보는 뺀다 — 회만 적힌 플랭크는 미등록, kg가 적힌 딥스는 맨몸 종목으로 짐작하지 않는다")
    void measureTypeMustFit() throws Exception {
        assertThat(matchOf(me, "플랭크: 기본", "1세트 30회")).isEqualTo("UNREGISTERED:null");
        assertThat(measureOf(me, "딥스", "1세트 20kg 10회")).isNotEqualTo("BODYWEIGHT_REPS");
        assertThat(measureOf(me, "딥스", "1세트 12회")).isEqualTo("BODYWEIGHT_REPS");
    }

    @Test
    @DisplayName("내가 맞춘 짝이 짝 표보다 먼저다. 짝의 종목이 숨겨지면 그 짝은 없는 것으로 본다")
    void rememberedAliasFirst() throws Exception {
        LocalDate day = today.minusDays(4);
        long closeGrip = builtinId("랫풀다운: 클로즈그립");
        save(me, request(day, null, "랫풀다운: 오버핸드", closeGrip, true, "{\"kind\":\"SET\",\"weightKg\":40,\"reps\":12}"))
                .andExpect(status().isCreated());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from exercise_import_aliases where source_name = ?",
                Long.class, "랫풀다운: 오버핸드")).isEqualTo(1L);
        assertThat(matchOf(me, "랫풀다운: 오버핸드", "1세트 40kg 12회")).isEqualTo("MATCHED:랫풀다운: 클로즈그립");
        // 다른 사람에게는 상관없다
        assertThat(matchOf(other, "랫풀다운: 오버핸드", "1세트 40kg 12회")).isEqualTo("MATCHED:랫풀다운: 와이드 그립");

        long custom = createCustom(me, "내 월 슬라이드", "BODYWEIGHT", "BODYWEIGHT_REPS");
        save(me, request(day, null, "월 슬라이드", custom, true, "{\"kind\":\"SET\",\"reps\":30}"))
                .andExpect(status().isCreated());
        assertThat(matchOf(me, "월 슬라이드", "1세트 30회")).isEqualTo("MATCHED:내 월 슬라이드");
        mvc.perform(delete("/api/v1/exercises/" + custom).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent()); // 기록이 있어 숨긴다
        assertThat(matchOf(me, "월 슬라이드", "1세트 30회")).isEqualTo("UNREGISTERED:null");
    }

    @Test
    @DisplayName("미래 날짜는 미리보기에서 빠진다. 날짜 줄이 없으면 400 IMPORT_FORMAT_UNKNOWN, 31일을 넘으면 400")
    void previewLimits() throws Exception {
        preview(me, BodyCalendarSamples.one(today.plusDays(1), "덤벨 컬", "1세트 10kg 12회"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
        preview(me, "어제 벤치 했음\n1세트 40kg 10회")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FORMAT_UNKNOWN"));

        StringBuilder month = new StringBuilder();
        for (int i = 1; i <= 32; i++) {
            month.append(BodyCalendarSamples.one(today.minusDays(i), "덤벨 컬", "1세트 10kg 12회")).append('\n');
        }
        preview(me, month.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/imports/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 저장

    @Test
    @DisplayName("샘플 저장 — 날마다 세트 수, 운동 시간은 N분, 맨몸 kg는 버림, 웜업은 워밍업·드랍은 본세트, 시간은 초")
    void saveSamples() throws Exception {
        LocalDate d1 = today.minusDays(3);
        LocalDate d2 = today.minusDays(2);
        long wallSlide = createCustom(me, "월 슬라이드", "BODYWEIGHT", "BODYWEIGHT_REPS");
        String previewBody = preview(me, BodyCalendarSamples.first(d1) + "\n" + BodyCalendarSamples.second(d2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String request = requestFromPreview(previewBody, source -> wallSlide);

        String saved = save(me, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].date").value(d1.toString()))
                .andExpect(jsonPath("$.days[0].added").value(54))
                .andExpect(jsonPath("$.days[0].alreadyThere").value(0))
                .andExpect(jsonPath("$.days[1].added").value(12))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long first = ((Number) JsonPath.read(saved, "$.days[0].sessionId")).longValue();
        long second = ((Number) JsonPath.read(saved, "$.days[1].sessionId")).longValue();

        String day1 = session(me, first);
        assertThat(((Number) JsonPath.read(day1, "$.durationOverrideSec")).intValue()).isEqualTo(110 * 60);
        assertThat((List<String>) JsonPath.read(day1, "$.exercises[*].exerciseName")).containsExactly(
                "월 슬라이드", "풀업", "랫풀다운: 와이드 그립", "랫풀다운: 언더그립", "바벨 벤치프레스",
                "원암 덤벨 숄더프레스", "인클라인 덤벨 컬", "덤벨 오버헤드 익스텐션", "케이블 푸시다운", "페이스풀",
                "머신 레터럴 레이즈");
        assertThat((List<Object>) JsonPath.read(day1, "$.exercises[1].sets[*].weightKg")).containsOnlyNulls();
        assertThat((List<Integer>) JsonPath.read(day1, "$.exercises[1].sets[*].setNo"))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        String day2 = session(me, second);
        assertThat(((Number) JsonPath.read(day2, "$.durationOverrideSec")).intValue()).isEqualTo(180);
        assertThat((List<Integer>) JsonPath.read(day2, "$.exercises[0].sets[*].durationSec"))
                .containsExactly(120, 120, 120);
        assertThat((List<Boolean>) JsonPath.read(day2, "$.exercises[2].sets[*].isWarmup"))
                .containsExactly(true, false, false, false);
        // 세트 시각은 그날 정오부터 — 순서가 지켜진다
        assertThat((String) JsonPath.read(day2, "$.startedAt")).startsWith(d2 + "T12:00:00");
    }

    @Test
    @DisplayName("같은 글을 두 번 넣으면 한 번만 들어간다 — 두 번째는 added 0, alreadyThere 전부")
    void saveTwiceOnce() throws Exception {
        LocalDate day = today.minusDays(2);
        String previewBody = preview(me, BodyCalendarSamples.second(day))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String request = requestFromPreview(previewBody, source -> {
            throw new AssertionError("미등록 운동이 없어야 한다: " + source);
        });

        save(me, request).andExpect(jsonPath("$.days[0].added").value(12));
        String again = save(me, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].added").value(0))
                .andExpect(jsonPath("$.days[0].alreadyThere").value(12))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long sessionId = ((Number) JsonPath.read(again, "$.days[0].sessionId")).longValue();
        assertThat((List<?>) JsonPath.read(session(me, sessionId), "$.exercises[*].sets[*]")).hasSize(12);
    }

    @Test
    @DisplayName("기록이 있던 날에는 그 뒤에 이어 붙이고 운동 시간은 덮지 않는다")
    void appendToExistingDay() throws Exception {
        LocalDate day = today.minusDays(3);
        long sessionId = openDay(me, day);
        long squat = builtinId("바벨 스쿼트");
        mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + squat
                                + ",\"weightKg\":100,\"reps\":5}"))
                .andExpect(status().isCreated());

        save(me, request(day, 45, "벤치 프레스: 바벨", builtinId("바벨 벤치프레스"), false,
                "{\"kind\":\"SET\",\"weightKg\":60,\"reps\":10}", "{\"kind\":\"SET\",\"weightKg\":60,\"reps\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.days[0].added").value(2));

        String body = session(me, sessionId);
        assertThat((List<String>) JsonPath.read(body, "$.exercises[*].exerciseName"))
                .containsExactly("바벨 스쿼트", "바벨 벤치프레스");
        assertThat((Object) JsonPath.read(body, "$.durationOverrideSec")).isNull();
    }

    @Test
    @DisplayName("기록 방식에 필요한 값이 없으면 400, 미래 날짜 400, 같은 날짜 두 번 400, 남의 내 종목 404")
    void saveValidation() throws Exception {
        LocalDate day = today.minusDays(1);
        save(me, request(day, null, "플랭크", builtinId("플랭크"), false, "{\"kind\":\"SET\",\"reps\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEASURE_INPUT"));
        save(me, request(today.plusDays(1), null, "덤벨 컬", builtinId("덤벨 컬"), false,
                "{\"kind\":\"SET\",\"weightKg\":10,\"reps\":12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        String oneDay = day(day, null, "덤벨 컬", builtinId("덤벨 컬"), false,
                "{\"kind\":\"SET\",\"weightKg\":10,\"reps\":12}");
        save(me, "{\"days\":[" + oneDay + "," + oneDay + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        long othersCustom = createCustom(other, "남의 종목", "CABLE", "WEIGHT_REPS");
        save(me, request(day, null, "남의 종목", othersCustom, true, "{\"kind\":\"SET\",\"weightKg\":10,\"reps\":12}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("가져온 세트는 분석(회복)에 들어가고, 어제를 가져와도 오늘 기록 열기는 오늘이다")
    void countedInAnalysisAndKeepsToday() throws Exception {
        LocalDate yesterday = today.minusDays(1);
        save(me, request(yesterday, 30, "벤치 프레스: 바벨", builtinId("바벨 벤치프레스"), false,
                "{\"kind\":\"SET\",\"weightKg\":60,\"reps\":10}"))
                .andExpect(status().isCreated());

        String recovery = mvc.perform(get("/api/v1/analysis/recovery").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<String>) JsonPath.read(recovery, "$.muscles[?(@.key == 'CHEST')].lastTrainedOn"))
                .containsExactly(yesterday.toString());

        mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.performedOn").value(today.toString()));
    }

    // ── 도우미

    private String signUp() {
        String email = "im" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return "Bearer " + authService.signUp(email, "hunter2hunter2", "민석").tokens().accessToken();
    }

    private ResultActions preview(String auth, String text) throws Exception {
        ObjectNode body = om.createObjectNode();
        body.put("text", text);
        return mvc.perform(post("/api/v1/imports/preview").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private ResultActions save(String auth, String json) throws Exception {
        return mvc.perform(post("/api/v1/imports").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 종목 하나짜리 일지의 맞춘 결과 — "상태:우리 이름" */
    private String matchOf(String auth, String title, String... setLines) throws Exception {
        String body = preview(auth, BodyCalendarSamples.one(today.minusDays(1), title, setLines))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> found = statusAndName(body, 0);
        assertThat(found).hasSize(1);
        return found.get(0).substring(found.get(0).indexOf('=') + 1);
    }

    private String measureOf(String auth, String title, String... setLines) throws Exception {
        String body = preview(auth, BodyCalendarSamples.one(today.minusDays(1), title, setLines))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.days[0].exercises[0].exercise.measureType");
    }

    /** 그날 종목마다 "원래 이름=상태:우리 이름" */
    private static List<String> statusAndName(String body, int day) {
        List<Map<String, Object>> exercises = JsonPath.read(body, "$.days[" + day + "].exercises");
        return exercises.stream().map(exercise -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> matched = (Map<String, Object>) exercise.get("exercise");
            return exercise.get("source") + "=" + exercise.get("status") + ":"
                    + (matched == null ? null : matched.get("nameKo"));
        }).toList();
    }

    /** 미리보기를 그대로 저장 요청으로 — 미등록 운동은 {@code unregistered}가 종목 id를 준다 */
    private String requestFromPreview(String previewBody, Function<String, Long> unregistered) {
        JsonNode preview = om.readTree(previewBody);
        ObjectNode request = om.createObjectNode();
        ArrayNode days = request.putArray("days");
        for (JsonNode day : preview.get("days")) {
            ObjectNode out = days.addObject();
            out.put("date", day.get("date").asString());
            out.set("minutes", day.get("minutes"));
            ArrayNode exercises = out.putArray("exercises");
            for (JsonNode exercise : day.get("exercises")) {
                ObjectNode item = exercises.addObject();
                String source = exercise.get("source").asString();
                boolean registered = !exercise.get("exercise").isNull();
                item.put("source", source);
                item.put("exerciseId", registered ? exercise.get("exercise").get("id").asLong()
                        : unregistered.apply(source));
                item.put("remember", !registered);
                item.set("sets", exercise.get("sets"));
            }
        }
        return om.writeValueAsString(request);
    }

    private static String request(LocalDate date, Integer minutes, String source, long exerciseId, boolean remember,
                                  String... sets) {
        return "{\"days\":[" + day(date, minutes, source, exerciseId, remember, sets) + "]}";
    }

    private static String day(LocalDate date, Integer minutes, String source, long exerciseId, boolean remember,
                              String... sets) {
        return "{\"date\":\"" + date + "\",\"minutes\":" + minutes + ",\"exercises\":[{\"source\":\"" + source
                + "\",\"exerciseId\":" + exerciseId + ",\"remember\":" + remember + ",\"sets\":["
                + String.join(",", sets) + "]}]}";
    }

    private long builtinId(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = ?", Long.class, name);
    }

    private long createCustom(String auth, String name, String equipment, String measureType) throws Exception {
        String body = mvc.perform(post("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameKo\":\"" + name + "\",\"category\":\"BACK\",\"equipment\":\"" + equipment
                                + "\",\"measureType\":\"" + measureType + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long openDay(String auth, LocalDate day) throws Exception {
        String body = mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + day + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String session(String auth, long sessionId) throws Exception {
        return mvc.perform(get("/api/v1/workout-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
