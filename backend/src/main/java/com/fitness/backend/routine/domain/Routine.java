package com.fitness.backend.routine.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import com.fitness.backend.routine.domain.RoutineEnums.Author;
import com.fitness.backend.routine.domain.RoutineEnums.FallbackReason;
import com.fitness.backend.routine.domain.RoutineEnums.FeedbackRating;
import com.fitness.backend.routine.domain.RoutineEnums.FeedbackReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 루틴({@code routines}).
 *
 * <p>하루에 하나다. 다시 만들기는 없고 항목을 후보정한다. 행은 생성 쿼리가 넣고
 * ({@link com.fitness.backend.routine.repository.RoutineRepository#insertIfAbsent}),
 * 엔티티는 읽기와 만족도 기록에 쓴다.
 */
@Entity
@Getter
@Table(name = "routines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "routine_date", nullable = false)
    private LocalDate routineDate;

    /** 분석 기준일 — 어제 */
    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 10)
    private Author generatedBy;

    @Column(name = "llm_attempts", nullable = false)
    private short llmAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_reason", length = 30)
    private FallbackReason fallbackReason;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_by", nullable = false, length = 10)
    private Author commentBy;

    @Column(name = "final_check_passed", nullable = false)
    private boolean finalCheckPassed;

    /** 실패한 규칙 ID, 쉼표로 이음 (예: {@code R3}) */
    @Column(name = "final_check_failures", length = 100)
    private String finalCheckFailures;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_rating", length = 10)
    private FeedbackRating feedbackRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_reason", length = 20)
    private FeedbackReason feedbackReason;

    @Column(name = "feedback_skipped", nullable = false)
    private boolean feedbackSkipped;

    @Column(name = "feedback_at")
    private OffsetDateTime feedbackAt;

    /** 만족도를 받았거나 건너뛰었다 — 다시 묻지 않는다 */
    public boolean feedbackClosed() {
        return feedbackRating != null || feedbackSkipped;
    }

    /** 보통·별로일 때만 이유를 남긴다. 좋음에 붙은 이유는 버린다 */
    public void recordFeedback(FeedbackRating rating, FeedbackReason reason, OffsetDateTime at) {
        this.feedbackRating = rating;
        this.feedbackReason = rating == FeedbackRating.GOOD ? null : reason;
        this.feedbackAt = at;
    }

    public void skipFeedback(OffsetDateTime at) {
        this.feedbackSkipped = true;
        this.feedbackAt = at;
    }
}
