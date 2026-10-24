package com.fitness.backend.group.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.group.repository.CheerRepository;
import com.fitness.backend.group.repository.FriendGroupRepository;
import com.fitness.backend.group.repository.GroupMemberRepository;
import com.fitness.backend.group.web.GroupDtos.GroupDetailResponse;
import com.fitness.backend.user.repository.UserRepository;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 동시에 들어온 요청. 연타·동시 참여·동시 나가기가 규칙을 깨지 않는지 본다.
 *
 * <p>다른 테스트와 달리 {@code @Transactional}을 걸지 않는다 — 요청마다 따로 커밋돼야 실제로
 * 경합이 생긴다. 대신 만든 데이터를 끝나고 지운다.
 */
@SpringBootTest
class GroupConcurrencyTest {

    @Autowired GroupService groupService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired FriendGroupRepository groupRepository;
    @Autowired GroupMemberRepository memberRepository;
    @Autowired CheerRepository cheerRepository;
    @Autowired WorkoutSessionRepository sessionRepository;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> groups = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 방장 FK에 연쇄 삭제가 없어 그룹부터 지운다. 멤버십·세션·응원은 사용자를 지우면 따라 지워진다
        groups.forEach(id -> groupRepository.findById(id).ifPresent(groupRepository::delete));
        userRepository.deleteAllById(users);
    }

    @Test
    @DisplayName("같은 사람이 초대 링크를 연타해도 한 번만 들어가고, 오류가 나지 않는다")
    void doubleJoinIsIdempotent() throws Exception {
        Long owner = user();
        Long friend = user();
        String code = group(owner).inviteCode();

        List<Object> results = race(() -> groupService.join(friend, code), () -> groupService.join(friend, code));

        assertTrue(results.stream().noneMatch(Throwable.class::isInstance), results.toString());
        assertEquals(2, memberRepository.countByGroupId(groups.get(0)));
    }

    @Test
    @DisplayName("19명인 그룹에 둘이 동시에 들어오면 한 명만 들어간다 — 20명을 넘지 않는다")
    void memberLimitHoldsUnderRace() throws Exception {
        Long owner = user();
        String code = group(owner).inviteCode();
        for (int i = 0; i < 18; i++) {
            groupService.join(user(), code);
        }
        Long a = user();
        Long b = user();

        List<Object> results = race(() -> groupService.join(a, code), () -> groupService.join(b, code));

        assertEquals(20, memberRepository.countByGroupId(groups.get(0)));
        assertEquals(1, results.stream()
                .filter(r -> r instanceof ApiException e && e.errorCode() == ErrorCode.GROUP_LIMIT_EXCEEDED)
                .count(), results.toString());
    }

    @Test
    @DisplayName("방장과 다음 방장이 동시에 나가도 방장은 남은 멤버다")
    void ownerAndSuccessorLeavingTogether() throws Exception {
        Long owner = user();
        Long successor = user();
        Long third = user();
        String code = group(owner).inviteCode();
        groupService.join(successor, code);
        groupService.join(third, code);
        Long groupId = groups.get(0);

        race(() -> {
            groupService.leave(owner, groupId);
            return null;
        }, () -> {
            groupService.leave(successor, groupId);
            return null;
        });

        Long ownerId = groupRepository.findById(groupId).orElseThrow().getOwnerId();
        assertEquals(third, ownerId);
        assertTrue(memberRepository.existsByGroupIdAndUserId(groupId, ownerId));
        assertEquals(1, memberRepository.countByGroupId(groupId));
    }

    @Test
    @DisplayName("응원을 연타해도 한 번이고, 오류가 나지 않는다")
    void doubleCheerIsIdempotent() throws Exception {
        Long owner = user();
        Long friend = user();
        String code = group(owner).inviteCode();
        groupService.join(friend, code);
        sessionRepository.insertIfAbsent(friend, LocalDate.now());
        Long sessionId = sessionRepository.findByUserIdAndPerformedOn(friend, LocalDate.now()).orElseThrow().getId();
        Long groupId = groups.get(0);

        List<Object> results = race(() -> groupService.cheer(owner, groupId, sessionId),
                () -> groupService.cheer(owner, groupId, sessionId));

        assertTrue(results.stream().noneMatch(Throwable.class::isInstance), results.toString());
        assertEquals(1, cheerRepository.countBySessionId(sessionId));
    }

    // ── 도우미

    private Long user() {
        String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Long id = authService.signUp(email, "hunter2hunter2", "경합").user().getId();
        users.add(id);
        return id;
    }

    private GroupDetailResponse group(Long owner) {
        GroupDetailResponse group = groupService.create(owner, "경합 시험");
        groups.add(group.id());
        return group;
    }

    /** 두 작업을 최대한 같은 순간에 시작한다. 결과나 던진 예외를 순서대로 돌려준다 */
    private static List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
