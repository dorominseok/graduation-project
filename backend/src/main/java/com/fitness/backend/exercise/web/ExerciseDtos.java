package com.fitness.backend.exercise.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.domain.PushPull;

/** 운동 종목 API의 응답. 명세 5.2~5.3. */
public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    /**
     * 종목 한 건.
     *
     * <p>{@code isFavorite}는 <b>인증된 요청에만</b> 담는다. 비인증 요청에서는
     * {@code null}이 되고 {@link JsonInclude}가 키 자체를 뺀다 — 명세 5.2가
     * "비인증 요청에선 키 생략"으로 정했다. {@code false}로 내리면 "즐겨찾기가
     * 아니다"로 읽혀 로그인 여부와 구분되지 않는다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExerciseResponse(Long id,
                                   String nameKo,
                                   String nameEn,
                                   BodyPart bodyPart,
                                   String primaryMuscle,
                                   PushPull pushPull,
                                   MeasureType measureType,
                                   Equipment equipment,
                                   DeltRegion deltRegion,
                                   Boolean isFavorite) {

        public static ExerciseResponse of(Exercise e, Boolean isFavorite) {
            return new ExerciseResponse(e.getId(), e.getNameKo(), e.getNameEn(),
                    e.getBodyPart(), e.getPrimaryMuscle(), e.getPushPull(),
                    e.getMeasureType(), e.getEquipment(), e.getDeltRegion(), isFavorite);
        }
    }
}
