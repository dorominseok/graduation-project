import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { isApiError, workoutApi } from '../../api'
import type { WorkoutSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { formatDayLabel } from '../../app/dates'
import { DaySessionView } from '../group/DaySessionView'
import styles from '../group/group.module.css'

/**
 * 그날 기록 한눈에 보기 — 그룹 멤버의 하루 기록과 같은 모양(종목 그림 + 세트 칸)으로 내 기록을 본다.
 *
 * <p>그룹이 없어도, 그룹 › 내 달력을 거치지 않아도 기록 화면 ⋮ 메뉴에서 바로 온다. 읽기 전용이고 체크한 세트만
 * 보인다(계획 줄은 없다). 뒤로 가면 그날 기록 화면이다.
 */
export function DayOverviewScreen() {
  const sessionId = Number(useParams().sessionId)
  if (!Number.isInteger(sessionId) || sessionId <= 0) {
    return <Navigate to={paths.session} replace />
  }
  return <DayOverview key={sessionId} sessionId={sessionId} />
}

function DayOverview({ sessionId }: { sessionId: number }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const { showToast } = useToast()
  const [session, setSession] = useState<WorkoutSession | null>(null)

  useEffect(() => {
    let alive = true
    workoutApi
      .getSession(sessionId)
      .then((found) => {
        if (alive) setSession(found)
      })
      .catch((err: unknown) => {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        navigate(paths.session, { replace: true })
      })
    return () => {
      alive = false
    }
  }, [sessionId, navigate, showToast])

  // 기록 화면에서 왔으면 거기로 돌아간다. 주소로 바로 열었으면(돌아갈 곳이 없으면) 그날 기록 화면을 연다
  const back = () => {
    if (location.key !== 'default') navigate(-1)
    else navigate(paths.sessionDetail(sessionId), { replace: true })
  }

  const label = session ? formatDayLabel(session.performedOn) : ''

  return (
    <>
      <ScreenHeader title={user && session ? `${user.nickname} · ${label}` : label} onBack={back} />

      <div className={styles.page}>
        {session && session.exercises.length === 0 && (
          <div className={styles.empty}>
            <div className={styles.emptyText}>이날은 기록이 없어요.</div>
          </div>
        )}

        {session && session.exercises.length > 0 && (
          <DaySessionView
            startedAt={session.startedAt}
            durationSec={session.effectiveDurationSec}
            exercises={session.exercises}
          />
        )}
      </div>
    </>
  )
}
