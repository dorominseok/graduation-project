import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader } from '../../components'
import { groupApi } from '../../api'
import type { MemberDay, MemberSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { formatDayLabel, formatDuration } from '../../app/dates'
import { exerciseImage, hideOnError } from '../full/exerciseImage'
import { SetGrid } from '../full/SetGrid'
import { CheerButton } from './CheerButton'
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

/** "2026-10-11T19:12:03+09:00" → "오후 7:12" */
function formatClock(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

/**
 * 운동 머리 한 줄 — "오후 7:12 시작 · 1시간 10분". 하루에 두 번 운동했으면 시간은 둘을 더한 것이다.
 *
 * <p>나중에 입력한 기록인지는 표시하지 않는다 — 기록을 의심하는 표시처럼 읽힌다. 시작 시각은
 * 운동 시간이 있을 때만 적는다. 없으면 나중에 입력한 기록이라 저장 시각이 운동한 시각이 아니다.
 */
function metaOf(session: MemberSession): string[] {
  const parts: string[] = []
  if (session.durationSec != null && session.startedAt) parts.push(`${formatClock(session.startedAt)} 시작`)
  if (session.durationSec != null) parts.push(formatDuration(session.durationSec))
  return parts
}

type MemberExercise = MemberSession['exercises'][number]

/**
 * 그날 기록 — 머리 줄(시각·시간·세트 수·따봉) 아래에 종목마다 카드 하나.
 * 세트를 칸으로 늘어놓아, 줄글보다 한눈에 들어오게 한다.
 */
function SessionBlock({ groupId, session, mine }: { groupId: number; session: MemberSession; mine: boolean }) {
  const setCount = session.exercises.reduce((sum, e) => sum + e.sets.filter((s) => !s.isWarmup).length, 0)

  return (
    <section className={styles.session}>
      <div className={styles.sessionHead}>
        <span className={styles.sessionMeta}>{metaOf(session).join(' · ')}</span>
        <span className={styles.sessionRight}>
          <span className={styles.sessionSets}>{setCount}세트</span>
          <CheerButton
            groupId={groupId}
            sessionId={session.id}
            initial={{ cheerCount: session.cheerCount, cheeredByMe: session.cheeredByMe }}
            mine={mine}
          />
        </span>
      </div>

      {session.exercises.map((exercise, index) => (
        <ExerciseCard key={`${exercise.exerciseId}-${index}`} exercise={exercise} />
      ))}
    </section>
  )
}

function ExerciseCard({ exercise }: { exercise: MemberExercise }) {
  const shots = ([1, 2] as const)
    .map((shot) => exerciseImage(exercise.exerciseNameEn, shot))
    .filter((src): src is string => src != null)

  return (
    <div className={`${styles.card} ${styles.exerciseCard}`}>
      <div className={styles.exerciseHead}>
        <span className={styles.exerciseName}>{exercise.exerciseName}</span>
        {shots.length > 0 && (
          <span className={styles.exerciseShots} aria-hidden="true">
            {shots.map((src) => (
              <img key={src} src={src} alt="" className={styles.exerciseShot} onError={hideOnError} loading="lazy" />
            ))}
          </span>
        )}
      </div>
      <SetGrid measureType={exercise.measureType} sets={exercise.sets} />
    </div>
  )
}
