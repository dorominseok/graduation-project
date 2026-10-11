package com.fitness.backend.group.service;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.group.domain.FriendGroup;
import com.fitness.backend.group.domain.GroupMember;
import com.fitness.backend.group.domain.InviteCode;
import com.fitness.backend.group.domain.TodayStatus;
import com.fitness.backend.group.repository.CheerCount;
import com.fitness.backend.group.repository.CheerRepository;
import com.fitness.backend.group.repository.FriendGroupRepository;
import com.fitness.backend.group.repository.GroupMemberRepository;
import com.fitness.backend.group.web.GroupDtos.CheerResponse;
import com.fitness.backend.group.web.GroupDtos.GroupDetailResponse;
import com.fitness.backend.group.web.GroupDtos.GroupSummary;
import com.fitness.backend.group.web.GroupDtos.InviteCodeResponse;
import com.fitness.backend.group.web.GroupDtos.InvitePreview;
import com.fitness.backend.group.web.GroupDtos.JoinResponse;
import com.fitness.backend.group.web.GroupDtos.MemberActivity;
import com.fitness.backend.group.web.GroupDtos.MemberDayResponse;
import com.fitness.backend.group.web.GroupDtos.MemberPreview;
import com.fitness.backend.group.web.GroupDtos.MemberExercise;
import com.fitness.backend.group.web.GroupDtos.MemberSession;
import com.fitness.backend.group.web.GroupDtos.MemberSet;
import com.fitness.backend.group.web.GroupDtos.TodayActivity;
import com.fitness.backend.group.web.GroupDtos.WeekActivity;
import com.fitness.backend.user.domain.User;
import com.fitness.backend.user.repository.UserRepository;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.repository.WorkoutSessionRepository;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarResponse;
import com.fitness.backend.workout.web.WorkoutDtos.DayTimes;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseGroup;
import com.fitness.backend.workout.web.WorkoutDtos.SessionResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 그룹. 명세 10장, 기능명세 2.6, UC-05 (LOG-38).
 *
 * <p>성격은 경쟁이 아니라 <b>서로의 운동이 보이는 것</b>이다(기능명세 2.6). 그래서 순위·점수는
 * 없고, 오늘 운동했는지와 이번 주 횟수·시간, 그리고 멤버의 기록을 읽기 전용으로 보여준다.
 *
 * <p><b>권한</b>: 그룹의 모든 조회는 멤버만 한다. 멤버가 아니면 그룹이 있는지조차 드러내지
 * 않으려고 403이 아니라 404를 준다(명세 6.5와 같은 원칙). 멤버인데 방장 기능을 쓰면 그때는
 * 그룹이 있다는 걸 이미 아므로 403이다.
 */
@Service
@Transactional(readOnly = true)
public class GroupService {

    /** 한 그룹의 최대 인원. 그룹 화면이 멤버 전원을 한 번에 그리므로 상한을 둔다 */
    static final int MAX_MEMBERS = 20;
    /** 한 사람이 들어갈 수 있는 그룹 수 */
    static final int MAX_GROUPS_PER_USER = 10;
    private static final int INVITE_CODE_ATTEMPTS = 5;

    private final FriendGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final CheerRepository cheerRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutService workoutService;
    private final ExerciseRepository exerciseRepository;
    private final Clock clock;

    public GroupService(FriendGroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        CheerRepository cheerRepository,
                        UserRepository userRepository,
                        WorkoutSessionRepository sessionRepository,
                        WorkoutService workoutService,
                        ExerciseRepository exerciseRepository,
                        Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.cheerRepository = cheerRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.workoutService = workoutService;
        this.exerciseRepository = exerciseRepository;
        this.clock = clock;
    }

    // ── 그룹

    /** 그룹 만들기(UC-05-01). 만든 사람이 방장이자 첫 멤버다. */
    @Transactional
    public GroupDetailResponse create(Long userId, String name) {
        ensureCanJoinMore(userId);
        FriendGroup group = groupRepository.save(FriendGroup.create(cleanName(name), newInviteCode(), userId));
        memberRepository.save(GroupMember.join(group.getId(), userId, OffsetDateTime.now(clock)));
        return detail(userId, group.getId());
    }

    /**
     * 내 그룹 목록. 그룹은 보통 한두 개라, 들어가기 전에도 누가 오늘 운동했는지 보이게
     * 멤버별 오늘 상태를 함께 담는다.
     *
     * <p>그룹마다 멤버·기록을 조회하면 그룹 수만큼 쿼리가 나간다. 내 그룹들의 멤버와 그들의
     * 오늘 기록을 한 번씩 읽어 메모리에서 나눈다.
     */
    public List<GroupSummary> myGroups(Long userId) {
        List<Long> groupIds = memberRepository.findByUserIdOrderByJoinedAtAscIdAsc(userId).stream()
                .map(GroupMember::getGroupId)
                .toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }

        Map<Long, FriendGroup> groups = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(FriendGroup::getId, Function.identity()));
        Map<Long, List<Long>> memberIdsByGroup = memberRepository.findByGroupIdIn(groupIds).stream()
                .sorted(Comparator.comparing(GroupMember::getJoinedAt).thenComparing(GroupMember::getId))
                .collect(Collectors.groupingBy(GroupMember::getGroupId,
                        Collectors.mapping(GroupMember::getUserId, Collectors.toList())));
        Set<Long> allMemberIds = memberIdsByGroup.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<Long, String> nicknames = nicknames(allMemberIds);

        LocalDate today = LocalDate.now(clock);
        Map<Long, WorkoutSession> todayByMember = sessionRepository
                .findRecorded(allMemberIds, today, today).stream()
                .collect(Collectors.toMap(WorkoutSession::getUserId, Function.identity()));

        return groupIds.stream()
                // 멤버십을 읽은 뒤 방장이 그룹을 지웠으면 여기서 빠진다
                .filter(groups::containsKey)
                .map(groupId -> {
                    FriendGroup group = groups.get(groupId);
                    List<Long> memberIds = memberIdsByGroup.getOrDefault(groupId, List.of());
                    List<MemberPreview> previews = memberIds.stream()
                            .map(memberId -> new MemberPreview(memberId, nicknames.get(memberId),
                                    memberId.equals(userId),
                                    todayActivity(todayByMember.get(memberId), NO_CHEERS).status()))
                            .toList();
                    return new GroupSummary(groupId, group.getName(), memberIds.size(), group.isOwnedBy(userId),
                            (int) previews.stream().filter(p -> p.todayStatus() == TodayStatus.DONE).count(),
                            previews);
                })
                .toList();
    }

    /** 목록 카드는 응원 수를 보이지 않는다 */
    private static final CheerTally NO_CHEERS = new CheerTally(Map.of(), Set.of());

    /**
     * 그룹 상세(UC-05-03) — 멤버별 오늘 운동 여부와 이번 주(월~일) 횟수·시간.
     *
     * <p>집계 테이블 없이 그때그때 센다(기능명세 2.6). 멤버 전원의 이번 주 기록과 세트를 한 번에 읽는다.
     */
    public GroupDetailResponse detail(Long userId, Long groupId) {
        FriendGroup group = mustFindMyGroup(userId, groupId);
        List<GroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(groupId);
        List<Long> memberIds = members.stream().map(GroupMember::getUserId).toList();
        Map<Long, String> nicknames = nicknames(memberIds);

        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WorkoutSession> week = sessionRepository.findRecorded(memberIds, weekStart, weekStart.plusDays(6));
        Map<Long, DayTimes> times = workoutService.timesOf(week);
        Map<Long, List<WorkoutSession>> weekByMember = week.stream()
                .collect(Collectors.groupingBy(WorkoutSession::getUserId));
        Map<Long, WorkoutSession> todayByMember = week.stream()
                .filter(session -> session.getPerformedOn().isEqual(today))
                .collect(Collectors.toMap(WorkoutSession::getUserId, Function.identity()));
        CheerTally cheers = tally(userId, todayByMember.values().stream().map(WorkoutSession::getId).toList());

        List<MemberActivity> activities = members.stream()
                .map(member -> {
                    Long memberId = member.getUserId();
                    return new MemberActivity(memberId, nicknames.get(memberId), group.isOwnedBy(memberId),
                            memberId.equals(userId), member.getJoinedAt(),
                            todayActivity(todayByMember.get(memberId), cheers),
                            weekActivity(weekByMember.getOrDefault(memberId, List.of()), times));
                })
                .toList();

        return new GroupDetailResponse(group.getId(), group.getName(), group.getInviteCode(),
                group.isOwnedBy(userId), today, weekStart, activities);
    }

    /** 이름 바꾸기(방장). */
    @Transactional
    public GroupDetailResponse rename(Long userId, Long groupId, String name) {
        mustFindOwnedGroup(userId, groupId).rename(cleanName(name));
        return detail(userId, groupId);
    }

    /** 초대코드 다시 만들기(방장). 옛 코드·링크로는 더 들어올 수 없다. */
    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long userId, Long groupId) {
        FriendGroup group = mustFindOwnedGroup(userId, groupId);
        group.changeInviteCode(newInviteCode());
        return new InviteCodeResponse(group.getInviteCode());
    }

    /** 그룹 삭제(방장). 멤버십은 FK 연쇄 삭제로 함께 지워지고, 멤버들의 기록은 그대로다. */
    @Transactional
    public void delete(Long userId, Long groupId) {
        groupRepository.delete(lockOwnedGroup(userId, groupId));
    }

    // ── 초대·참여

    /** 초대 링크로 들어왔을 때 참여 전에 보여줄 그룹 정보. */
    public InvitePreview preview(Long userId, String code) {
        FriendGroup group = mustFindByCode(code);
        int memberCount = (int) memberRepository.countByGroupId(group.getId());
        String ownerNickname = userRepository.findById(group.getOwnerId()).map(User::getNickname).orElse(null);
        return new InvitePreview(group.getId(), group.getName(), memberCount, ownerNickname,
                memberRepository.existsByGroupIdAndUserId(group.getId(), userId),
                memberCount >= MAX_MEMBERS);
    }

    /**
     * 코드로 참여(UC-05-02). 이미 멤버면 오류가 아니라 그대로 그룹으로 보낸다(예외 흐름 3b) —
     * 초대 링크를 두 번 누르는 일은 흔하다.
     */
    @Transactional
    public JoinResponse join(Long userId, String code) {
        FriendGroup group = lock(mustFindByCode(code).getId());
        // 잠근 뒤에 세어야 같은 순간 들어온 두 사람이 둘 다 "19명"을 보고 들어오지 않는다.
        // 같은 사람이 링크를 연타해도 두 번째는 여기서 이미 멤버로 걸린다
        if (memberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            return new JoinResponse(group.getId(), group.getName(), true);
        }
        if (memberRepository.countByGroupId(group.getId()) >= MAX_MEMBERS) {
            throw new ApiException(ErrorCode.GROUP_LIMIT_EXCEEDED,
                    "그룹 인원이 가득 찼습니다. (최대 " + MAX_MEMBERS + "명)");
        }
        ensureCanJoinMore(userId);
        memberRepository.save(GroupMember.join(group.getId(), userId, OffsetDateTime.now(clock)));
        return new JoinResponse(group.getId(), group.getName(), false);
    }

    // ── 나가기·내보내기

    /**
     * 그룹 나가기(UC-05-04). 방장이 나가면 남은 멤버 중 가장 먼저 들어온 사람에게 방장을 넘긴다.
     * 마지막 한 명이 나가면 그룹을 지운다 — 멤버 없는 그룹은 아무도 볼 수 없다.
     */
    @Transactional
    public void leave(Long userId, Long groupId) {
        FriendGroup group = lockMyGroup(userId, groupId);
        if (group.isOwnedBy(userId) && !handOverOwnership(group, userId)) {
            return;   // 마지막 한 명이라 그룹째 지웠다
        }
        memberRepository.findByGroupIdAndUserId(groupId, userId).ifPresent(memberRepository::delete);
    }

    /** 멤버 내보내기(방장). 방장 자신은 나가기로 나간다. */
    @Transactional
    public void removeMember(Long userId, Long groupId, Long memberId) {
        lockOwnedGroup(userId, groupId);
        if (memberId.equals(userId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "방장은 자신을 내보낼 수 없습니다. 그룹 나가기를 이용해주세요.");
        }
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, memberId)
                .orElseThrow(() -> ApiException.notFound("멤버"));
        memberRepository.delete(member);
    }

    /**
     * 회원 탈퇴 직전에 부른다. 방장인 그룹은 다음 멤버에게 넘기고, 혼자였으면 지운다.
     *
     * <p>{@code friend_groups.owner_id}에는 연쇄 삭제를 걸지 않았다 — 걸면 방장 한 명의 탈퇴로
     * 남은 멤버들의 그룹이 통째로 사라진다. 이 메서드를 빠뜨리면 FK가 탈퇴를 막는다.
     */
    @Transactional
    public void releaseOwnership(Long userId) {
        for (FriendGroup owned : groupRepository.findByOwnerId(userId)) {
            handOverOwnership(lock(owned.getId()), userId);
        }
        groupRepository.flush();
    }

    /**
     * 방장이 떠날 때 — 남은 멤버 중 가장 먼저 들어온 사람에게 방장을 넘긴다. 남은 사람이 없으면
     * 그룹을 지운다. 나가기와 탈퇴가 같은 규칙을 쓴다. 그룹은 잠근 채로 넘겨받는다.
     *
     * @return 넘겼으면 true, 그룹을 지웠으면 false
     */
    private boolean handOverOwnership(FriendGroup group, Long leavingUserId) {
        Optional<GroupMember> successor =
                memberRepository.findFirstByGroupIdAndUserIdNotOrderByJoinedAtAscIdAsc(group.getId(), leavingUserId);
        if (successor.isEmpty()) {
            groupRepository.delete(group);
            return false;
        }
        group.transferOwnership(successor.get().getUserId());
        return true;
    }

    // ── 멤버 기록 (읽기 전용)

    /** 멤버의 월별 기록 요약. 내 캘린더(명세 6.8)와 같은 모양이다. */
    public CalendarResponse memberCalendar(Long userId, Long groupId, Long memberId, int year, int month) {
        mustBeMember(groupId, userId, "그룹");
        mustBeMember(groupId, memberId, "멤버");
        return workoutService.calendar(memberId, year, month);
    }

    /** 멤버의 하루 기록. 세트까지 보이고 메모는 빠진다. 운동하지 않은 날이면 기록이 비어 있다. */
    public MemberDayResponse memberDay(Long userId, Long groupId, Long memberId, LocalDate date) {
        mustBeMember(groupId, userId, "그룹");
        mustBeMember(groupId, memberId, "멤버");
        String nickname = userRepository.findById(memberId).map(User::getNickname).orElse(null);

        Optional<SessionResponse> day = workoutService.dayOf(memberId, date);
        if (day.isEmpty()) {
            return new MemberDayResponse(memberId, nickname, date, null);
        }
        SessionResponse session = day.get();
        CheerTally cheers = tally(userId, List.of(session.id()));
        // 종목 카드에 동작 그림을 붙이려면 영문명이 있어야 한다. 그날 종목을 한 번에 읽는다
        Map<Long, String> namesEn = exerciseRepository.findAllById(session.exercises().stream()
                        .map(ExerciseGroup::exerciseId)
                        .toList())
                .stream()
                .filter(exercise -> exercise.getNameEn() != null)
                .collect(Collectors.toMap(Exercise::getId, Exercise::getNameEn));
        return new MemberDayResponse(memberId, nickname, date, toMemberSession(session, cheers, namesEn));
    }

    // ── 응원

    /** 응원. 이미 눌렀으면 그대로 둔다 — 두 번 눌러도 한 번이다. */
    @Transactional
    public CheerResponse cheer(Long userId, Long groupId, Long sessionId) {
        WorkoutSession session = mustFindMemberSession(userId, groupId, sessionId);
        if (session.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "내 운동에는 응원할 수 없습니다.");
        }
        cheerRepository.insertIfAbsent(sessionId, userId);
        return new CheerResponse((int) cheerRepository.countBySessionId(sessionId), true);
    }

    /** 응원 취소. 누르지 않았어도 오류가 아니다. */
    @Transactional
    public CheerResponse cancelCheer(Long userId, Long groupId, Long sessionId) {
        mustFindMemberSession(userId, groupId, sessionId);
        cheerRepository.deleteBySessionIdAndUserId(sessionId, userId);
        return new CheerResponse((int) cheerRepository.countBySessionId(sessionId), false);
    }

    // ── 조회 도우미

    /** 멤버가 아니면 그룹이 있는지조차 드러내지 않는다 — 404다. */
    private FriendGroup mustFindMyGroup(Long userId, Long groupId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw ApiException.notFound("그룹");
        }
        return groupRepository.findById(groupId).orElseThrow(() -> ApiException.notFound("그룹"));
    }

    private FriendGroup mustFindOwnedGroup(Long userId, Long groupId) {
        FriendGroup group = mustFindMyGroup(userId, groupId);
        if (!group.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "방장만 할 수 있습니다.");
        }
        return group;
    }

    /** 멤버인지만 본다. 나 자신이면 "그룹"이 없는 것으로, 남이면 "멤버"가 없는 것으로 알린다 */
    private void mustBeMember(Long groupId, Long userId, String what) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw ApiException.notFound(what);
        }
    }

    /** 멤버가 바뀌는 일 앞에서 그룹을 잠근다(동시 참여·나가기, {@link FriendGroupRepository#findForUpdate}) */
    private FriendGroup lock(Long groupId) {
        return groupRepository.findForUpdate(groupId).orElseThrow(() -> ApiException.notFound("그룹"));
    }

    private FriendGroup lockMyGroup(Long userId, Long groupId) {
        mustBeMember(groupId, userId, "그룹");
        return lock(groupId);
    }

    private FriendGroup lockOwnedGroup(Long userId, Long groupId) {
        FriendGroup group = lockMyGroup(userId, groupId);
        if (!group.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "방장만 할 수 있습니다.");
        }
        return group;
    }

    /**
     * 그룹 이름. 앞뒤 공백을 걷는다. {@code @NotBlank}는 ASCII 공백만 보므로, 한글 입력기의
     * 전각 공백(U+3000)만 넣은 이름은 여기서 걸러야 빈 이름이 저장되지 않는다.
     */
    private static String cleanName(String name) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "그룹 이름을 입력해주세요.");
        }
        return cleaned;
    }

    /** 같은 그룹 멤버의 기록만. 그룹 밖 사람의 기록은 있는지조차 드러내지 않는다. */
    private WorkoutSession mustFindMemberSession(Long userId, Long groupId, Long sessionId) {
        mustBeMember(groupId, userId, "그룹");
        return sessionRepository.findById(sessionId)
                .filter(session -> memberRepository.existsByGroupIdAndUserId(groupId, session.getUserId()))
                .orElseThrow(() -> ApiException.notFound("운동 기록"));
    }

    private FriendGroup mustFindByCode(String code) {
        return groupRepository.findByInviteCode(InviteCode.normalize(code))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "초대코드를 찾을 수 없습니다. 코드를 다시 확인해주세요."));
    }

    private void ensureCanJoinMore(Long userId) {
        if (memberRepository.countByUserId(userId) >= MAX_GROUPS_PER_USER) {
            throw new ApiException(ErrorCode.GROUP_LIMIT_EXCEEDED,
                    "그룹은 " + MAX_GROUPS_PER_USER + "개까지 들어갈 수 있습니다.");
        }
    }

    /** 코드가 겹치면 다시 뽑는다. 8.9억 가지라 다섯 번 연속으로 겹칠 일은 없다. */
    private String newInviteCode() {
        for (int i = 0; i < INVITE_CODE_ATTEMPTS; i++) {
            String code = InviteCode.generate();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "초대코드를 만들지 못했습니다. 다시 시도해주세요.");
    }

    private Map<Long, String> nicknames(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    // ── 집계

    private static TodayActivity todayActivity(WorkoutSession session, CheerTally cheers) {
        if (session == null) {
            return new TodayActivity(TodayStatus.NONE, null, 0, false);
        }
        return new TodayActivity(TodayStatus.DONE, session.getId(),
                cheers.count(session.getId()), cheers.byMe(session.getId()));
    }

    /**
     * 이번 주 활동(기능명세 2.6) — 횟수는 운동한 날 수이고 사후 입력도 들어간다. 시간 합에서는
     * 시간이 없는 사후 입력이 빠진다(보정값을 넣었으면 들어간다).
     */
    private static WeekActivity weekActivity(List<WorkoutSession> days, Map<Long, DayTimes> times) {
        int totalDuration = days.stream()
                .map(day -> times.get(day.getId()).effectiveSec(day))
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        List<LocalDate> dates = days.stream().map(WorkoutSession::getPerformedOn).sorted().toList();
        return new WeekActivity(days.size(), totalDuration, dates);
    }

    private CheerTally tally(Long viewerId, Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return new CheerTally(Map.of(), Set.of());
        }
        Map<Long, Long> counts = cheerRepository.countBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(CheerCount::sessionId, CheerCount::count));
        Set<Long> mine = new HashSet<>(cheerRepository.findCheeredSessionIds(viewerId, sessionIds));
        return new CheerTally(counts, mine);
    }

    private static MemberSession toMemberSession(SessionResponse session, CheerTally cheers,
                                                 Map<Long, String> namesEn) {
        List<MemberExercise> exercises = session.exercises().stream()
                .map(exercise -> new MemberExercise(exercise.exerciseId(), exercise.exerciseName(),
                        namesEn.get(exercise.exerciseId()), exercise.measureType(),
                        exercise.sets().stream()
                                .map(set -> new MemberSet(set.setNo(), set.weightKg(), set.reps(),
                                        set.durationSec(), set.isWarmup()))
                                .toList()))
                .toList();
        return new MemberSession(session.id(), session.startedAt(),
                session.endedAt(), session.effectiveDurationSec(), exercises,
                cheers.count(session.id()), cheers.byMe(session.id()));
    }

    /** 화면에 보이는 세션들의 응원 수와, 그중 내가 누른 것 */
    private record CheerTally(Map<Long, Long> counts, Set<Long> mine) {

        int count(Long sessionId) {
            return counts.getOrDefault(sessionId, 0L).intValue();
        }

        boolean byMe(Long sessionId) {
            return mine.contains(sessionId);
        }
    }
}
