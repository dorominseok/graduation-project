import { Link } from 'react-router-dom'
import { Chevron } from '../../components'
import type { GroupSummary } from '../../api'
import { paths } from '../../app/paths'
import styles from './group.module.css'

/** 카드에 얼굴을 몇 명까지 그릴지. 넘으면 "+N"으로 줄인다 */
const MAX_FACES = 6


/**
 * 그룹 목록의 카드 한 장. 그룹은 보통 한두 개라, 들어가기 전에도 누가 오늘 운동했는지
 * 보이게 멤버 얼굴과 오늘 상태를 그린다. 그룹 전체 합계·비율 막대·"누가 운동 중" 문장은
 * 두지 않는다 — 얼굴 줄의 표시와 같은 말을 한 번 더 할 뿐이다.
 */
export function GroupCard({ group }: { group: GroupSummary }) {
  const faces = group.members.slice(0, MAX_FACES)
  const hidden = group.members.length - faces.length
  const alone = group.memberCount === 1

  return (
    <Link to={paths.groupDetail(group.id)} className={`${styles.card} ${styles.groupCard}`}>
      <div className={styles.groupRow}>
        <div className={styles.groupMain}>
          <div className={styles.nameRow}>
            <span className={styles.groupName}>{group.name}</span>
            {group.isOwner && <span className={styles.tag}>방장</span>}
          </div>
          <div className={styles.subLine}>
            멤버 {group.memberCount}명{!alone && ` · 오늘 ${group.todayWorkedOutCount}명 운동`}
          </div>
        </div>
        <Chevron />
      </div>

      <div className={styles.faces}>
        {faces.map((member) => (
          <span key={member.userId} className={styles.face}>
            <span
              className={`${styles.faceAvatar} ${member.isMe ? styles.avatarMe : ''}`}
              aria-label={member.todayStatus === 'DONE' ? `${member.nickname} 오늘 운동함` : member.nickname}
            >
              {member.nickname.slice(0, 1)}
              {member.todayStatus === 'DONE' && (
                <span className={`${styles.faceBadge} ${styles.faceBadgeDone}`} aria-hidden="true">
                  ✓
                </span>
              )}
            </span>
            <span className={styles.faceName}>{member.isMe ? '나' : member.nickname}</span>
          </span>
        ))}
        {hidden > 0 && (
          <span className={styles.face}>
            <span className={`${styles.faceAvatar} ${styles.faceMore}`}>+{hidden}</span>
          </span>
        )}
      </div>

      {alone && <div className={styles.subLine}>아직 혼자예요. 들어가서 친구를 초대해 보세요.</div>}
    </Link>
  )
}
