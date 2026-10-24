package com.fitness.backend.group.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.group.repository.FriendGroupRepository;
import com.fitness.backend.group.repository.GroupMemberRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
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
 * 친구 그룹 — 만들기·참여·권한·나가기.
 *
 * <p>오늘·이번 주 집계와 응원·기록 열람은 {@link GroupActivityTest}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuthService authService;
    @Autowired FriendGroupRepository groupRepository;
    @Autowired GroupMemberRepository memberRepository;
    @Autowired EntityManager entityManager;

    // ── 만들기·목록

    @Test
    @DisplayName("만든 사람이 방장이자 첫 멤버가 되고, 헷갈리는 글자 없는 6자리 초대코드가 나온다")
    void createMakesOwnerAndInviteCode() throws Exception {
        Member owner = signUp("민석");

        mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  헬스 친구들 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("헬스 친구들"))
                .andExpect(jsonPath("$.inviteCode").value(org.hamcrest.Matchers.matchesPattern("[A-HJKMNP-Z2-9]{6}")))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.members[0].isMe").value(true))
                .andExpect(jsonPath("$.members[0].isOwner").value(true))
                .andExpect(jsonPath("$.members[0].today.status").value("NONE"));
    }

    @Test
    @DisplayName("이름은 1~20자다 — 전각 공백(한글 입력기의 스페이스)만 있는 이름도 빈 이름이다")
    void nameIsValidated() throws Exception {
        Member owner = signUp("민석");
        for (String body : new String[] {"{\"name\":\"   \"}", "{\"name\":\"　　\"}",
                "{\"name\":\"" + "가".repeat(21) + "\"}"}) {
            mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("목록에는 내가 들어간 그룹만, 들어간 순서로 나온다")
    void listShowsOnlyMyGroups() throws Exception {
        Member owner = signUp("민석");
        Member other = signUp("영희");
        createGroup(owner, "첫째");
        createGroup(owner, "둘째");

        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("첫째"))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].isOwner").value(true))
                .andExpect(jsonPath("$[1].name").value("둘째"));
        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── 초대·참여

    @Test
    @DisplayName("초대코드로 미리보고 참여한다 — 소문자·공백·하이픈도 받는다")
    void previewAndJoinByCode() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode group = createGroup(owner, "헬스 친구들");
        String code = group.get("inviteCode").asString();

        mvc.perform(get("/api/v1/groups/invite/" + code.toLowerCase(Locale.ROOT))
                        .header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("헬스 친구들"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.ownerNickname").value("민석"))
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andExpect(jsonPath("$.full").value(false));

        String typed = " " + code.substring(0, 3).toLowerCase(Locale.ROOT) + "-" + code.substring(3) + " ";
        join(friend, typed)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(group.get("id").asLong()))
                .andExpect(jsonPath("$.alreadyMember").value(false));

        mvc.perform(get("/api/v1/groups/" + group.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.members[1].userId").value(friend.id()))
                .andExpect(jsonPath("$.members[1].isMe").value(true));
    }

    @Test
    @DisplayName("이미 멤버가 다시 참여하면 오류가 아니라 200과 alreadyMember다 — 링크를 두 번 누르는 일은 흔하다")
    void joiningTwiceIsNotAnError() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        String code = createGroup(owner, "헬스").get("inviteCode").asString();
        join(friend, code).andExpect(status().isCreated());

        join(friend, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(true));
        mvc.perform(get("/api/v1/groups/invite/" + code).header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(jsonPath("$.alreadyMember").value(true))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @DisplayName("없는 코드는 404다")
    void unknownCodeIsNotFound() throws Exception {
        Member friend = signUp("영희");
        mvc.perform(get("/api/v1/groups/invite/ZZZZZZ").header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isNotFound());
        join(friend, "ZZZZZZ").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("멤버가 아니면 그룹이 있는지조차 알 수 없다 — 403이 아니라 404")
    void strangerCannotSeeGroup() throws Exception {
        Member owner = signUp("민석");
        Member stranger = signUp("낯선이");
        long groupId = createGroup(owner, "헬스").get("id").asLong();

        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/groups/" + groupId + "/members/" + owner.id() + "/calendar")
                        .param("year", "2026").param("month", "10")
                        .header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/me").header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound());
    }

    // ── 방장 권한

    @Test
    @DisplayName("이름 변경·코드 재발급·내보내기·삭제는 방장만 — 멤버는 403")
    void ownerOnlyActionsRejectMembers() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        join(friend, group.get("inviteCode").asString());

        mvc.perform(patch("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, friend.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/groups/" + groupId + "/invite-code").header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("방장이 이름을 바꾸고 코드를 다시 만들면 옛 코드로는 못 들어온다")
    void ownerRenamesAndRegeneratesCode() throws Exception {
        Member owner = signUp("민석");
        Member late = signUp("늦게옴");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        String oldCode = group.get("inviteCode").asString();

        mvc.perform(patch("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새벽 헬스\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새벽 헬스"));

        String newCode = json(mvc.perform(post("/api/v1/groups/" + groupId + "/invite-code")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())).get("inviteCode").asString();
        assertNotEquals(oldCode, newCode);

        join(late, oldCode).andExpect(status().isNotFound());
        join(late, newCode).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("방장이 내보낸 멤버는 그룹을 볼 수 없다. 방장 자신은 내보낼 수 없다")
    void ownerRemovesMember() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        join(friend, group.get("inviteCode").asString());

        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + friend.id())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isNotFound());
        // 이미 나간 사람을 또 내보내면 404
        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + friend.id())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("방장이 그룹을 지우면 멤버십도 함께 지워진다")
    void ownerDeletesGroup() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        join(friend, group.get("inviteCode").asString());

        mvc.perform(delete("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        assertFalse(groupRepository.existsById(groupId));
        assertEquals(0, memberRepository.countByGroupId(groupId));
        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── 나가기

    @Test
    @DisplayName("방장이 나가면 남은 멤버 중 가장 먼저 들어온 사람이 방장이 된다")
    void ownerLeavingPassesOwnershipToOldestMember() throws Exception {
        Member owner = signUp("민석");
        Member first = signUp("먼저옴");
        Member second = signUp("나중옴");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        join(first, group.get("inviteCode").asString());
        join(second, group.get("inviteCode").asString());

        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/me").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, first.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(first.id()))
                .andExpect(jsonPath("$.members[0].isOwner").value(true));
        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("마지막 한 명이 나가면 그룹이 지워진다")
    void lastMemberLeavingDeletesGroup() throws Exception {
        Member owner = signUp("민석");
        long groupId = createGroup(owner, "혼자").get("id").asLong();

        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/me").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());
        assertFalse(groupRepository.existsById(groupId));
    }

    @Test
    @DisplayName("멤버가 나가도 그룹과 방장은 그대로다")
    void memberLeavingKeepsGroup() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode group = createGroup(owner, "헬스");
        long groupId = group.get("id").asLong();
        join(friend, group.get("inviteCode").asString());

        mvc.perform(delete("/api/v1/groups/" + groupId + "/members/me").header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    @DisplayName("방장이 탈퇴하면 그룹은 다음 멤버에게 넘어가고, 혼자인 그룹은 지워진다")
    void accountDeletionReleasesOwnership() throws Exception {
        Member owner = signUp("민석");
        Member friend = signUp("영희");
        JsonNode shared = createGroup(owner, "같이");
        long sharedId = shared.get("id").asLong();
        long soloId = createGroup(owner, "혼자").get("id").asLong();
        join(friend, shared.get("inviteCode").asString());

        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"hunter2hunter2\"}"))
                .andExpect(status().isNoContent());
        // 운영에서는 요청마다 커밋된다. 테스트는 전체가 한 트랜잭션이라, 사용자 삭제와 그 연쇄
        // 삭제(멤버십)를 DB에 내려 보내야 다음 요청이 운영과 같은 상태를 본다
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/v1/groups/" + sharedId).header(HttpHeaders.AUTHORIZATION, friend.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.members.length()").value(1));
        assertFalse(groupRepository.existsById(soloId));
    }

    // ── 한도

    @Test
    @DisplayName("한 사람이 들어갈 수 있는 그룹은 10개다")
    void userGroupLimit() throws Exception {
        Member owner = signUp("민석");
        for (int i = 0; i < 10; i++) {
            createGroup(owner, "그룹" + i);
        }
        mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"열하나\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("그룹 인원은 20명까지다 — 미리보기에도 가득 참이 보인다")
    void groupMemberLimit() throws Exception {
        Member owner = signUp("방장");
        String code = createGroup(owner, "꽉 참").get("inviteCode").asString();
        for (int i = 0; i < 19; i++) {
            join(signUp("멤버" + i), code).andExpect(status().isCreated());
        }

        Member late = signUp("늦게옴");
        mvc.perform(get("/api/v1/groups/invite/" + code).header(HttpHeaders.AUTHORIZATION, late.bearer()))
                .andExpect(jsonPath("$.memberCount").value(20))
                .andExpect(jsonPath("$.full").value(true));
        join(late, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_LIMIT_EXCEEDED"));
        assertEquals(20, memberRepository.countByGroupId(groupRepository.findByInviteCode(code).orElseThrow().getId()));
    }

    // ── 도우미

    private record Member(Long id, String bearer) {
    }

    private Member signUp(String nickname) {
        String email = "g" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        AuthService.AuthResult result = authService.signUp(email, "hunter2hunter2", nickname);
        return new Member(result.user().getId(), "Bearer " + result.tokens().accessToken());
    }

    private JsonNode createGroup(Member owner, String name) throws Exception {
        return json(mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions join(Member member, String code) throws Exception {
        return mvc.perform(post("/api/v1/groups/join").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
