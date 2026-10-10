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
 * 친구 그룹 — 오늘·이번 주 집계, 멤버 기록 열람, 응원. 명세 10장 (LOG-38).
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
    @DisplayName("오늘 운동은 기록에서 정한다 — 완료하면 DONE, 기록 중이거나 없으면 NONE (끝난 운동만 보인다)")
    void todayStatusComesFromRecords() throws Exception {
        long done = liveSession(younghee, 2);
        complete(younghee, done);
        liveSession(chulsoo, 1);   // 종료하지 않음

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
    @DisplayName("이번 주는 월요일부터다 — 횟수엔 사후 입력이 들어가고, 시간 합에선 시간 없는 사후 입력이 빠진다")
    void weekCountsDoneSessionsSinceMonday() throws Exception {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        backfillDone(younghee, weekStart.minusDays(1));   // 지난주 일요일 — 빠진다
        backfillDone(younghee, today);                     // 사후 입력 — 횟수엔 들고 시간엔 빠진다
        long live = liveSession(younghee, 2);
        int liveDuration = complete(younghee, live).get("effectiveDurationSec").asInt();

        detail(owner)
                .andExpect(jsonPath("$.weekStart").value(weekStart.toString()))
                .andExpect(jsonPath("$.members[1].week.sessionCount").value(2))
                .andExpect(jsonPath("$.members[1].week.totalDurationSec").value(liveDuration))
                .andExpect(jsonPath("$.members[1].week.workoutDates.length()").value(1))
                .andExpect(jsonPath("$.members[1].week.workoutDates[0]").value(today.toString()))
                // 오늘 둘 중 가장 최근에 끝낸 것이 응원 대상이다
                .andExpect(jsonPath("$.members[1].today.sessionId").value(live))
                .andExpect(jsonPath("$.members[0].week.sessionCount").value(0))
                .andExpect(jsonPath("$.members[0].week.totalDurationSec").value(0));
    }

    // ── 멤버 기록 열람

    @Test
    @DisplayName("멤버의 하루 기록은 세트까지 보이고 메모는 보이지 않는다")
    void memberDayHidesMemo() throws Exception {
        long session = liveSession(younghee, 2);
        complete(younghee, session);
        mvc.perform(patch("/api/v1/workout-sessions/" + session).header(HttpHeaders.AUTHORIZATION, younghee.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"오늘 컨디션 최악\"}"))
                .andExpect(status().isOk());

        memberDay(owner, younghee, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("영희"))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].id").value(session))
                .andExpect(jsonPath("$.sessions[0].status").value("DONE"))
                .andExpect(jsonPath("$.sessions[0].exercises[0].exerciseName").value("바벨 벤치프레스"))
                // 종목 카드의 동작 그림 파일 이름을 만든다
                .andExpect(jsonPath("$.sessions[0].exercises[0].exerciseNameEn").isNotEmpty())
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets[0].weightKg").value(40.0))
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets[0].reps").value(10))
                .andExpect(jsonPath("$.sessions[0].memo").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets[0].clientSetId").doesNotExist());

        memberDay(owner, younghee, today.minusDays(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    @Test
    @DisplayName("진행 중인 운동은 하루 기록·캘린더에 보이지 않고, 응원할 수도 없다")
    void inProgressWorkoutIsHidden() throws Exception {
        long draft = liveSession(chulsoo, 2);   // 종료하지 않음

        memberDay(owner, chulsoo, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(0));
        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + chulsoo.id() + "/calendar")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.days.length()").value(0));
        cheer(owner, draft).andExpect(status().isNotFound());

        complete(chulsoo, draft);
        memberDay(owner, chulsoo, today).andExpect(jsonPath("$.sessions.length()").value(1));
        cheer(owner, draft).andExpect(status().isOk());
    }

    @Test
    @DisplayName("멤버의 캘린더는 내 캘린더와 같은 모양이다")
    void memberCalendarMatchesOwnCalendar() throws Exception {
        complete(younghee, liveSession(younghee, 1));

        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + younghee.id() + "/calendar")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value(today.toString()))
                .andExpect(jsonPath("$.days[0].sessionCount").value(1));
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
        complete(younghee, session);

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
                .andExpect(jsonPath("$.sessions[0].cheerCount").value(2))
                .andExpect(jsonPath("$.sessions[0].cheeredByMe").value(true));

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
        complete(owner, mine);
        cheer(owner, mine).andExpect(status().isBadRequest());

        Member stranger = signUp("낯선이");
        long strangers = liveSession(stranger, 1);
        complete(stranger, strangers);
        cheer(owner, strangers).andExpect(status().isNotFound());

        long younghees = liveSession(younghee, 1);
        complete(younghee, younghees);
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

    /** 오늘 날짜로 진행 중 기록을 시작하고 세트를 넣는다. 종료는 하지 않는다 */
    private long liveSession(Member member, int sets) throws Exception {
        long id = json(mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"performedOn\":\"" + today + "\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        addSets(member, id, sets);
        return id;
    }

    /** 사후 입력 한 건(세트 하나)을 종료까지 */
    private void backfillDone(Member member, LocalDate date) throws Exception {
        long id = json(mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + date + "\",\"source\":\"BACKFILL\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        addSets(member, id, 1);
        complete(member, id);
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

    private JsonNode complete(Member member, long sessionId) throws Exception {
        return json(mvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk()));
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
