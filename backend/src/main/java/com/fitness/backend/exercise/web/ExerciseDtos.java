package com.fitness.backend.exercise.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.BrowseCategory;
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
                                   String groupName,
                                   Boolean isFavorite) {

        public static ExerciseResponse of(Exercise e, Boolean isFavorite) {
            return new ExerciseResponse(e.getId(), e.getNameKo(), e.getNameEn(),
                    e.getBodyPart(), e.getPrimaryMuscle(), e.getPushPull(),
                    e.getMeasureType(), e.getEquipment(), e.getDeltRegion(),
                    e.getGroupName(), isFavorite);
        }
    }

    /**
     * 부위 그리드의 한 칸(LOG-24).
     *
     * <p>종목이 0개인 분류도 내려간다 — 칸이 사라지면 그리드 자리가 밀려
     * 위치로 기억한 사용자가 다시 찾아야 한다.
     */
    public record CategoryCount(BrowseCategory category, String label, long count) {
    }

    /**
     * 계열 목록의 한 칸(LOG-24).
     *
     * <p>{@code representativeId}는 카드에 그림을 붙일 때 쓸 대표 종목이다.
     * {@code representativeNameEn}은 그 그림의 파일명을 만드는 데 쓴다 — 파일명이
     * 영문명 슬러그라서 id만으로는 어느 그림인지 알 수 없다.
     */
    public record GroupCount(String groupName, long count,
                             Long representativeId, String representativeNameEn) {
    }
}
