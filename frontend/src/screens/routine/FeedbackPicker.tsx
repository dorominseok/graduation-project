import { useState } from 'react'
import type { FeedbackRating, FeedbackReason } from '../../api'
import { RATING_LABEL, REASON_LABEL } from './format'
import styles from './routine.module.css'

const RATINGS: FeedbackRating[] = ['GOOD', 'OKAY', 'BAD']
const REASONS: FeedbackReason[] = ['EXERCISE', 'TOO_MUCH', 'TOO_LITTLE', 'OTHER']

interface FeedbackPickerProps {
  disabled?: boolean
  onSubmit: (rating: FeedbackRating, reason?: FeedbackReason) => void
}

/**
 * 만족도. 좋았어요는 한 번 누르면 끝이다. 보통·별로면 이유를 하나 고를 수 있고,
 * 고르지 않고 보내도 된다 — 묻는 쪽이 길어지면 답을 안 한다.
 *
 * <p>홈 카드 아래와 다음 방문 시트가 같은 것을 쓴다.
 */
export function FeedbackPicker({ disabled = false, onSubmit }: FeedbackPickerProps) {
  const [pending, setPending] = useState<FeedbackRating | null>(null)

  if (pending) {
    return (
      <div className={styles.feedback}>
        <div className={styles.feedbackAsk}>어떤 점이 아쉬웠나요?</div>
        <div className={styles.reasonList}>
          {REASONS.map((reason) => (
            <button
              key={reason}
              type="button"
              className={styles.reasonChip}
              disabled={disabled}
              onClick={() => onSubmit(pending, reason)}
            >
              {REASON_LABEL[reason]}
            </button>
          ))}
        </div>
        <button
          type="button"
          className={styles.feedbackPlain}
          disabled={disabled}
          onClick={() => onSubmit(pending)}
        >
          이유 없이 보내기
        </button>
      </div>
    )
  }

  return (
    <div className={styles.ratingRow}>
      {RATINGS.map((rating) => (
        <button
          key={rating}
          type="button"
          className={styles.ratingButton}
          disabled={disabled}
          onClick={() => (rating === 'GOOD' ? onSubmit(rating) : setPending(rating))}
        >
          {RATING_LABEL[rating]}
        </button>
      ))}
    </div>
  )
}
