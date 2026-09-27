package com.fitness.backend.workout.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.LastPerformanceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 종목 프리필(명세 5.5).
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
    @GetMapping("/{exerciseId}/last-performance")
    public ResponseEntity<LastPerformanceResponse> lastPerformance(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId) {
        return workoutService.findLastPerformance(principal.userId(), exerciseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
