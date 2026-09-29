package com.fitness.backend.stats.web;

import com.fitness.backend.exercise.domain.MeasureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 통계 API 응답. 명세 7장. */
public final class StatsDtos {

    private StatsDtos() {
    }

    @Schema(description = "종목 하나의 추정 1RM 추이")
    public record OneRmTrendResponse(
            Long exerciseId,
            String exerciseName,
            @Schema(description = "표시 단위. 현재는 kg 고정") String unit,
            @Schema(description = "실제 집계 시작일") LocalDate from,
            @Schema(description = "실제 집계 종료일") LocalDate to,
            @Schema(description = "날짜 오름차순. 기록이 없는 날은 아예 빠진다 — 선을 끊지 않고 이어 그리라는 뜻")
            List<TrendPoint> points) {
    }

    public record TrendPoint(
            LocalDate date,
            @Schema(description = "그날 세트들의 Epley 추정값 중 최댓값. 소수 1자리")
            BigDecimal estimatedOneRm,
            @Schema(description = "그 최댓값을 만든 세트. 화면이 근거를 보여줄 때 쓴다")
            BasedOnSet basedOnSet) {
    }

    public record BasedOnSet(BigDecimal weightKg, int reps) {
    }

    @Schema(description = "세션 하나의 세트별 강도 스냅샷")
    public record SessionIntensityResponse(
            Long sessionId,
            LocalDate performedOn,
            List<ExerciseIntensity> exercises) {
    }

    public record ExerciseIntensity(
            Long exerciseId,
            String exerciseName,
            MeasureType measureType,
            @Schema(description = "그날 그 종목의 추정 1RM. WEIGHT_REPS가 아니거나 대상 세트가 없으면 null")
            BigDecimal estimatedOneRm,
            List<IntensitySet> sets) {
    }

    public record IntensitySet(
            short setNo,
            BigDecimal weightKg,
            Short reps,
            Integer durationSec,
            boolean isWarmup,
            @Schema(description = "그날 추정 1RM 대비 중량 비율(%). 워밍업도 표시한다")
            Integer intensityPct) {
    }
}
