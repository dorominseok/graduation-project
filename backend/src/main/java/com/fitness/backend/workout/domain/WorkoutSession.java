package com.fitness.backend.workout.domain;

import com.fitness.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 번의 운동. 부록 A {@code workout_sessions} (V1).
 *
 * <p>세트를 {@code @OneToMany}로 달지 않고 {@link WorkoutSet}이 {@code sessionId}를
 * 값으로 갖는다. 이 저장소의 다른 엔티티와 같은 방식이며, 세트가 필요한 곳은
 * "이 세션의 세트 목록"뿐이라 리포지토리에서 명시적으로 읽는 편이 쿼리가 드러난다.
 *
 * <p>하루에 여러 세션을 허용한다(명세 6.1). 오전·오후로 나눠 운동하는 경우
 * 한 세션에 몰아넣으면 중간 공백이 15분 캡으로 잡혀 운동 시간이 왜곡된다.
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

    /** 추천 루틴에서 진입한 경우의 연결. 10월 {@code routines} 전까지는 항상 null (명세 6.2) */
    @Column(name = "routine_id")
    private Long routineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionSource source;

    /** 첫 세트가 저장될 때 채워진다. 그 전에는 null이라 화면이 경과 시간을 띄우지 않는다 (명세 6.3) */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    /** 세트 저장 시각으로 산출한 값. {@code BACKFILL}은 산출하지 않아 null로 남는다 */
    @Column(name = "duration_sec")
    private Integer durationSec;

    /** 사용자가 직접 넣은 보정값. 있으면 이쪽이 우선한다 (명세 6.9) */
    @Column(name = "duration_override_sec")
    private Integer durationOverrideSec;

    @Column(columnDefinition = "TEXT")
    private String memo;

    private WorkoutSession(Long userId, LocalDate performedOn, SessionSource source, Long routineId) {
        this.userId = userId;
        this.performedOn = performedOn;
        this.source = source;
        this.routineId = routineId;
        this.status = SessionStatus.DRAFT;
    }

    public static WorkoutSession start(Long userId, LocalDate performedOn, SessionSource source, Long routineId) {
        return new WorkoutSession(userId, performedOn, source, routineId);
    }

    public boolean isDone() {
        return status == SessionStatus.DONE;
    }

    public boolean isLive() {
        return source == SessionSource.LIVE;
    }

    /**
     * 첫 세트가 저장된 시각을 기록한다. 이미 있으면 그대로 둔다 — 세트를 지웠다
     * 다시 넣어도 세션이 시작된 시각은 바뀌지 않는다.
     */
    public void markStartedAt(OffsetDateTime recordedAt) {
        if (startedAt == null) {
            startedAt = recordedAt;
        }
    }

    /**
     * {@code DRAFT} → {@code DONE}. 종료 시각은 마지막 세트의 저장 시각이다.
     *
     * <p>벽시계 {@code now()}가 아닌 것은, 운동을 끝내고 한참 뒤에 종료를 누르는
     * 경우 그 공백까지 운동 시간에 들어가기 때문이다.
     */
    public void complete(OffsetDateTime lastRecordedAt, Duration duration) {
        this.status = SessionStatus.DONE;
        this.endedAt = lastRecordedAt;
        this.durationSec = duration == null ? null : (int) duration.toSeconds();
    }

    /** 세트가 바뀌어 운동 시간을 다시 계산했을 때. {@code LIVE} 세션에만 쓴다 */
    public void recalculateDuration(Duration duration) {
        this.durationSec = duration == null ? null : (int) duration.toSeconds();
    }

    /**
     * 화면과 그룹 집계가 쓰는 값. 보정값이 있으면 그것을, 없으면 산출값을 쓴다.
     * {@code BACKFILL}에 보정값도 없으면 null이고, 그 세션은 시간 합계에서 빠진다.
     */
    public Integer effectiveDurationSec() {
        return durationOverrideSec != null ? durationOverrideSec : durationSec;
    }
}
