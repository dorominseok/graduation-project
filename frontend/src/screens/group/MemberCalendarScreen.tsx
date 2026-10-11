import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { MonthCalendar, ScreenHeader } from '../../components'
import { groupApi } from '../../api'
import type { CalendarResponse, MemberActivity } from '../../api'
import { paths } from '../../app/paths'
import { formatDuration } from '../../app/dates'
import { isId, useGroupFail } from './useGroupFail'
import styles from './group.module.css'

/**
 * 그룹 멤버의 기록 — 월간 달력 (LOG-38). 내 기록 탭과 같은 달력을 읽기 전용으로 쓴다.
 *
 * <p>기록이 있는 날만 누를 수 있다. 내 기록 탭은 빈 날을 누르면 사후 입력을 시작하지만,
 * 남의 기록에는 채울 것이 없다.
 */
export function MemberCalendarScreen() {
  const params = useParams()
  const groupId = Number(params.groupId)
  const memberId = Number(params.memberId)
  if (!isId(groupId) || !isId(memberId)) return <Navigate to={paths.group} replace />
  return <MemberCalendar key={`${groupId}-${memberId}`} groupId={groupId} memberId={memberId} />
}

function MemberCalendar({ groupId, memberId }: { groupId: number; memberId: number }) {
  const navigate = useNavigate()
  const location = useLocation()
  // 그룹에서 나갔거나 멤버가 아니면(404) 목록으로 간다
  const fail = useGroupFail()

  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  // 그룹 상세에서 누르고 오면 이름과 이번 주 요약을 함께 받는다
  const passed = (location.state as { member?: MemberActivity } | null)?.member
  const [member, setMember] = useState<MemberActivity | null>(passed?.userId === memberId ? passed : null)
  const [calendar, setCalendar] = useState<CalendarResponse | null>(null)

  // 주소로 바로 들어왔으면(새로고침·공유한 주소) 그룹 상세에서 이 멤버를 찾는다
  const needsMember = member == null
  useEffect(() => {
    if (!needsMember) return
    const run = async () => {
      try {
        const group = await groupApi.getGroup(groupId)
        setMember(group.members.find((m) => m.userId === memberId) ?? null)
      } catch (err) {
        fail(err)
      }
    }
    void run()
  }, [needsMember, groupId, memberId, fail])

  useEffect(() => {
    // 달을 빠르게 넘기면 응답이 순서대로 오지 않는다. 지나간 달의 응답은 버린다
    let stale = false
    const run = async () => {
      try {
        const next = await groupApi.getMemberCalendar(groupId, memberId, year, month)
        if (!stale) setCalendar(next)
      } catch (err) {
        if (!stale) fail(err)
      }
    }
    void run()
    return () => {
      stale = true
    }
  }, [groupId, memberId, year, month, fail])

  const moveMonth = (delta: number) => {
    const next = new Date(year, month - 1 + delta, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
  }

  return (
    <>
      <ScreenHeader
        title={member ? `${member.nickname}님의 기록` : '기록'}
        onBack={() => navigate(paths.groupDetail(groupId))}
      />

      <div className={styles.calendarPage}>
        {member && (
          <div className={styles.calendarSummary}>
            이번 주 {member.week.dayCount}회
            {member.week.totalDurationSec > 0 && ` · ${formatDuration(member.week.totalDurationSec)}`}
          </div>
        )}
        <MonthCalendar
          year={year}
          month={month}
          days={calendar?.days}
          onMoveMonth={moveMonth}
          onSelectDay={(key) => navigate(paths.groupMemberDay(groupId, memberId, key))}
          emptyDaysSelectable={false}
        />
      </div>
    </>
  )
}
