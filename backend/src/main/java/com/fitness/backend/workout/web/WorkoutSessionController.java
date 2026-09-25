package com.fitness.backend.workout.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSessionRequest;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.SessionResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SetSaveResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 운동 기록. 명세 6.2~6.7.
 *
 * <p>전부 인증이 필요하다 — 기록은 사용자의 것이고, 조회 경로도 {@code userId}로
 * 한정해 남의 세션이 존재하는지조차 드러나지 않게 한다.
 */
@ApiV1Controller
@RequestMapping("/workout-sessions")
public class WorkoutSessionController {

    private final WorkoutService workoutService;

    public WorkoutSessionController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /** 세션 생성(명세 6.2). */
    @PostMapping
    public ResponseEntity<SessionResponse> create(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = workoutService.createSession(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 세트 저장(명세 6.4).
     *
     * <p>새로 저장하면 {@code 201}, 같은 {@code clientSetId}로 이미 있던 것이면 {@code 200}이다.
     */
    @PostMapping("/{sessionId}/sets")
    public ResponseEntity<WorkoutDtos.SetResponse> addSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateSetRequest request) {
        SetSaveResult result = workoutService.addSet(principal.userId(), sessionId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.set());
    }

    /** 세션 종료(명세 6.3). 본문 없이 호출한다. */
    @PostMapping("/{sessionId}/complete")
    public SessionResponse complete(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        return workoutService.complete(principal.userId(), sessionId);
    }

    /**
     * 진행 중 세션(명세 6.7).
     *
     * <p>없으면 {@code 204}다 — {@code 404}로 하면 "경로가 없다"와 구분되지 않고,
     * 빈 객체를 내리면 클라이언트가 매번 필드를 들여다봐야 한다.
     */
    @GetMapping("/current")
    public ResponseEntity<SessionResponse> current(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return workoutService.findCurrent(principal.userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 세션 상세(명세 6.5). */
    @GetMapping("/{sessionId}")
    public SessionResponse get(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        return workoutService.getSession(principal.userId(), sessionId);
    }
}
