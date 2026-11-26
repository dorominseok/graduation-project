package com.fitness.backend.importing.web;

import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.importing.domain.ExerciseMatcher.Guess;
import com.fitness.backend.importing.domain.MatchStatus;
import com.fitness.backend.importing.domain.SetKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 기록 가져오기 API의 요청·응답(「기록가져오기_설계서」 7장, 명세 13장). */
public final class ImportDtos {

    private ImportDtos() {
    }

    // ── 미리보기

    public record PreviewRequest(
            @NotBlank(message = "필수 항목입니다.")
            @Size(max = 100_000, message = "한 번에 10만 자까지 붙여넣을 수 있어요.") String text) {
    }

    /** 날짜 순. 유산소·미래 날짜는 없다 */
    public record PreviewResponse(List<PreviewDay> days) {
    }

    /** @param minutes 요약 줄의 {@code N분}. 없으면 null */
    public record PreviewDay(LocalDate date, Integer minutes, List<PreviewExercise> exercises) {
    }

    /**
     * 종목 하나와 맞춘 결과.
     *
     * @param exercise            맞춘 종목. 미등록 운동이면 null
     * @param guess               미등록 운동일 때만 — 내 종목 만들기 창을 미리 채울 값
     * @param allowedMeasureTypes 이 세트를 담을 수 있는 기록 방식 — "바꾸기"·"비슷한 종목 고르기"는 이 방식의 종목만 보인다
     * @param sets                글에 적힌 값 그대로. 고른 종목이 쓰지 않는 값은 화면이 보이지 않고 저장할 때 버린다
     */
    public record PreviewExercise(String source, MatchStatus status, ExerciseBrief exercise, Guess guess,
                                  List<MeasureType> allowedMeasureTypes, List<ImportSet> sets) {
    }

    public record ExerciseBrief(Long id, String nameKo, MeasureType measureType, boolean mine) {

        public static ExerciseBrief of(Exercise exercise) {
            return new ExerciseBrief(exercise.getId(), exercise.getNameKo(), exercise.getMeasureType(),
                    exercise.isCustom());
        }
    }

    /** 세트 하나. 미리보기 응답과 저장 요청이 같은 꼴이다 — 화면은 받은 그대로 돌려보낸다 */
    public record ImportSet(
            @NotNull(message = "필수 항목입니다.") SetKind kind,
            @DecimalMin(value = "0.0", message = "0 이상이어야 합니다.")
            @Digits(integer = 4, fraction = 2, message = "소수점 두 자리까지 입력할 수 있습니다.")
            BigDecimal weightKg,
            @Max(value = Short.MAX_VALUE, message = "32767 이하여야 합니다.")
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer reps,
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer durationSec,
            @Max(value = Short.MAX_VALUE, message = "32767 이하여야 합니다.")
            @Min(value = 2, message = "2 이상이어야 합니다.") Integer targetReps) {
    }

    // ── 저장

    public record ImportRequest(
            @NotEmpty(message = "필수 항목입니다.")
            @Size(max = 31, message = "한 번에 31일까지 가져올 수 있어요.")
            List<@Valid @NotNull ImportDay> days) {
    }

    /** @param minutes 미리보기의 {@code minutes} 그대로. 그날 기록이 비어 있었을 때만 운동 시간이 된다 */
    public record ImportDay(
            @NotNull(message = "필수 항목입니다.") LocalDate date,
            @Min(value = 0, message = "0 이상이어야 합니다.")
            @Max(value = 1440, message = "1440 이하여야 합니다.") Integer minutes,
            @NotEmpty(message = "필수 항목입니다.")
            @Size(max = 100, message = "하루 100종목까지예요.")
            List<@Valid @NotNull ImportExercise> exercises) {
    }

    /**
     * @param source     미리보기의 원래 이름 그대로 — 세트의 고정 번호와 맞춘 이름 기억에 쓴다
     * @param exerciseId 넣을 종목. 미등록 운동은 화면이 먼저 내 종목을 만들어 그 id를 넣는다
     * @param remember   "바꾸기"로 고쳤거나 미리보기에서 만든 종목이면 true — 이 짝을 기억한다
     */
    public record ImportExercise(
            @NotBlank(message = "필수 항목입니다.")
            @Size(max = 200, message = "200자 이하여야 합니다.") String source,
            @NotNull(message = "필수 항목입니다.") Long exerciseId,
            Boolean remember,
            @NotEmpty(message = "필수 항목입니다.")
            @Size(max = 100, message = "한 종목에 100세트까지예요.")
            List<@Valid @NotNull ImportSet> sets) {

        public boolean rememberOrDefault() {
            return Boolean.TRUE.equals(remember);
        }
    }

    public record ImportResponse(List<ImportedDay> days) {
    }

    /**
     * @param added        새로 넣은 세트 수
     * @param alreadyThere 같은 글을 전에 넣어 이미 있던 세트 수
     */
    public record ImportedDay(LocalDate date, Long sessionId, int added, int alreadyThere) {
    }
}
