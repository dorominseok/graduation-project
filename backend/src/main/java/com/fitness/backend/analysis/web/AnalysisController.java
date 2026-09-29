package com.fitness.backend.analysis.web;

import com.fitness.backend.analysis.service.AnalysisService;
import com.fitness.backend.analysis.web.AnalysisDtos.BalanceResponse;
import com.fitness.backend.analysis.web.AnalysisDtos.MuscleVolumeResponse;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 약점 판정. 명세 8장.
 *
 * <p>두 엔드포인트 모두 판정을 <b>완성해서</b> 내려준다 — 화면은 합산도 구간 분류도
 * 하지 않는다(8.2). 같은 판정이 서버와 화면 양쪽에 있으면 언젠가 둘이 어긋나고,
 * 그때 사용자에게 보인 숫자가 맞는지 확인할 방법이 없다.
 *
 * <p>{@code weeks}·{@code referenceDate}는 테스트와 과거 시점 조회용이다. 평소에는
 * 생략하면 설정값(4주)과 오늘이 쓰인다.
 */
@ApiV1Controller
@RequestMapping("/analysis")
public class AnalysisController {

    /** 집계 기간 상한. 넘겨도 계산은 되지만 "최근 경향"이라 부를 수 없는 범위다. */
    private static final int MAX_WEEKS = 52;

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "부위별 볼륨·부족 판정",
            description = "상위 6종 · 하위 9종 2계층과 신뢰도. 판정 단위는 하위 9종이다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록이 없어도 6개 부위가 모두 0세트로 내려온다"),
            @ApiResponse(responseCode = "400", description = "weeks가 범위를 벗어남")
    })
    @GetMapping("/muscle-volume")
    public MuscleVolumeResponse muscleVolume(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam(required = false) @Min(1) @Max(MAX_WEEKS) Integer weeks,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {

        return analysisService.muscleVolume(principal.userId(), weeks, referenceDate);
    }

    @Operation(summary = "밀기/당기기 · 상체/하체 균형",
            description = "볼륨과 같은 재료로 두 쌍의 비율을 낸다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "양쪽 다 0이면 INSUFFICIENT_DATA — 균형이 맞는 것이 아니다"),
            @ApiResponse(responseCode = "400", description = "weeks가 범위를 벗어남")
    })
    @GetMapping("/balance")
    public BalanceResponse balance(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam(required = false) @Min(1) @Max(MAX_WEEKS) Integer weeks,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {

        return analysisService.balance(principal.userId(), weeks, referenceDate);
    }
}
