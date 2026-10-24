package com.fitness.backend.routine.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.routine.service.RoutineService;
import com.fitness.backend.routine.web.RoutineDtos.AlternativeResponse;
import com.fitness.backend.routine.web.RoutineDtos.CreateResult;
import com.fitness.backend.routine.web.RoutineDtos.FeedbackRequest;
import com.fitness.backend.routine.web.RoutineDtos.RoutineResponse;
import com.fitness.backend.routine.web.RoutineDtos.UpdateItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * 오늘의 루틴.
 *
 * <p>전부 인증이 필요하다. 남의 루틴은 있는지조차 드러내지 않도록 404다.
 */
@ApiV1Controller
@RequestMapping("/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @Operation(summary = "오늘 루틴 만들기")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "오늘 첫 루틴을 만듦"),
            @ApiResponse(responseCode = "200", description = "오늘 루틴이 이미 있어 그 루틴을 줌 — 다시 만들기는 없다")
    })
    @PostMapping("/today")
    public ResponseEntity<RoutineResponse> createToday(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        CreateResult result = routineService.createToday(principal.userId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.routine());
    }

    @Operation(summary = "오늘 루틴")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "오늘 루틴"),
            @ApiResponse(responseCode = "204", description = "아직 받지 않음")
    })
    @GetMapping("/today")
    public ResponseEntity<RoutineResponse> today(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return routineService.today(principal.userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "만족도를 물을 지난 루틴")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "루틴 부위를 체크했고 답하지 않은 가장 최근 루틴"),
            @ApiResponse(responseCode = "204", description = "물을 루틴 없음")
    })
    @GetMapping("/feedback-pending")
    public ResponseEntity<RoutineResponse> feedbackPending(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return routineService.feedbackPending(principal.userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "루틴 상세")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "루틴"),
            @ApiResponse(responseCode = "404", description = "없는 루틴 또는 남의 루틴")
    })
    @GetMapping("/{routineId}")
    public RoutineResponse get(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                               @PathVariable Long routineId) {
        return routineService.get(principal.userId(), routineId);
    }

    @Operation(summary = "후보정 — 종목 교체·세트 조정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "바뀐 루틴. 원래 값은 항목에 남는다"),
            @ApiResponse(responseCode = "400", description = "다른 부위의 종목이거나 지난 루틴")
    })
    @PatchMapping("/{routineId}/items/{itemId}")
    public RoutineResponse updateItem(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @PathVariable Long routineId,
                                      @PathVariable Long itemId,
                                      @Valid @RequestBody UpdateItemRequest request) {
        return routineService.updateItem(principal.userId(), routineId, itemId, request);
    }

    @Operation(summary = "후보정 — 종목 빼기")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "바뀐 루틴. 항목은 지우지 않고 removed로 남는다"),
            @ApiResponse(responseCode = "400", description = "지난 루틴")
    })
    @DeleteMapping("/{routineId}/items/{itemId}")
    public RoutineResponse removeItem(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @PathVariable Long routineId,
                                      @PathVariable Long itemId) {
        return routineService.removeItem(principal.userId(), routineId, itemId);
    }

    @Operation(summary = "교체 후보")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "같은 부위 — 기구가 다른 종목이 먼저")
    })
    @GetMapping("/{routineId}/items/{itemId}/alternatives")
    public List<AlternativeResponse> alternatives(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                                  @PathVariable Long routineId,
                                                  @PathVariable Long itemId) {
        return routineService.alternatives(principal.userId(), routineId, itemId);
    }

    @Operation(summary = "만족도")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록됨"),
            @ApiResponse(responseCode = "400", description = "만족도도 건너뛰기도 없음"),
            @ApiResponse(responseCode = "409", description = "이미 답했거나 건너뛴 루틴 (ROUTINE_FEEDBACK_EXISTS)")
    })
    @PutMapping("/{routineId}/feedback")
    public RoutineResponse feedback(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                    @PathVariable Long routineId,
                                    @RequestBody FeedbackRequest request) {
        return routineService.feedback(principal.userId(), routineId, request);
    }
}
