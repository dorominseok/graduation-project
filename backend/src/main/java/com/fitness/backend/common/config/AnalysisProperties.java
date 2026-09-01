package com.fitness.backend.common.config;

import com.fitness.backend.analysis.domain.VolumeThresholds;
import com.fitness.backend.workout.domain.DurationPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 분석·통계 계산의 설정값. API 명세서 8.5.
 *
 * <p>명세가 "모든 값은 {@code app.analysis.*} 프로퍼티로 노출해 코드 수정 없이
 * 조정 가능하게 한다"고 정했다. 판정 로직은 순수 함수로 두고 이 클래스가 값 객체를
 * 만들어 넘기는 구조라, 계산 규칙과 조정 가능한 수치가 분리된다.
 *
 * <p>{@code @Validated}를 붙여 잘못된 설정이면 <b>기동 시점에</b> 실패하게 한다.
 * 임계값이 어긋난 채로 뜨면 판정이 조용히 틀리고, 그 사실은 화면에 드러나지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(

        /** 판정 집계 기간(주). 기준일 포함 {@code weeks × 7}일을 집계한다(8.2). */
        @Min(1) int weeks,

        /** 부족 판정 임계 — 주당 세트. */
        @NotNull Volume volume,

        /** 균형 판정 비율 임계. 큰 쪽 ÷ 작은 쪽이 이 값을 초과하면 불균형(4.2). */
        @NotNull @Positive BigDecimal balanceRatioThreshold,

        /** 최근 {@code weeks}주 완료 세션이 이 값 미만이면 신뢰도 낮음(기록 방식 5.5). */
        @Min(1) int confidenceSessionThreshold,

        /** 추정 1RM 산출 대상 반복 횟수 상한(LOG-07). */
        @Min(1) int oneRmMaxReps,

        /** 운동 시간 산출 보정(기록 방식 4.2). */
        @NotNull Duration interSetCap,
        @NotNull Duration lastSetBonus,
        @NotNull Duration sessionCap) {

    /**
     * @param insufficientBelow 미만이면 부족
     * @param optimalFrom       이상부터 최적
     * @param optimalThrough    이하까지 최적, 초과하면 과다
     */
    public record Volume(@NotNull BigDecimal insufficientBelow,
                         @NotNull BigDecimal optimalFrom,
                         @NotNull BigDecimal optimalThrough) {
    }

    /** 판정 계산에 넘길 값 객체. 생성자에서 경계 순서가 검증된다. */
    public VolumeThresholds volumeThresholds() {
        return new VolumeThresholds(volume.insufficientBelow(), volume.optimalFrom(), volume.optimalThrough());
    }

    /** 운동 시간 산출에 넘길 값 객체. */
    public DurationPolicy durationPolicy() {
        return new DurationPolicy(interSetCap, lastSetBonus, sessionCap);
    }

    /** 집계 구간의 시작일 오프셋. 기준일을 포함하므로 하루를 뺀다(8.2, LOG-13). */
    public int periodDaysBack() {
        return weeks * 7 - 1;
    }
}
