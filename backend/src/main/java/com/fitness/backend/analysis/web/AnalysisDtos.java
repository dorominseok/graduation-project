package com.fitness.backend.analysis.web;

import com.fitness.backend.analysis.domain.BalancePair;
import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.BalanceVerdict;
import com.fitness.backend.analysis.domain.ConfidenceLevel;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.SummaryBadge;
import com.fitness.backend.analysis.domain.TierGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 분석 API 응답. 명세 8장.
 *
 * <p>화면은 합산·구간 분류·배지 결정·신뢰도 판정을 하지 않는다(8.2 「화면이 하지
 * 않아도 되는 것」). 같은 판정을 서버와 화면 양쪽에 두면 둘이 어긋나는 날이 오고,
 * 그때 어느 쪽이 맞는지 가릴 방법이 없다.
 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    @Schema(description = "부위별 볼륨과 4단계 부족 판정")
    public record MuscleVolumeResponse(
            LocalDate referenceDate,
            int periodWeeks,
            @Schema(description = "집계 구간 시작일. 양끝 포함이라 periodTo - periodFrom + 1 = weeks × 7")
            LocalDate periodFrom,
            LocalDate periodTo,
            @Schema(description = "어깨 앞뒤 구분이 선 상태인지. false면 화면이 어깨 판정을 참고치로 낮춰 표시한다")
            boolean shoulderSplitResolved,
            ConfidenceResponse confidence,
            @Schema(description = "상위 6종. 항상 6개, 고정 순서") List<TierResponse> tiers,
            @Schema(description = "종아리·전완. 판정하지 않고 세트 수만 준다") List<DisplayOnlyResponse> displayOnly) {
    }

    public record ConfidenceResponse(
            ConfidenceLevel level,
            int doneSessionCount,
            int threshold,
            @Schema(description = "LOW일 때만 채운다. 충분하면 화면에 띄울 말이 없다")
            String message) {
    }

    public record TierResponse(
            TierGroup key,
            String label,
            @Schema(description = "하위 합의 주당 평균. 소수 1자리") BigDecimal weeklySets,
            long totalSets,
            boolean hasChildren,
            @Schema(description = "하위가 1개일 때만 값. 2개면 null — 상위엔 판정을 붙이지 않는다")
            VolumeVerdict verdict,
            String verdictLabel,
            @Schema(description = "하위가 2개일 때만 값. 하위 중 가장 나쁜 상태 요약")
            SummaryBadge summaryBadge,
            String summaryBadgeLabel,
            @Schema(description = "실제 판정 단위. 여기엔 verdict가 항상 있다") List<ChildResponse> children) {
    }

    public record ChildResponse(
            MuscleGroup key,
            String label,
            BigDecimal weeklySets,
            long totalSets,
            VolumeVerdict verdict,
            String verdictLabel) {
    }

    public record DisplayOnlyResponse(
            MuscleGroup key,
            String label,
            BigDecimal weeklySets,
            long totalSets) {
    }

    @Schema(description = "밀기/당기기 · 상체/하체 균형")
    public record BalanceResponse(
            LocalDate referenceDate,
            int periodWeeks,
            @Schema(description = "이 배수를 넘으면 불균형") BigDecimal ratioThreshold,
            boolean shoulderSplitResolved,
            List<PairResponse> pairs) {
    }

    public record PairResponse(
            BalancePair key,
            String label,
            SideResponse left,
            SideResponse right,
            @Schema(description = "세트가 많은 쪽. 양쪽 다 0이면 null") BalanceSide biggerSide,
            @Schema(description = "큰 쪽 ÷ 작은 쪽, 소수 2자리. 작은 쪽이 0이면 null") BigDecimal ratio,
            @Schema(description = "true면 화면은 비율 대신 '계산 불가'로 적는다") boolean smallerSideZero,
            BalanceVerdict verdict,
            String verdictLabel) {
    }

    public record SideResponse(
            BalanceSide key,
            String label,
            BigDecimal weeklySets,
            @Schema(description = "합산에 쓴 판정 부위. 근거 표시용") List<MuscleGroup> components) {
    }
}
