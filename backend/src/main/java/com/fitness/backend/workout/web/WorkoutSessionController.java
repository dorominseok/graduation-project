package com.fitness.backend.workout.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.common.web.PageResponse;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.CalendarResponse;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayRequest;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayResult;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 운동 기록.
 *
 * <p>한 기록이 사용자의 하루다. 시작·종료가 없다 — 그날 기록을 열고 세트를 체크하면 끝이다.
 *
 * <p>전부 인증이 필요하다 — 기록은 사용자의 것이고, 조회 경로도 {@code userId}로
 * 한정해 남의 기록이 존재하는지조차 드러나지 않게 한다.
 */
@ApiV1Controller
@RequestMapping("/workout-sessions")
public class WorkoutSessionController {

    /** from·to를 생략했을 때의 조회 범위 */
    private static final int DEFAULT_HISTORY_DAYS = 30;

    private final WorkoutService workoutService;

    public WorkoutSessionController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /** 그날 기록 열기. */
    @Operation(summary = "그날 기록 열기")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "그날 첫 기록을 만듦 — 세트는 완료 체크할 때 저장된다"),
            @ApiResponse(responseCode = "200", description = "그날 기록이 이미 있어 그 기록을 줌"),
            @ApiResponse(responseCode = "400", description = "미래 날짜")
    })
    @PostMapping
    public ResponseEntity<SessionResponse> open(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody OpenDayRequest request) {
        OpenDayResult result = workoutService.openDay(principal.userId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
    }

    /**
     * 세트 저장.
     *
     * <p>새로 저장하면 {@code 201}, 같은 {@code clientSetId}로 이미 있던 것이면 {@code 200}이다.
     */
    @Operation(summary = "세트 저장")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새로 저장됨 — 순번은 서버가 매긴다"),
            @ApiResponse(responseCode = "200", description = "같은 clientSetId로 이미 저장된 세트 — 재전송이라 다시 만들지 않는다"),
            @ApiResponse(responseCode = "400", description = "종목이 요구하는 값이 없음"),
            @ApiResponse(responseCode = "404", description = "없는 기록 또는 종목")
    })
    @PostMapping("/{sessionId}/sets")
    public ResponseEntity<WorkoutDtos.SetResponse> addSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateSetRequest request) {
        SetSaveResult result = workoutService.addSet(principal.userId(), sessionId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.set());
    }

    /**
     * 히스토리 목록. 세트는 담지 않는 요약이다.
     *
     * <p>{@code from}·{@code to}를 생략하면 최근 30일이다. 전체를 훑는 것이 기본이 되면
     * 기록이 쌓일수록 느려지고, 화면도 기본 진입에서 그만큼을 보여주지 않는다.
     */
    @Operation(summary = "기록 목록")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "운동한 날만. 세트는 담지 않고 개수와 종목 목록만 준다")
    })
    @GetMapping
    public PageResponse<SessionSummary> history(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long exerciseId,
            @PageableDefault(size = 20, sort = "performedOn", direction = Sort.Direction.DESC)
            Pageable pageable) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_HISTORY_DAYS);
        return PageResponse.from(
                workoutService.history(principal.userId(), fromDate, toDate, exerciseId, pageable));
    }

    /** 캘린더 월별 요약. */
    @Operation(summary = "월별 기록 요약")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "운동한 날짜만. 달력 화면의 데이터")
    })
    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return workoutService.calendar(principal.userId(), year, month);
    }

    /** 하루 기록 상세. */
    @Operation(summary = "하루 기록 상세")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "종목별로 묶고 수행 순서대로 준다"),
            @ApiResponse(responseCode = "404", description = "없는 기록 또는 남의 기록")
    })
    @GetMapping("/{sessionId}")
    public SessionResponse get(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        return workoutService.getSession(principal.userId(), sessionId);
    }

    /** 하루 기록 수정. 메모·날짜·시간 보정. */
    @Operation(summary = "하루 기록 수정 (메모·날짜·시간 보정)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정된 기록"),
            @ApiResponse(responseCode = "400", description = "미래 날짜"),
            @ApiResponse(responseCode = "409", description = "옮길 날짜에 기록이 이미 있음 (DAY_ALREADY_RECORDED)")
    })
    @PatchMapping("/{sessionId}")
    public SessionResponse update(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return workoutService.updateSession(principal.userId(), sessionId, request);
    }

    /** 하루 기록 삭제. 세트도 함께 지워진다. */
    @Operation(summary = "하루 기록 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "기록과 세트가 함께 삭제됨"),
            @ApiResponse(responseCode = "404", description = "없는 기록")
    })
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {
        workoutService.deleteSession(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** 세트 수정. */
    @Operation(summary = "세트 값 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정된 세트 — 측정 유형이 쓰지 않는 값은 무시한다"),
            @ApiResponse(responseCode = "404", description = "없는 세트")
    })
    @PatchMapping("/{sessionId}/sets/{setId}")
    public WorkoutDtos.SetResponse updateSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long setId,
            @Valid @RequestBody UpdateSetRequest request) {
        return workoutService.updateSet(principal.userId(), sessionId, setId, request);
    }

    /** 세트 삭제. */
    @Operation(summary = "세트 하나 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨 — 마지막 세트를 지워도 기록은 남는다"),
            @ApiResponse(responseCode = "404", description = "없는 세트")
    })
    @DeleteMapping("/{sessionId}/sets/{setId}")
    public ResponseEntity<Void> deleteSet(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long setId) {
        workoutService.deleteSet(principal.userId(), sessionId, setId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 종목 단위 삭제. 그 종목의 세트를 한 번에 지운다.
     *
     * <p>세트를 하나씩 지우는 것과 결과는 같지만, 요청이 한 번이라 중간에 끊겨
     * 일부만 지워지는 상태가 생기지 않는다.
     */
    @Operation(summary = "기록에서 종목 빼기")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "그 종목의 세트가 한 번에 삭제됨 — 다른 종목은 남는다"),
            @ApiResponse(responseCode = "404", description = "그 기록에 없는 종목")
    })
    @DeleteMapping("/{sessionId}/exercises/{exerciseId}")
    public ResponseEntity<Void> deleteExercise(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId,
            @PathVariable Long exerciseId) {
        workoutService.deleteExercise(principal.userId(), sessionId, exerciseId);
        return ResponseEntity.noContent().build();
    }
}
