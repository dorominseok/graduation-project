package com.fitness.backend.workout.web;

import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.workout.domain.WorkoutSession;
import com.fitness.backend.workout.domain.WorkoutSet;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 운동 기록 API의 요청·응답. */
public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    /**
     * 하루 기록 열기.
     *
     * <p>{@code performedOn}을 생략하면 오늘이다. 단 어젯밤 운동이 자정을 넘겨 이어지는 중이면
     * 어제 기록을 연다 — 서버가 정한다.
     */
    public record OpenDayRequest(LocalDate performedOn) {
    }

    /** 하루 기록 열기 결과. {@code created}가 {@code 201}과 {@code 200}을 가른다 */
    public record OpenDayResult(SessionResponse session, boolean created) {
    }

    /**
     * 세트 저장 시각으로 계산한 하루의 시각과 시간. 저장하지 않고 읽을 때 계산한다.
     *
     * @param startedAt 첫 세트 저장 시각. 세트가 없으면 null
     * @param endedAt   마지막 세트 저장 시각. 세트가 없으면 null
     * @param duration  산출한 운동 시간. 세트가 없거나 사후 입력만 있으면 null
     */
    public record DayTimes(OffsetDateTime startedAt, OffsetDateTime endedAt, Duration duration) {

        public Integer durationSec() {
            return duration == null ? null : (int) duration.toSeconds();
        }

        /** 화면과 그룹 집계가 쓰는 값. 사용자가 고친 값이 있으면 그것을, 없으면 산출값을 쓴다 */
        public Integer effectiveSec(WorkoutSession session) {
            return session.getDurationOverrideSec() != null ? session.getDurationOverrideSec() : durationSec();
        }
    }

    /**
     * 세트 저장.
     *
     * <p>{@code weightKg}·{@code reps}·{@code durationSec} 중 무엇이 필수인지는
     * 종목의 {@code measureType}에 달려 있어 애노테이션으로 못 박지 않는다.
     * 여기서는 형식만 보고, 조합 검증은 서비스가 한다({@code INVALID_MEASURE_INPUT}).
     */
    public record CreateSetRequest(
            @NotNull(message = "필수 항목입니다.") UUID clientSetId,
            @NotNull(message = "필수 항목입니다.") Long exerciseId,
            @DecimalMin(value = "0.0", message = "0 이상이어야 합니다.")
            @Digits(integer = 4, fraction = 2, message = "소수점 두 자리까지 입력할 수 있습니다.")
            BigDecimal weightKg,
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer reps,
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer durationSec,
            Boolean isWarmup) {

        public boolean warmupOrDefault() {
            return Boolean.TRUE.equals(isWarmup);
        }
    }

    /** 세트 한 건. 세트 저장의 응답 */
    public record SetResponse(Long id,
                              Long sessionId,
                              UUID clientSetId,
                              Long exerciseId,
                              String exerciseName,
                              Short setNo,
                              BigDecimal weightKg,
                              Short reps,
                              Integer durationSec,
                              boolean isWarmup,
                              OffsetDateTime recordedAt) {

        public static SetResponse of(WorkoutSet s, String exerciseName) {
            return new SetResponse(s.getId(), s.getSessionId(), s.getClientSetId(),
                    s.getExerciseId(), exerciseName, s.getSetNo(), s.getWeightKg(),
                    s.getReps(), s.getDurationSec(), s.isWarmup(), s.getRecordedAt());
        }
    }

    /** 하루 기록 상세 안에서 종목별로 묶은 세트 */
    public record ExerciseGroup(Long exerciseId,
                                String exerciseName,
                                MeasureType measureType,
                                List<SetInGroup> sets) {
    }

    /** 종목 묶음 안의 세트. 종목 정보는 묶음이 이미 갖고 있어 빼고 담는다 */
    public record SetInGroup(Long id,
                             UUID clientSetId,
                             Short setNo,
                             BigDecimal weightKg,
                             Short reps,
                             Integer durationSec,
                             boolean isWarmup,
                             OffsetDateTime recordedAt) {

        public static SetInGroup of(WorkoutSet s) {
            return new SetInGroup(s.getId(), s.getClientSetId(), s.getSetNo(), s.getWeightKg(),
                    s.getReps(), s.getDurationSec(), s.isWarmup(), s.getRecordedAt());
        }
    }

    /**
     * 하루 기록 상세.
     *
     * <p>{@code exercises}는 <b>수행 순서</b>로 정렬한다 — 각 종목의 가장 이른
     * 저장 시각 오름차순이다. 화면이 운동한 차례대로 그리기 때문이다.
     *
     * <p>{@code startedAt}·{@code endedAt}·{@code durationSec}은 세트 저장 시각으로 계산한 값이다.
     */
    public record SessionResponse(Long id,
                                  LocalDate performedOn,
                                  Long routineId,
                                  OffsetDateTime startedAt,
                                  OffsetDateTime endedAt,
                                  Integer durationSec,
                                  Integer durationOverrideSec,
                                  Integer effectiveDurationSec,
                                  String memo,
                                  OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt,
                                  List<ExerciseGroup> exercises) {

        public static SessionResponse of(WorkoutSession s, DayTimes times, List<ExerciseGroup> exercises) {
            return new SessionResponse(s.getId(), s.getPerformedOn(), s.getRoutineId(),
                    times.startedAt(), times.endedAt(), times.durationSec(),
                    s.getDurationOverrideSec(), times.effectiveSec(s), s.getMemo(),
                    s.getCreatedAt(), s.getUpdatedAt(), exercises);
        }
    }

    /**
     * 세트 저장 결과. {@code created}가 {@code 201}과 {@code 200}을 가른다.
     *
     * <p>같은 {@code clientSetId}로 이미 저장돼 있으면 {@code 200}이라, 클라이언트가
     * "새로 저장됨 / 이미 있던 것"을 구분할 수 있다.
     */
    public record SetSaveResult(SetResponse set, boolean created) {
    }

    /**
     * 세션 수정. 보내지 않은 항목은 건드리지 않는다.
     *
     * <p><b>값을 비우는 방법</b>: 메모는 빈 문자열, 시간 보정은 {@code 0}을 보낸다.
     * JSON에서 "필드가 없음"과 "null로 보냄"은 Jackson이 똑같이 {@code null}로 주기
     * 때문에, null을 "지우기"로 쓰면 날짜만 고치려는 요청에 메모가 날아간다.
     * 두 필드 모두 빈 문자열·0이 실제 값으로 쓰일 일이 없어 비움 신호로 삼았다.
     */
    public record UpdateSessionRequest(
            @Size(max = 2000, message = "2000자 이하여야 합니다.") String memo,
            LocalDate performedOn,
            @Min(value = 0, message = "0 이상이어야 합니다.") Integer durationOverrideSec) {
    }

    /**
     * 세트 수정.
     *
     * <p>여기는 {@link Optional}을 쓰지 않는다 — 중량·횟수·시간은 측정 유형이 요구하면
     * 반드시 있어야 하는 값이라 "지운다"는 선택지가 없다. {@code null}은 그대로
     * "건드리지 않음"이다.
     */
    public record UpdateSetRequest(
            @DecimalMin(value = "0.0", message = "0 이상이어야 합니다.")
            @Digits(integer = 4, fraction = 2, message = "소수점 두 자리까지 입력할 수 있습니다.")
            BigDecimal weightKg,
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer reps,
            @Min(value = 1, message = "1 이상이어야 합니다.") Integer durationSec,
            Boolean isWarmup) {
    }

    /** 히스토리 목록의 종목 참조. "최근 사용" 탭과 종목 상세 이동이 {@code id}를 쓴다 */
    public record ExerciseRef(Long id, String nameKo) {
    }

    /**
     * 히스토리 한 건. 세트는 담지 않는 요약이다.
     *
     * <p>{@code exercises}를 이름 배열이 아니라 {@code id}를 가진 객체로 내리는 것은,
     * 목록에서 종목 상세나 프리필로 이어가려면 ID가 있어야 하기 때문이다.
     * 이름으로 역조회하면 동명 종목을 구분하지 못한다.
     */
    public record SessionSummary(Long id,
                                 LocalDate performedOn,
                                 Integer effectiveDurationSec,
                                 int setCount,
                                 int exerciseCount,
                                 List<ExerciseRef> exercises) {
    }

    /** 캘린더의 하루. 운동한 날만 담는다. 하루에 기록이 하나라 날짜면 충분하다 */
    public record CalendarDay(LocalDate date) {
    }

    /**
     * 직전 수행 기록. 종목을 고르는 순간 세트 행을 채우는 데 쓴다.
     *
     * <p>세트에 {@code id}를 담지 않는다 — 새 기록에 채워 넣을 참조값이지 편집 대상
     * 행이 아니다. 프리필된 행은 완료 체크 시점에 새로 저장된다.
     */
    public record LastPerformanceResponse(Long exerciseId,
                                          String exerciseName,
                                          MeasureType measureType,
                                          Long sessionId,
                                          LocalDate performedOn,
                                          List<LastPerformanceSet> sets) {
    }

    /** 직전 수행 기록의 세트. 워밍업도 담고 {@code isWarmup}으로 구분한다 */
    public record LastPerformanceSet(Short setNo,
                                     BigDecimal weightKg,
                                     Short reps,
                                     Integer durationSec,
                                     boolean isWarmup) {

        public static LastPerformanceSet of(WorkoutSet s) {
            return new LastPerformanceSet(s.getSetNo(), s.getWeightKg(), s.getReps(),
                    s.getDurationSec(), s.isWarmup());
        }
    }

    /** 종목 기록의 하루 — 그날 그 종목의 세트. 워밍업도 담고 {@code isWarmup}으로 구분한다 */
    public record ExerciseHistoryDay(Long sessionId, LocalDate performedOn, List<LastPerformanceSet> sets) {
    }

    /** 캘린더 월별 요약 */
    public record CalendarResponse(int year, int month, List<CalendarDay> days) {
    }
}
