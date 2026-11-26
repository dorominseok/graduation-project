package com.fitness.backend.exercise.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.BrowseCategory;
import com.fitness.backend.exercise.domain.CustomTarget.Side;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.domain.PushPull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 운동 종목 API의 요청·응답. */
public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    /**
     * 종목 한 건.
     *
     * <p>{@code isFavorite}는 <b>인증된 요청에만</b> 담는다. 비인증 요청에서는
     * {@code null}이 되고 {@link JsonInclude}가 키 자체를 뺀다. {@code false}로 내리면
     * "즐겨찾기가 아니다"로 읽혀 로그인 여부와 구분되지 않는다.
     *
     * <p>{@code mine}은 사용자가 직접 만든 종목(「내종목_설계서」). 남의 내 종목은 내려가지 않으므로
     * {@code true}면 언제나 요청한 사람의 것이다.
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
                                   boolean mine,
                                   Boolean isFavorite) {

        public static ExerciseResponse of(Exercise e, Boolean isFavorite) {
            return new ExerciseResponse(e.getId(), e.getNameKo(), e.getNameEn(),
                    e.getBodyPart(), e.getPrimaryMuscle(), e.getPushPull(),
                    e.getMeasureType(), e.getEquipment(), e.getDeltRegion(),
                    e.getGroupName(), e.isCustom(), isFavorite);
        }
    }

    /**
     * 내 종목 만들기·고치기. 고치기도 같은 값을 모두 보낸다 — 화면은 값이 채워진 같은 창을 연다.
     *
     * @param category  부위 고르기 화면과 같은 14개
     * @param side      {@code category}가 하체·목일 때만, 그때는 필수. 예전 이름 {@code legSide}도 받는다 —
     *                  배포 직후 예전 화면이 열려 있어도 하체 종목 만들기가 깨지지 않게
     * @param groupName 계열 안에서 만들 때 그 계열. 그 분류에 있는 계열이어야 한다. 없으면 "내 종목" 칸
     */
    public record CustomExerciseRequest(
            @NotBlank(message = "이름을 적어 주세요.")
            @Size(max = 50, message = "이름은 50자까지예요.")
            String nameKo,
            @NotNull(message = "부위를 골라 주세요.") BrowseCategory category,
            @JsonAlias("legSide") Side side,
            @NotNull(message = "기구를 골라 주세요.") Equipment equipment,
            @NotNull(message = "기록 방식을 골라 주세요.") MeasureType measureType,
            String groupName) {
    }

    /**
     * 부위 그리드의 한 칸.
     *
     * <p>종목이 0개인 분류도 내려간다 — 칸이 사라지면 그리드 자리가 밀려
     * 위치로 기억한 사용자가 다시 찾아야 한다.
     */
    public record CategoryCount(BrowseCategory category, String label, long count) {
    }

    /**
     * 계열 목록의 한 칸.
     *
     * <p>{@code representativeId}는 카드에 그림을 붙일 때 쓸 대표 종목이다.
     * {@code representativeNameEn}은 그 그림의 파일명을 만드는 데 쓴다 — 파일명이
     * 영문명 슬러그라서 id만으로는 어느 그림인지 알 수 없다.
     *
     * <p>{@code mine}이면 계열 없이 만든 내 종목을 모은 칸이다 — {@code groupName}·대표 종목은 {@code null}이고,
     * 화면이 "내 종목" 가로 칸으로 그린다. 목록은 {@code GET /exercises?category=…&ungroupedMine=true}.
     */
    public record GroupCount(String groupName, long count,
                             Long representativeId, String representativeNameEn, boolean mine) {
    }
}
