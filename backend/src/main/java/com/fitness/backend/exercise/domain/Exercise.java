package com.fitness.backend.exercise.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동 종목. 부록 A {@code exercises} (V1 + V2 + V3).
 *
 * <p><b>읽기 전용이다.</b> 8월 2주에 정제해 {@code R__seed_exercises.sql}로 넣었고,
 * 애플리케이션에는 종목을 만들거나 고치는 경로가 없다(명세 5.1). 그래서 변경
 * 메서드를 두지 않는다 — 즐겨찾기만 사용자별로 {@link UserFavoriteExercise}에 쓴다.
 *
 * <p>enum은 {@code EnumType.STRING}으로 저장한다. 순서 기반({@code ORDINAL})은
 * 상수를 중간에 추가하는 순간 기존 행의 의미가 바뀌고, 스키마의 CHECK 제약도
 * 문자열을 전제한다.
 */
@Entity
@Getter
@Table(name = "exercises")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 한글 명칭. 종목의 자연키이며 UNIQUE (V3). */
    @Column(name = "name_ko", nullable = false, length = 100)
    private String nameKo;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false, length = 20)
    private BodyPart bodyPart;

    /** 주동근 원자값({@code chest}, {@code lats} …). 판정 부위 9종의 산출 근거(명세 8.1). */
    @Column(name = "primary_muscle", length = 50)
    private String primaryMuscle;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_pull", length = 10)
    private PushPull pushPull;

    @Enumerated(EnumType.STRING)
    @Column(name = "measure_type", nullable = false, length = 30)
    private MeasureType measureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Equipment equipment;

    /** {@code primaryMuscle}이 {@code shoulders}인 종목만 값을 갖는다(스키마 CHECK로도 강제). */
    @Enumerated(EnumType.STRING)
    @Column(name = "delt_region", length = 10)
    private DeltRegion deltRegion;
}
