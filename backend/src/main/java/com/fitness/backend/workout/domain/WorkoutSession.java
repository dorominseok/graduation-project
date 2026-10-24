package com.fitness.backend.workout.domain;

import com.fitness.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 하루 운동 기록.
 *
 * <p>하루에 하나다({@code (user_id, performed_on)} 유니크). 시작·종료가 없다 — 체크한 세트는
 * 그 자리에서 저장되고, 마지막으로 체크한 세트가 곧 끝이다. 아침·저녁에 두 번 운동해도 같은
 * 기록에 쌓이고, 운동 시간은 공백에서 구간을 나눠 합한다({@link SessionDuration}).
 *
 * <p>테이블 이름은 {@code workout_sessions}이지만 뜻은 하루 기록이다. 세트와 응원이
 * {@code session_id}로 가리키고 있어 이름을 그대로 두었다.
 *
 * <p>세트를 {@code @OneToMany}로 달지 않고 {@link WorkoutSet}이 {@code sessionId}를
 * 값으로 갖는다. 이 저장소의 다른 엔티티와 같은 방식이며, 세트가 필요한 곳은
 * "이 날의 세트 목록"뿐이라 리포지토리에서 명시적으로 읽는 편이 쿼리가 드러난다.
 *
 * <p>시작·끝 시각과 운동 시간은 저장하지 않는다. 세트 저장 시각으로 읽을 때 계산한다 —
 * 세트를 고칠 때마다 저장값을 맞출 필요가 없다.
 */
@Entity
@Getter
@Table(name = "workout_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    /** 그날 루틴. 루틴을 받은 날만 채워진다 */
    @Column(name = "routine_id")
    private Long routineId;

    /** 사용자가 직접 넣은 운동 시간 보정값. 있으면 산출값보다 우선한다 */
    @Column(name = "duration_override_sec")
    private Integer durationOverrideSec;

    @Column(columnDefinition = "TEXT")
    private String memo;

    /** 그날 루틴을 잇는다. 루틴과 하루 기록은 날짜로 1:1이다 */
    public void linkRoutine(Long routineId) {
        this.routineId = routineId;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }

    /** 날짜 정정. 옮길 날짜에 기록이 이미 있는지는 호출 전에 본다 */
    public void changePerformedOn(LocalDate performedOn) {
        this.performedOn = performedOn;
    }

    /** 사용자가 직접 넣는 운동 시간. {@code null}이면 보정을 해제하고 산출값으로 돌아간다 */
    public void changeDurationOverrideSec(Integer durationOverrideSec) {
        this.durationOverrideSec = durationOverrideSec;
    }
}
