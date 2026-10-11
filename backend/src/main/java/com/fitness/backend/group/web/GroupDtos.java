package com.fitness.backend.group.web;

import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.group.domain.FriendGroup;
import com.fitness.backend.group.domain.TodayStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 친구 그룹 API의 요청·응답. 명세 10장(LOG-38). */
public final class GroupDtos {

    private GroupDtos() {
    }

    /** 그룹 만들기. 이름은 앞뒤 공백을 걷어 1~20자 */
    public record CreateGroupRequest(
            @NotBlank(message = "필수 항목입니다.")
            @Size(max = FriendGroup.NAME_MAX_LENGTH, message = "20자 이하여야 합니다.")
            String name) {
    }

    /** 이름 변경(방장). 만들 때와 같은 규칙이다 */
    public record RenameGroupRequest(
            @NotBlank(message = "필수 항목입니다.")
            @Size(max = FriendGroup.NAME_MAX_LENGTH, message = "20자 이하여야 합니다.")
            String name) {
    }

    /** 코드로 참여. 소문자·공백·하이픈은 서버가 걸러 받는다 */
    public record JoinGroupRequest(@NotBlank(message = "필수 항목입니다.") String code) {
    }

    /** 내 그룹 목록 한 줄. 목록에서도 "오늘 몇 명 운동했나"가 보이게 한다 */
    public record GroupSummary(Long id,
                               String name,
                               int memberCount,
                               boolean isOwner,
                               int todayWorkedOutCount,
                               List<MemberPreview> members) {
    }

    /**
     * 목록 카드의 멤버 얼굴 하나. 그룹에 들어가기 전에도 누가 오늘 운동했는지 보이게 한다.
     * 멤버는 들어온 순서다.
     */
    public record MemberPreview(Long userId, String nickname, boolean isMe, TodayStatus todayStatus) {
    }

    /**
     * 그룹 상세 — 오늘과 이번 주의 멤버별 활동.
     *
     * <p>초대코드는 멤버 누구에게나 준다. 방장만 초대할 수 있으면 친구가 친구를 부를 때마다
     * 방장을 거쳐야 한다. 코드를 다시 만드는 것만 방장이 한다.
     *
     * @param weekStart 이번 주 월요일. 주는 월요일에 시작한다(명세 1.2, 홈의 "이번 주"와 같은 기준)
     */
    public record GroupDetailResponse(Long id,
                                      String name,
                                      String inviteCode,
                                      boolean isOwner,
                                      LocalDate today,
                                      LocalDate weekStart,
                                      List<MemberActivity> members) {
    }

    /** 멤버 한 명의 오늘·이번 주 */
    public record MemberActivity(Long userId,
                                 String nickname,
                                 boolean isOwner,
                                 boolean isMe,
                                 OffsetDateTime joinedAt,
                                 TodayActivity today,
                                 WeekActivity week) {
    }

    /**
     * 오늘의 운동. {@code sessionId}는 응원할 대상인 오늘 기록이고, 운동하지 않았으면 {@code null}이다.
     * 하루에 기록은 하나라 여러 번 운동해도 응원은 그 하루에 붙는다(LOG-39).
     */
    public record TodayActivity(TodayStatus status,
                                Long sessionId,
                                int cheerCount,
                                boolean cheeredByMe) {
    }

    /**
     * 이번 주(월~일) 활동(기능명세 2.6).
     *
     * @param dayCount         운동한 날 수. 사후 입력도 포함한다
     * @param totalDurationSec 운동 시간 합. 시간이 없는 사후 입력은 빠진다
     * @param workoutDates     운동한 날짜. 화면이 월~일 칸을 채운다
     */
    public record WeekActivity(int dayCount,
                               int totalDurationSec,
                               List<LocalDate> workoutDates) {
    }

    /**
     * 초대 링크로 들어왔을 때 참여 전에 보여줄 정보.
     *
     * @param alreadyMember 이미 멤버면 화면이 참여 대신 그룹으로 보낸다
     * @param full          인원이 가득 차 들어올 수 없다
     */
    public record InvitePreview(Long groupId,
                                String name,
                                int memberCount,
                                String ownerNickname,
                                boolean alreadyMember,
                                boolean full) {
    }

    /** 참여 결과. 이미 멤버였으면 {@code alreadyMember}가 참이고 상태 코드는 200이다 */
    public record JoinResponse(Long groupId, String name, boolean alreadyMember) {
    }

    public record InviteCodeResponse(String inviteCode) {
    }

    /** 응원을 누르거나 취소한 뒤의 상태. 화면이 숫자를 맞춘다 */
    public record CheerResponse(int cheerCount, boolean cheeredByMe) {
    }

    /** 멤버의 하루 기록(읽기 전용). 그날 운동하지 않았으면 {@code session}이 {@code null}이다 */
    public record MemberDayResponse(Long userId,
                                    String nickname,
                                    LocalDate date,
                                    MemberSession session) {
    }

    /**
     * 멤버의 하루 기록. 명세 6.5의 기록 상세에서 <b>메모를 뺀</b> 것이다(LOG-38).
     *
     * <p>종료가 없으므로 친구가 운동하는 중이면 그때까지 체크한 세트가 보인다(LOG-39).
     *
     * <p>메모는 "오늘 컨디션 최악" 같은 혼잣말일 수 있어 그룹에 보이지 않는다. 빈 값으로
     * 내리지 않고 필드 자체를 두지 않는다 — {@code null}이면 "메모가 없다"로 읽힌다.
     *
     * @param durationSec 화면에 보이는 운동 시간(보정값 우선). 시간 없는 사후 입력은 {@code null}
     */
    public record MemberSession(Long id,
                                OffsetDateTime startedAt,
                                OffsetDateTime endedAt,
                                Integer durationSec,
                                List<MemberExercise> exercises,
                                int cheerCount,
                                boolean cheeredByMe) {
    }

    /** 종목별 세트. 수행 순서다(명세 6.5와 같다) */
    public record MemberExercise(Long exerciseId,
                                 String exerciseName,
                                 /** 동작 그림 파일 이름을 만든다(화면의 exerciseImage). 그림이 없는 종목은 null */
                                 String exerciseNameEn,
                                 MeasureType measureType,
                                 List<MemberSet> sets) {
    }

    /** 세트. 편집할 수 없으므로 세트 id·클라이언트 키는 담지 않는다 */
    public record MemberSet(Short setNo,
                            BigDecimal weightKg,
                            Short reps,
                            Integer durationSec,
                            boolean isWarmup) {
    }
}
