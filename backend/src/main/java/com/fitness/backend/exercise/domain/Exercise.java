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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동 종목.
 *
 * <p>두 가지가 한 테이블에 있다.
 * <ul>
 *   <li><b>기본 종목</b>({@code ownerId == null}) — 종목 CSV에서 만든 {@code R__seed_exercises.sql}로 넣는다.
 *       애플리케이션은 고치지 않는다.
 *   <li><b>내 종목</b>({@code ownerId}가 사용자) — 사용자가 직접 만든다(「내종목_설계서」). 만든 사람에게만 보이고,
 *       기록이 있는 채로 지우면 숨긴다({@code hiddenAt}). 같은 테이블이라 세트·분석·1RM이 손대지 않고 그대로 읽는다.
 * </ul>
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

    /** 한글 명칭. 기본 종목끼리, 그리고 한 사람의 내 종목끼리 겹치지 않는다(V16의 부분 유일 인덱스 둘). */
    @Column(name = "name_ko", nullable = false, length = 100)
    private String nameKo;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false, length = 20)
    private BodyPart bodyPart;

    /** 주동근 원자값({@code chest}, {@code lats} …). 판정 부위 9종의 산출 근거. */
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

    /**
     * 종목 계열. 같은 동작의 변형들을 한 묶음으로 본다.
     *
     * <p>{@code primaryMuscle}과 다른 축이다 — 저쪽은 "어느 근육을 쓰는가"(판정용),
     * 이쪽은 "어떤 동작인가"(탐색용)다. 벤치프레스와 체스트프레스는 같은 근육을 쓰지만
     * 사용자가 고를 때는 다른 동작으로 구분한다.
     */
    @Column(name = "group_name", length = 40)
    private String groupName;

    /**
     * 입문 대표 종목 순위. 부위마다 1~2개, 1이 먼저. 나머지는 null.
     *
     * <p>기록·즐겨찾기가 없는 사람에게 루틴 후보를 줄 때 시드 순서보다 앞에 둔다.
     */
    @Column(name = "starter_rank")
    private Short starterRank;

    /**
     * 헬스장에서 흔히 하는 정도. 1이 아주 흔함, 2가 흔함, 나머지는 null.
     *
     * <p>루틴 후보를 채울 때 입문 대표 종목 다음에 이 순서로 넣고, 종목 교체 목록도 이 순서로 보인다.
     */
    @Column(name = "common_rank")
    private Short commonRank;

    /** 내 종목의 주인. 기본 종목은 {@code null} */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 기록이 있는 내 종목을 지운 시각. 숨긴 종목은 목록에 안 나오고 새 세트를 받지 않지만, 지난 기록의 이름은 남는다 */
    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    /** 내 종목. 그림·영문명·흔한 정도·입문 순위는 없다 */
    public static Exercise custom(Long ownerId, String nameKo, CustomTarget target, Equipment equipment,
                                  MeasureType measureType, String groupName) {
        Exercise exercise = new Exercise();
        exercise.ownerId = ownerId;
        exercise.change(nameKo, target, equipment, measureType, groupName);
        return exercise;
    }

    /** 내 종목 고치기. 고치기도 만들기와 같은 값을 모두 받는다 */
    public void change(String nameKo, CustomTarget target, Equipment equipment, MeasureType measureType,
                       String groupName) {
        if (ownerId == null) {
            throw new IllegalStateException("기본 종목은 고치지 않는다");
        }
        this.nameKo = nameKo;
        this.primaryMuscle = target.primaryMuscle();
        this.deltRegion = target.deltRegion();
        this.bodyPart = target.bodyPart();
        this.pushPull = target.pushPull();
        this.equipment = equipment;
        this.measureType = measureType;
        this.groupName = groupName;
    }

    /** 기록이 있는 내 종목을 지울 때 */
    public void hide(OffsetDateTime now) {
        if (ownerId == null) {
            throw new IllegalStateException("기본 종목은 숨기지 않는다");
        }
        this.hiddenAt = now;
    }

    /** 숨긴 이름으로 다시 만들면 되살린다 — 지난 기록이 다시 이어진다 */
    public void restore() {
        this.hiddenAt = null;
    }

    public boolean isCustom() {
        return ownerId != null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public boolean ownedBy(Long userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    /** 이 사용자가 고르고 열 수 있는가 — 기본 종목, 또는 숨기지 않은 자기 종목 */
    public boolean visibleTo(Long userId) {
        return ownerId == null || (ownedBy(userId) && hiddenAt == null);
    }
}
