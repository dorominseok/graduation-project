package com.fitness.backend.routine;

import com.fitness.backend.routine.domain.RoutinePolicy;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 루틴 생성의 설정값(`app.routine.*`). 「루틴생성_로직_설계서」 13장.
 *
 * <p>규칙은 순수 계산({@code routine.domain})에 두고, 조정 가능한 수치만 여기서 넘긴다.
 * 분석 설정({@code app.analysis.*})과 같은 구조다. 값이 어긋나면 기동 시점에 실패한다.
 */
@Validated
@ConfigurationProperties(prefix = "app.routine")
public record RoutineProperties(
        @Min(1) int targetWeeklySets,
        @Min(1) int perMuscleMinSets,
        @Min(1) int perMuscleMaxSets,
        @Min(1) int fillMaxSets,
        @Min(1) int dailyMinSets,
        @Min(1) int dailyMaxSets,
        @Min(0) int recoveryDays,
        @Min(1) int defaultDaysPerWeek,
        @Min(0) int minKnownDays,
        @Min(1) int candidatesPerMuscle,
        @Min(1) int historyWeeks,
        @Min(0) int historyMaxCandidates,
        /** 만족도를 물을 지난 루틴의 범위(일). 이보다 오래된 루틴은 묻지 않는다 */
        @Min(1) int feedbackLookbackDays) {

    /** 과다 경계는 분석 설정에서 받는다 — 판정과 루틴 검증(R2)이 같은 값을 써야 한다 */
    public RoutinePolicy policy(BigDecimal optimalThrough) {
        return new RoutinePolicy(targetWeeklySets, perMuscleMinSets, perMuscleMaxSets, fillMaxSets,
                dailyMinSets, dailyMaxSets, recoveryDays, defaultDaysPerWeek, minKnownDays, optimalThrough);
    }
}
