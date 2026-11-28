import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader } from '../../components'
import { groupApi } from '../../api'
import type { MemberDay, MemberSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { formatDayLabel } from '../../app/dates'
import { CheerButton } from './CheerButton'
import { DaySessionView } from './DaySessionView'
import { isId, useGroupFail } from './useGroupFail'
import styles from './group.module.css'

/**
 * 그룹 멤버의 하루 기록. 읽기 전용이다 — 고칠 수 없고, 메모는 서버가 보내지 않는다.
 *
 * <p>하루에 여러 번 운동했으면 시작한 순서로 쌓는다. 운동마다 응원을 보낼 수 있다.
 */
export function MemberDayScreen() {
  const params = useParams()
  const groupId = Number(params.groupId)
  const memberId = Number(params.memberId)
  const date = params.date ?? ''
  if (!isId(groupId) || !isId(memberId) || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return <Navigate to={paths.group} replace />
  }
  return <MemberDayView key={`${groupId}-${memberId}-${date}`} groupId={groupId} memberId={memberId} date={date} />
}

function MemberDayView({ groupId, memberId, date }: { groupId: number; memberId: number; date: string }) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [day, setDay] = useState<MemberDay | null>(null)
  // 그룹에서 나갔거나 멤버가 아니면(404) 목록으로 간다
  const fail = useGroupFail()

  useEffect(() => {
    const run = async () => {
      try {
        setDay(await groupApi.getMemberDay(groupId, memberId, date))
      } catch (err) {
        fail(err)
      }
    }
    void run()
  }, [groupId, memberId, date, fail])

  const mine = user?.userId === memberId

  return (
    <>
      <ScreenHeader
        title={day ? `${day.nickname} · ${formatDayLabel(date)}` : formatDayLabel(date)}
        onBack={() => navigate(paths.groupMember(groupId, memberId))}
      />

      <div className={styles.page}>
        {day && day.session === null && (
          <div className={styles.empty}>
            <div className={styles.emptyText}>이날은 기록이 없어요.</div>
          </div>
        )}

        {day?.session && <SessionBlock groupId={groupId} session={day.session} mine={mine} />}
      </div>
    </>
  )
}

/** 그날 기록에 따봉을 붙인다. 카드 모양은 내 "운동 일지 보기"와 같다(DaySessionView) */
function SessionBlock({ groupId, session, mine }: { groupId: number; session: MemberSession; mine: boolean }) {
  return (
    <DaySessionView
      startedAt={session.startedAt}
      durationSec={session.durationSec}
      exercises={session.exercises}
      rounds={session.rounds}
      trailing={
        <CheerButton
          groupId={groupId}
          sessionId={session.id}
          initial={{ cheerCount: session.cheerCount, cheeredByMe: session.cheeredByMe }}
          mine={mine}
        />
      }
    />
  )
}
