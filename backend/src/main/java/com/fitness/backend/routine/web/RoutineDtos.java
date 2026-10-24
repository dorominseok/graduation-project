package com.fitness.backend.routine.web;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.routine.domain.RoutineEnums.Author;
import com.fitness.backend.routine.domain.RoutineEnums.FeedbackRating;
import com.fitness.backend.routine.domain.RoutineEnums.FeedbackReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 오늘의 루틴 API의 요청·응답. */
public final class RoutineDtos {

    private RoutineDtos() {
    }

    /**
     * 하루 루틴.
     *
     * @param lowConfidence 기록이 적어 판정 신뢰도가 낮다 — 화면이 "기록이 쌓일수록…" 안내를 띄운다
     * @param totalSets     빼지 않은 항목의 계획 세트 합
     * @param doneSets      빼지 않은 항목마다 min(그날 체크한 세트, 계획)의 합 — 진행 표시용
     * @param askFeedback   오늘 루틴이고, 루틴 부위를 체크했고, 아직 답하지 않았다
     */
    public record RoutineResponse(Long id,
                                  LocalDate routineDate,
                                  LocalDate referenceDate,
                                  Author generatedBy,
                                  String comment,
                                  boolean lowConfidence,
                                  int totalSets,
                                  int doneSets,
                                  List<RoutineItemResponse> items,
                                  FeedbackState feedback,
                                  boolean askFeedback) {
    }

    /**
     * 루틴 항목.
     *
     * @param targetMin       반복 범위. 시간 종목은 초다
     * @param basisWeeklySets 근거 — 만든 날의 최근 4주 주당 평균
     * @param doneSets        그날 이 종목의 체크한 본세트 수
     * @param adjusted        교체·세트 조정·빼기 중 하나라도 했다
     */
    public record RoutineItemResponse(Long id,
                                      int position,
                                      MuscleGroup muscleGroup,
                                      String muscleLabel,
                                      Long exerciseId,
                                      String exerciseName,
                                      String exerciseNameEn,
                                      MeasureType measureType,
                                      Equipment equipment,
                                      int plannedSets,
                                      int targetMin,
                                      int targetMax,
                                      boolean firstTime,
                                      BigDecimal basisWeeklySets,
                                      VolumeVerdict basisVerdict,
                                      String basisVerdictLabel,
                                      boolean removed,
                                      boolean adjusted,
                                      int doneSets) {
    }

    public record FeedbackState(FeedbackRating rating, FeedbackReason reason, boolean skipped) {
    }

    /** 후보정 — 종목 교체와 세트 조정. 보내지 않은 항목은 그대로 둔다 */
    public record UpdateItemRequest(Long exerciseId,
                                    @Min(value = 1, message = "1 이상이어야 합니다.")
                                    @Max(value = 10, message = "10 이하여야 합니다.") Integer plannedSets) {
    }

    /** 만족도. 답하거나({@code rating}, 보통·별로면 {@code reason}) 건너뛴다({@code skipped: true}) */
    public record FeedbackRequest(FeedbackRating rating, FeedbackReason reason, Boolean skipped) {
    }

    /**
     * 교체 후보. 같은 부위에서 기구가 다른 종목이 먼저 온다.
     *
     * @param sameEquipment 지금 종목과 기구가 같다 — 화면이 묶어서 뒤에 보인다
     */
    public record AlternativeResponse(Long exerciseId,
                                      String exerciseName,
                                      String exerciseNameEn,
                                      Equipment equipment,
                                      MeasureType measureType,
                                      boolean firstTime,
                                      boolean sameEquipment) {
    }

    /** 생성 결과. {@code created}가 {@code 201}과 {@code 200}을 가른다 */
    public record CreateResult(RoutineResponse routine, boolean created) {
    }
}
