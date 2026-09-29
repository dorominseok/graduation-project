package com.fitness.backend.stats.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.stats.service.StatsService;
import com.fitness.backend.stats.web.StatsDtos.OneRmTrendResponse;
import com.fitness.backend.stats.web.StatsDtos.SessionIntensityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 통계. 명세 7장.
 *
 * <p>추정값은 전부 서버가 낸다. 화면이 Epley 공식을 들고 있으면 공식을 바꿀 때
 * 배포된 앱마다 다른 1RM을 그리게 되고, 어느 쪽이 맞는지 알 수 없다.
 */
@ApiV1Controller
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "추정 1RM 추이",
            description = "종목 하나의 날짜별 추정 1RM. 그날 세트 중 최댓값을 점으로 찍는다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "날짜 오름차순. 기록이 없으면 points가 빈 배열"),
            @ApiResponse(responseCode = "400", description = "중량·횟수를 쓰지 않는 종목이거나 날짜 범위가 뒤집힘"),
            @ApiResponse(responseCode = "404", description = "없는 종목")
    })
    @GetMapping("/one-rm-trend")
    public OneRmTrendResponse oneRmTrend(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam Long exerciseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return statsService.oneRmTrend(principal.userId(), exerciseId, from, to);
    }

    @Operation(summary = "세션 강도 스냅샷",
            description = "그 세션의 각 세트가 그날 추정 1RM의 몇 %였는지")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "종목별로 묶은 세트 목록. 1RM을 낼 수 없는 종목은 null"),
            @ApiResponse(responseCode = "404", description = "없거나 남의 세션")
    })
    @GetMapping("/session-intensity/{sessionId}")
    public SessionIntensityResponse sessionIntensity(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long sessionId) {

        return statsService.sessionIntensity(principal.userId(), sessionId);
    }
}
