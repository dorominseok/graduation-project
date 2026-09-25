package com.fitness.backend.workout.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.common.web.PageResponse;
import com.fitness.backend.workout.domain.SessionStatus;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarResponse;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSessionRequest;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.SessionResponse;
import com.fitness.backend.workout.web.WorkoutDtos.SessionSummary;
import com.fitness.backend.workout.web.WorkoutDtos.SetSaveResult;
import com.fitness.backend.workout.web.WorkoutDtos.UpdateSessionRequest;
import com.fitness.backend.workout.web.WorkoutDtos.UpdateSetRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 운동 기록. 명세 6.2~6.7.
 *
 * <p>전부 인증이 필요하다 — 기록은 사용자의 것이고, 조회 경로도 {@code userId}로
 * 한정해 남의 세션이 존재하는지조차 드러나지 않게 한다.
 */
@ApiV1Controller
@Validated
@RequestMapping("/workout-sessions")
public class WorkoutSessionController {

    /** 명세 6.6 — from·to를 생략했을 때의 조회 범위 */
    private static final int DEFAULT_HISTORY_DAYS = 30;

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

    /**
     * 히스토리 목록(명세 6.6). 세트는 담지 않는 요약이다.
     *
     * <p>{@code from}·{@code to}를 생략하면 최근 30일이다. 전체를 훑는 것이 기본이 되면
     * 기록이 쌓일수록 느려지고, 화면도 기본 진입에서 그만큼을 보여주지 않는다.
     */
    @GetMapping
    public PageResponse<SessionSummary> history(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) Long exerciseId,
            @PageableDefault(size = 20, sort = "performedOn", direction = Sort.Direction.DESC)
            Pageable pageable) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_HISTORY_DAYS);
        return PageResponse.from(
                workoutService.history(principal.userId(), fromDate, toDate, status, exerciseId, pageable));
    }

    /** 캘린더 월별 요약(명세 6.8). */
    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return workoutService.calendar(principal.userId(), year, month);
    }

    /** 세션 상세(명세 6.5). */
    @GetMapping("/{sessionId}")
    public SessionResponse get(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        return workoutService.getSession(principal.userId(), sessionId);
    }

    /** 세션 수정(명세 6.9). 메모·날짜·시간 보정. */
    @PatchMapping("/{sessionId}")
    public SessionResponse update(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return workoutService.updateSession(principal.userId(), sessionId, request);
    }

    /** 세션 삭제(명세 6.10). 세트도 함께 지워진다. */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        workoutService.deleteSession(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** 세트 수정(명세 6.11). */
    @PatchMapping("/{sessionId}/sets/{setId}")
    public WorkoutDtos.SetResponse updateSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long setId,
            @Valid @RequestBody UpdateSetRequest request) {
        return workoutService.updateSet(principal.userId(), sessionId, setId, request);
    }

    /** 세트 삭제(명세 6.12). */
    @DeleteMapping("/{sessionId}/sets/{setId}")
    public ResponseEntity<Void> deleteSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long setId) {
        workoutService.deleteSet(principal.userId(), sessionId, setId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 종목 단위 삭제(LOG-22). 그 종목의 세트를 한 번에 지운다.
     *
     * <p>세트를 하나씩 지우는 것과 결과는 같지만, 요청이 한 번이라 중간에 끊겨
     * 일부만 지워지는 상태가 생기지 않는다.
     */
    @DeleteMapping("/{sessionId}/exercises/{exerciseId}")
    public ResponseEntity<Void> deleteExercise(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long exerciseId) {
        workoutService.deleteExercise(principal.userId(), sessionId, exerciseId);
        return ResponseEntity.noContent().build();
    }
}
