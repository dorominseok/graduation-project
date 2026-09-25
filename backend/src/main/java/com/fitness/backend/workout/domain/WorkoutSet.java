package com.fitness.backend.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 완료 체크한 세트 하나. 부록 A {@code workout_sets} (V1 + V4).
 *
 * <p>계획 행은 여기 오지 않는다 — 루틴에 미리 표시된 행은 계획일 뿐이고,
 * 사용자가 완료 체크를 한 세트만 저장한다(LOG-05).
 *
 * <p>{@code recordedAt}은 {@code BaseCreatedEntity}를 상속하지 않고 직접 갖는다.
 * 이름은 감사 값처럼 보이지만 운동 시간 산출에 쓰이는 <b>도메인 값</b>이라
 * 세트를 수정해도 갱신되면 안 된다(「운동기록_방식_설계서」 4.2).
 */
@Entity
@Getter
@Table(name = "workout_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    /**
     * 저장 시점에 부여하고 이후 재부여하지 않는다. 세트를 지우면 구멍이 생길 수
     * 있으나(1, 2, 4) 화면은 배열 순번으로 그리므로 사용자에게는 보이지 않는다(명세 6.12).
     */
    @Column(name = "set_no", nullable = false)
    private Short setNo;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    private Short reps;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "is_warmup", nullable = false)
    private boolean isWarmup;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    /**
     * 멱등 키. 클라이언트가 완료 체크 시점에 만든 UUID이고
     * {@code (session_id, client_set_id)}에 UNIQUE가 걸려 있다(V4).
     *
     * <p>요청이 저장까지 끝난 뒤 응답만 유실되면 클라이언트는 실패로 보고 재전송하는데,
     * 이 키가 없으면 같은 세트가 2행이 된다. 중복 세트는 볼륨 과대 집계이고
     * 부족한 부위를 충분한 것으로 올려 <b>약점을 놓치는 방향</b>으로 판정을 틀리게 한다.
     */
    @Column(name = "client_set_id", nullable = false)
    private UUID clientSetId;

    private WorkoutSet(Long sessionId, Long exerciseId, Short setNo, BigDecimal weightKg,
                       Short reps, Integer durationSec, boolean isWarmup,
                       OffsetDateTime recordedAt, UUID clientSetId) {
        this.sessionId = sessionId;
        this.exerciseId = exerciseId;
        this.setNo = setNo;
        this.weightKg = weightKg;
        this.reps = reps;
        this.durationSec = durationSec;
        this.isWarmup = isWarmup;
        this.recordedAt = recordedAt;
        this.clientSetId = clientSetId;
    }

    public static WorkoutSet record(Long sessionId, Long exerciseId, Short setNo, BigDecimal weightKg,
                                    Short reps, Integer durationSec, boolean isWarmup,
                                    OffsetDateTime recordedAt, UUID clientSetId) {
        return new WorkoutSet(sessionId, exerciseId, setNo, weightKg, reps,
                durationSec, isWarmup, recordedAt, clientSetId);
    }

    /**
     * 사후 수정(명세 6.11). {@code null}로 온 항목은 건드리지 않는다 — 부분 수정이라
     * 보내지 않은 필드는 그대로 두어야 한다.
     */
    public void modify(BigDecimal weightKg, Short reps, Integer durationSec, Boolean isWarmup) {
        if (weightKg != null) {
            this.weightKg = weightKg;
        }
        if (reps != null) {
            this.reps = reps;
        }
        if (durationSec != null) {
            this.durationSec = durationSec;
        }
        if (isWarmup != null) {
            this.isWarmup = isWarmup;
        }
    }
}
