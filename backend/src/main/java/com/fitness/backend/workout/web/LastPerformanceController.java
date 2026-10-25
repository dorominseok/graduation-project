package com.fitness.backend.workout.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.common.web.PageResponse;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.ExerciseHistoryDay;
import com.fitness.backend.workout.web.WorkoutDtos.LastPerformanceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 종목별 개인 기록 — 직전 수행(프리필)과 날짜별 기록.
 *
 * <p>경로는 종목 아래지만 데이터는 운동 기록에서 나오므로 {@code workout} 패키지에 둔다.
 * {@code exercise}가 {@code workout}을 의존하면 방향이 뒤집혀 순환이 된다.
 *
 * <p>개인 기록이라 {@code GET /exercises}와 달리 인증이 필요하다.
 */
@ApiV1Controller
@RequestMapping("/exercises")
public class LastPerformanceController {

    private final WorkoutService workoutService;

    public LastPerformanceController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /** 그 종목을 마지막으로 수행한 기록. 처음 하는 종목이면 {@code 204}다. */
    @Operation(summary = "직전 수행 기록 (프리필용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "마지막으로 그 종목을 한 날의 세트"),
            @ApiResponse(responseCode = "204", description = "처음 하는 종목 — 오류가 아니라 정상 상태다"),
            @ApiResponse(responseCode = "404", description = "없는 종목")
    })
    @GetMapping("/{exerciseId}/last-performance")
    public ResponseEntity<LastPerformanceResponse> lastPerformance(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId) {
        return workoutService.findLastPerformance(principal.userId(), exerciseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 그 종목을 한 날마다 그날의 세트. 정렬은 운동 날짜 최신순으로 고정이다 */
    @Operation(summary = "종목 기록 (날짜별 세트)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "그 종목을 한 날만, 최신순. 기록이 없으면 빈 페이지"),
            @ApiResponse(responseCode = "404", description = "없는 종목")
    })
    @GetMapping("/{exerciseId}/history")
    public PageResponse<ExerciseHistoryDay> history(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.from(
                workoutService.exerciseHistory(principal.userId(), exerciseId, PageRequest.of(page, size)));
    }
}
