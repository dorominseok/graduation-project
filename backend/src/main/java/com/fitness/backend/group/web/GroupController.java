package com.fitness.backend.group.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.group.service.GroupService;
import com.fitness.backend.group.web.GroupDtos.CheerResponse;
import com.fitness.backend.group.web.GroupDtos.CreateGroupRequest;
import com.fitness.backend.group.web.GroupDtos.GroupDetailResponse;
import com.fitness.backend.group.web.GroupDtos.GroupSummary;
import com.fitness.backend.group.web.GroupDtos.InviteCodeResponse;
import com.fitness.backend.group.web.GroupDtos.InvitePreview;
import com.fitness.backend.group.web.GroupDtos.JoinGroupRequest;
import com.fitness.backend.group.web.GroupDtos.JoinResponse;
import com.fitness.backend.group.web.GroupDtos.MemberDayResponse;
import com.fitness.backend.group.web.GroupDtos.RenameGroupRequest;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 친구 그룹.
 *
 * <p>전부 인증이 필요하다. 초대 링크도 로그인한 뒤에 열린다 — 참여하려면 어차피 계정이 있어야 한다.
 */
@ApiV1Controller
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(summary = "그룹 만들기")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "만든 사람이 방장이자 첫 멤버. 초대코드가 함께 나온다"),
            @ApiResponse(responseCode = "409", description = "들어간 그룹이 이미 10개 (GROUP_LIMIT_EXCEEDED)")
    })
    @PostMapping
    public ResponseEntity<GroupDetailResponse> create(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.create(principal.userId(), request.name()));
    }

    @Operation(summary = "내 그룹 목록")
    @GetMapping
    public List<GroupSummary> myGroups(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return groupService.myGroups(principal.userId());
    }

    @Operation(summary = "그룹 상세 — 멤버별 오늘·이번 주 활동")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "멤버는 들어온 순서"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 멤버가 아님")
    })
    @GetMapping("/{groupId}")
    public GroupDetailResponse detail(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @PathVariable Long groupId) {
        return groupService.detail(principal.userId(), groupId);
    }

    @Operation(summary = "그룹 이름 바꾸기 (방장)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "바뀐 그룹 상세"),
            @ApiResponse(responseCode = "403", description = "방장이 아님")
    })
    @PatchMapping("/{groupId}")
    public GroupDetailResponse rename(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @PathVariable Long groupId,
                                      @Valid @RequestBody RenameGroupRequest request) {
        return groupService.rename(principal.userId(), groupId, request.name());
    }

    @Operation(summary = "그룹 삭제 (방장)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨. 멤버들의 운동 기록은 그대로다"),
            @ApiResponse(responseCode = "403", description = "방장이 아님")
    })
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                       @PathVariable Long groupId) {
        groupService.delete(principal.userId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "초대코드 다시 만들기 (방장)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새 코드. 옛 코드와 링크는 더 쓸 수 없다"),
            @ApiResponse(responseCode = "403", description = "방장이 아님")
    })
    @PostMapping("/{groupId}/invite-code")
    public InviteCodeResponse regenerateInviteCode(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long groupId) {
        return groupService.regenerateInviteCode(principal.userId(), groupId);
    }

    @Operation(summary = "초대 미리보기 — 참여 전에 그룹 이름·인원을 보여준다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "그룹 정보. 이미 멤버인지, 가득 찼는지 함께"),
            @ApiResponse(responseCode = "404", description = "없는 코드")
    })
    @GetMapping("/invite/{code}")
    public InvitePreview preview(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                 @PathVariable String code) {
        return groupService.preview(principal.userId(), code);
    }

    @Operation(summary = "초대코드로 참여")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "참여함"),
            @ApiResponse(responseCode = "200", description = "이미 멤버 — 오류가 아니라 그룹으로 보낸다"),
            @ApiResponse(responseCode = "404", description = "없는 코드"),
            @ApiResponse(responseCode = "409", description = "그룹 인원 20명 또는 내 그룹 10개 초과 (GROUP_LIMIT_EXCEEDED)")
    })
    @PostMapping("/join")
    public ResponseEntity<JoinResponse> join(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                             @Valid @RequestBody JoinGroupRequest request) {
        JoinResponse response = groupService.join(principal.userId(), request.code());
        return ResponseEntity.status(response.alreadyMember() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "그룹 나가기")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "나감. 방장이면 가장 먼저 들어온 멤버가 방장이 되고, 마지막 한 명이면 그룹이 지워진다")
    })
    @DeleteMapping("/{groupId}/members/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @PathVariable Long groupId) {
        groupService.leave(principal.userId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버 내보내기 (방장)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "내보냄"),
            @ApiResponse(responseCode = "400", description = "방장 자신 — 나가기를 쓴다"),
            @ApiResponse(responseCode = "403", description = "방장이 아님")
    })
    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                             @PathVariable Long groupId,
                                             @PathVariable Long memberId) {
        groupService.removeMember(principal.userId(), groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버의 월별 기록 요약 (읽기 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 캘린더와 같은 모양"),
            @ApiResponse(responseCode = "404", description = "그룹 멤버가 아님")
    })
    @GetMapping("/{groupId}/members/{memberId}/calendar")
    public CalendarResponse memberCalendar(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                           @PathVariable Long groupId,
                                           @PathVariable Long memberId,
                                           // 터무니없는 연도는 날짜 계산에서 예외가 나 500이 된다. 400으로 막는다
                                           @RequestParam @Min(2000) @Max(2100) int year,
                                           @RequestParam @Min(1) @Max(12) int month) {
        return groupService.memberCalendar(principal.userId(), groupId, memberId, year, month);
    }

    @Operation(summary = "멤버의 하루 기록 (읽기 전용, 메모 제외)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "그날의 운동과 세트. 기록이 없으면 빈 목록"),
            @ApiResponse(responseCode = "404", description = "그룹 멤버가 아님")
    })
    @GetMapping("/{groupId}/members/{memberId}/days/{date}")
    public MemberDayResponse memberDay(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                       @PathVariable Long groupId,
                                       @PathVariable Long memberId,
                                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return groupService.memberDay(principal.userId(), groupId, memberId, date);
    }

    @Operation(summary = "응원하기")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "응원 후 수. 두 번 눌러도 한 번이다"),
            @ApiResponse(responseCode = "400", description = "내 운동"),
            @ApiResponse(responseCode = "404", description = "그룹 멤버의 운동이 아님")
    })
    @PutMapping("/{groupId}/sessions/{sessionId}/cheer")
    public CheerResponse cheer(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                               @PathVariable Long groupId,
                               @PathVariable Long sessionId) {
        return groupService.cheer(principal.userId(), groupId, sessionId);
    }

    @Operation(summary = "응원 취소")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 후 수. 누르지 않았어도 오류가 아니다")
    })
    @DeleteMapping("/{groupId}/sessions/{sessionId}/cheer")
    public CheerResponse cancelCheer(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                     @PathVariable Long groupId,
                                     @PathVariable Long sessionId) {
        return groupService.cancelCheer(principal.userId(), groupId, sessionId);
    }
}
