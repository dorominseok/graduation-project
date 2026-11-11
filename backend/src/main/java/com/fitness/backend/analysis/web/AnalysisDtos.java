package com.fitness.backend.analysis.web;

import com.fitness.backend.analysis.domain.BalancePair;
import com.fitness.backend.analysis.domain.BalanceSide;
import com.fitness.backend.analysis.domain.BalanceVerdict;
import com.fitness.backend.analysis.domain.ConfidenceLevel;
import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.RecoveryState;
import com.fitness.backend.analysis.domain.SummaryBadge;
import com.fitness.backend.analysis.domain.TierGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.routine.domain.RoutineExclusion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 분석 API 응답. 합산·구간 분류·배지·신뢰도는 서버가 완성해서 준다 — 화면은 그리기만 한다.
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
            @Schema(description = "주당 평균을 낼 때 나눈 일수. 기록이 4주 미만이면 첫 기록일부터 센 일수(최소 7), 아니면 28")
            int basisDays,
            @Schema(description = "basisDays의 시작일. periodTo - basisDays + 1")
            LocalDate basisFrom,
            @Schema(description = "어깨 앞뒤 구분이 선 상태인지. false면 화면이 어깨 판정을 참고치로 낮춰 표시한다")
            boolean shoulderSplitResolved,
            ConfidenceResponse confidence,
            @Schema(description = "상위 6종. 항상 6개, 고정 순서") List<TierResponse> tiers,
            @Schema(description = "종아리·전완. 판정하지 않고 세트 수만 준다") List<DisplayOnlyResponse> displayOnly) {
    }

    public record ConfidenceResponse(
            ConfidenceLevel level,
            int workoutDayCount,
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
            @Schema(description = "주당 평균을 낼 때 나눈 일수. muscle-volume과 같다") int basisDays,
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
            @Schema(description = "구성 부위의 주당 세트 합") BigDecimal weeklySets,
            @Schema(description = "부위당 주 평균 세트. 비율은 이 값끼리 맞댄다") BigDecimal perMuscleSets,
            @Schema(description = "합산에 쓴 판정 부위. 근거 표시용") List<MuscleGroup> components) {
    }

    /** 부위별 회복 상태(「회복지도_설계서」 4장). */
    public record RecoveryResponse(
            @Schema(description = "기준일(오늘)") LocalDate date,
            @Schema(description = "오늘 포함 이 일수 안에 한 부위는 회복 중 — 루틴의 회복 제외와 같은 값") int recoveryDays,
            @Schema(description = "회복 중 기간의 세트가 이 값 이상이면 HIGH — 루틴의 부위당 최대 세트") int heavySets,
            @Schema(description = "11개 부위. MuscleGroup 순서로 항상 모두 온다") List<RecoveryMuscleResponse> muscles) {
    }

    public record RecoveryMuscleResponse(
            MuscleGroup key,
            String label,
            @Schema(description = "부족 판정 대상인지. 종아리·전완은 false") boolean judged,
            RecoveryState state,
            String stateLabel,
            @Schema(description = "최근 4주 안의 마지막 운동일. 없으면 null") LocalDate lastTrainedOn,
            @Schema(description = "마지막 운동일부터 오늘까지 일수. 오늘 했으면 0, 없으면 null") Long daysSince,
            @Schema(description = "오늘 포함 recoveryDays일 동안의 본세트 수") long recentSets,
            @Schema(description = "회복 때문에 오늘 루틴에서 빠지는지. 루틴과 같은 함수로 정한다. 종아리·전완은 루틴이 원래 고르지 않아 늘 false")
            boolean routineExcluded,
            @Schema(description = "빠지는 이유. SELF_RECOVERING 이 부위가 회복 중 / BUNDLE_RECOVERING 같은 묶음의 다른 부위가 회복 중. 안 빠지면 null")
            RoutineExclusion.Reason routineExclusionReason,
            @Schema(description = "BUNDLE_RECOVERING일 때 같은 묶음에서 회복 중인 부위. 그 밖에는 빈 배열")
            List<MuscleGroup> routineExcludedBy) {
    }
}
