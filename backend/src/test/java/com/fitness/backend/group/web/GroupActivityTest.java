package com.fitness.backend.group.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 친구 그룹 — 오늘·이번 주 집계, 멤버 기록 열람, 응원.
 *
 * <p>멤버 셋(방장·영희·철수)이 한 그룹에 있고, 각자의 기록을 만들어 방장 눈으로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupActivityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuthService authService;

    private Member owner;
    private Member younghee;
    private Member chulsoo;
    private long groupId;
    private long benchPress;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("민석");
        younghee = signUp("영희");
        chulsoo = signUp("철수");
        JsonNode group = json(mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"헬스 친구들\"}"))
                .andExpect(status().isCreated()));
        groupId = group.get("id").asLong();
        for (Member member : new Member[] {younghee, chulsoo}) {
            mvc.perform(post("/api/v1/groups/join").header(HttpHeaders.AUTHORIZATION, member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"" + group.get("inviteCode").asString() + "\"}"))
                    .andExpect(status().isCreated());
        }
        benchPress = exerciseIdOf("바벨 벤치프레스");
        today = LocalDate.now();
    }

    // ── 오늘·이번 주

    @Test
    @DisplayName("오늘 운동은 기록에서 정한다 — 오늘 세트가 있으면 DONE, 기록을 열기만 했거나 없으면 NONE")
    void todayStatusComesFromRecords() throws Exception {
        long done = liveSession(younghee, 2);
        liveSession(chulsoo, 0);   // 기록 화면만 열고 체크하지 않음

        detail(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.members[0].today.status").value("NONE"))
                .andExpect(jsonPath("$.members[0].today.sessionId").doesNotExist())
                .andExpect(jsonPath("$.members[1].today.status").value("DONE"))
                .andExpect(jsonPath("$.members[1].today.sessionId").value(done))
                .andExpect(jsonPath("$.members[2].today.status").value("NONE"))
                .andExpect(jsonPath("$.members[2].today.sessionId").doesNotExist());

        // 목록 카드에서도 멤버별 오늘 상태가 보인다 — 들어온 순서
        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$[0].memberCount").value(3))
                .andExpect(jsonPath("$[0].todayWorkedOutCount").value(1))
                .andExpect(jsonPath("$[0].members.length()").value(3))
                .andExpect(jsonPath("$[0].members[0].isMe").value(true))
                .andExpect(jsonPath("$[0].members[0].todayStatus").value("NONE"))
                .andExpect(jsonPath("$[0].members[1].nickname").value("영희"))
                .andExpect(jsonPath("$[0].members[1].todayStatus").value("DONE"))
                .andExpect(jsonPath("$[0].members[2].todayStatus").value("NONE"));
    }

    @Test
    @DisplayName("이번 주는 월요일부터 운동한 날을 센다 — 사후 입력은 횟수엔 들고 시간 합에선 빠진다")
    void weekCountsWorkoutDaysSinceMonday() throws Exception {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        openWithSets(younghee, weekStart.minusDays(1), 1);   // 지난주 일요일 — 빠진다
        // 이번 주의 지난 날에 사후 입력. 오늘이 월요일이면 이번 주에 지난 날이 없다
        boolean earlierDayThisWeek = weekStart.isBefore(today);
        if (earlierDayThisWeek) {
            openWithSets(younghee, weekStart, 1);
        }
        long live = liveSession(younghee, 2);
        int liveDuration = json(mvc.perform(get("/api/v1/workout-sessions/" + live)
                .header(HttpHeaders.AUTHORIZATION, younghee.bearer()))).get("effectiveDurationSec").asInt();
        int days = earlierDayThisWeek ? 2 : 1;

        detail(owner)
                .andExpect(jsonPath("$.weekStart").value(weekStart.toString()))
                .andExpect(jsonPath("$.members[1].week.dayCount").value(days))
                .andExpect(jsonPath("$.members[1].week.totalDurationSec").value(liveDuration))
                .andExpect(jsonPath("$.members[1].week.workoutDates.length()").value(days))
                .andExpect(jsonPath("$.members[1].week.workoutDates[" + (days - 1) + "]").value(today.toString()))
                .andExpect(jsonPath("$.members[1].today.sessionId").value(live))
                .andExpect(jsonPath("$.members[0].week.dayCount").value(0))
                .andExpect(jsonPath("$.members[0].week.totalDurationSec").value(0));
    }

    // ── 멤버 기록 열람

    @Test
    @DisplayName("멤버의 하루 기록은 세트까지 보이고 메모는 보이지 않는다")
    void memberDayHidesMemo() throws Exception {
        long session = liveSession(younghee, 2);
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, younghee.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"오늘 컨디션 최악\"}"))
                .andExpect(status().isOk());

        memberDay(owner, younghee, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("영희"))
                .andExpect(jsonPath("$.session.id").value(session))
                .andExpect(jsonPath("$.session.exercises[0].exerciseName").value("바벨 벤치프레스"))
                // 종목 카드의 동작 그림 파일 이름을 만든다
                .andExpect(jsonPath("$.session.exercises[0].exerciseNameEn").isNotEmpty())
                .andExpect(jsonPath("$.session.exercises[0].sets.length()").value(2))
                .andExpect(jsonPath("$.session.exercises[0].sets[0].weightKg").value(40.0))
                .andExpect(jsonPath("$.session.exercises[0].sets[0].reps").value(10))
                .andExpect(jsonPath("$.session.memo").doesNotExist())
                .andExpect(jsonPath("$.session.exercises[0].sets[0].clientSetId").doesNotExist());

        memberDay(owner, younghee, today.minusDays(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("운동하는 중에도 그때까지 체크한 세트가 보이고 응원할 수 있다 — 종료가 없다")
    void workoutInProgressIsVisible() throws Exception {
        long session = liveSession(chulsoo, 0);   // 열기만 했다

        // 세트가 없으면 운동한 날이 아니다
        memberDay(owner, chulsoo, today).andExpect(jsonPath("$.session").doesNotExist());
        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + chulsoo.id() + "/calendar")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.days.length()").value(0));

        addSets(chulsoo, session, 2);
        memberDay(owner, chulsoo, today)
                .andExpect(jsonPath("$.session.exercises[0].sets.length()").value(2));
        cheer(owner, session).andExpect(status().isOk());

        // 한 세트 더 하면 그대로 늘어난다
        addSets(chulsoo, session, 1);
        memberDay(owner, chulsoo, today)
                .andExpect(jsonPath("$.session.exercises[0].sets.length()").value(3))
                .andExpect(jsonPath("$.session.cheerCount").value(1));
    }

    @Test
    @DisplayName("멤버의 캘린더는 내 캘린더와 같은 모양이다")
    void memberCalendarMatchesOwnCalendar() throws Exception {
        liveSession(younghee, 1);

        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + younghee.id() + "/calendar")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value(today.toString()));
    }

    @Test
    @DisplayName("터무니없는 연도는 500이 아니라 400이다")
    void calendarRejectsAbsurdYear() throws Exception {
        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + younghee.id() + "/calendar")
                        .param("year", "2000000000").param("month", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("그룹 밖 사람의 기록은 볼 수 없다")
    void cannotSeeNonMemberRecords() throws Exception {
        Member stranger = signUp("낯선이");
        memberDay(owner, stranger, today).andExpect(status().isNotFound());
        memberDay(stranger, younghee, today).andExpect(status().isNotFound());
    }

    // ── 응원

    @Test
    @DisplayName("응원은 한 사람이 한 번 — 다시 눌러도 그대로고, 취소하면 빠진다")
    void cheerIsOncePerPerson() throws Exception {
        long session = liveSession(younghee, 1);

        cheer(owner, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.cheerCount").value(1))
                .andExpect(jsonPath("$.cheeredByMe").value(true));
        cheer(owner, session).andExpect(jsonPath("$.cheerCount").value(1));
        cheer(chulsoo, session).andExpect(jsonPath("$.cheerCount").value(2));

        detail(owner)
                .andExpect(jsonPath("$.members[1].today.cheerCount").value(2))
                .andExpect(jsonPath("$.members[1].today.cheeredByMe").value(true));
        // 응원받은 본인에게도 수가 보인다
        detail(younghee)
                .andExpect(jsonPath("$.members[1].today.cheerCount").value(2))
                .andExpect(jsonPath("$.members[1].today.cheeredByMe").value(false));
        memberDay(chulsoo, younghee, today)
                .andExpect(jsonPath("$.session.cheerCount").value(2))
                .andExpect(jsonPath("$.session.cheeredByMe").value(true));

        cancelCheer(owner, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.cheerCount").value(1))
                .andExpect(jsonPath("$.cheeredByMe").value(false));
        // 누르지 않은 응원을 취소해도 오류가 아니다
        cancelCheer(owner, session).andExpect(status().isOk()).andExpect(jsonPath("$.cheerCount").value(1));
    }

    @Test
    @DisplayName("내 운동에는 응원할 수 없고, 그룹 밖 운동에도 할 수 없다")
    void cheerIsOnlyForGroupMates() throws Exception {
        long mine = liveSession(owner, 1);
        cheer(owner, mine).andExpect(status().isBadRequest());

        Member stranger = signUp("낯선이");
        long strangers = liveSession(stranger, 1);
        cheer(owner, strangers).andExpect(status().isNotFound());

        long younghees = liveSession(younghee, 1);
        cheer(stranger, younghees).andExpect(status().isNotFound());
    }

    // ── 도우미

    private record Member(Long id, String bearer) {
    }

    private Member signUp(String nickname) {
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        AuthService.AuthResult result = authService.signUp(email, "hunter2hunter2", nickname);
        return new Member(result.user().getId(), "Bearer " + result.tokens().accessToken());
    }

    private ResultActions detail(Member viewer) throws Exception {
        return mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
    }

    private ResultActions memberDay(Member viewer, Member target, LocalDate date) throws Exception {
        return mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + target.id() + "/days/" + date)
                .header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
    }

    private ResultActions cheer(Member viewer, long sessionId) throws Exception {
        return mvc.perform(put("/api/v1/groups/" + groupId + "/sessions/" + sessionId + "/cheer")
                .header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
    }

    private ResultActions cancelCheer(Member viewer, long sessionId) throws Exception {
        return mvc.perform(delete("/api/v1/groups/" + groupId + "/sessions/" + sessionId + "/cheer")
                .header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
    }

    /** 오늘 기록을 열고 세트를 넣는다 */
    private long liveSession(Member member, int sets) throws Exception {
        return openWithSets(member, today, sets);
    }

    /** 그날 기록을 열고 세트를 넣는다. 지난 날짜면 사후 입력이다 */
    private long openWithSets(Member member, LocalDate date, int sets) throws Exception {
        long id = json(mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + date + "\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        addSets(member, id, sets);
        return id;
    }

    private void addSets(Member member, long sessionId, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/sets")
                            .header(HttpHeaders.AUTHORIZATION, member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + benchPress
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

    private JsonNode json(ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
