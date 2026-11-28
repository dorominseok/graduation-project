import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { BottomSheet, ScreenHeader, useToast } from '../../components'
import { isApiError, workoutApi } from '../../api'
import type { WorkoutSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import { formatDayLabel } from '../../app/dates'
import { DaySessionView } from '../group/DaySessionView'
import type { DayView } from '../group/DaySessionView'
import { renderJournalImage, saveImage } from './journalImage'
import { roundLabel } from './sessionPlan'
import styles from '../group/group.module.css'
import full from './fullScreen.module.css'

/**
 * 그날 운동 일지 보기 — 그룹 멤버의 하루 기록과 같은 모양(종목 그림 + 세트 칸)으로 내 기록을 본다.
 *
 * <p>그룹이 없어도, 그룹 › 내 달력을 거치지 않아도 기록 화면 ⋮ 메뉴에서 바로 온다. 읽기 전용이고 체크한 세트만
 * 보인다(계획 줄은 없다). 뒤로 가면 그날 기록 화면이다.
 *
 * <p>회차가 나뉜 날은 탭으로 회차 하나만 볼 수 있고, 기록 화면에서 보던 회차로 열린다({@code ?round=2}). 머리줄 오른쪽 버튼으로
 * 보고 있는 그대로를 이미지로 저장한다(결정 기록 D-24 — 바디캘린더처럼, 캡처해 자르지 않게).
 */
export function DayOverviewScreen() {
  const sessionId = Number(useParams().sessionId)
  if (!Number.isInteger(sessionId) || sessionId <= 0) {
    return <Navigate to={paths.session} replace />
  }
  return <DayOverview key={sessionId} sessionId={sessionId} />
}

/** 주소의 회차. 없거나 이상하면 전체 */
function viewOf(param: string | null): DayView {
  if (param === null) return 'all'
  const round = Number(param)
  return Number.isInteger(round) && round >= 0 ? round : 'all'
}

/** 저장할 이미지 — 만든 그림과 파일 이름 */
interface Picture {
  blob: Blob
  url: string
  fileName: string
}

function DayOverview({ sessionId }: { sessionId: number }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { user } = useAuth()
  const { showToast } = useToast()
  const [session, setSession] = useState<WorkoutSession | null>(null)
  const [view, setView] = useState<DayView>(() => viewOf(params.get('round')))

  // 이미지 만들기. 누르면 화면 밖에 저장용 일지를 그리고(폭은 지금 화면과 같게), 그려지면 굽는다
  const pageRef = useRef<HTMLDivElement>(null)
  const sheetRef = useRef<HTMLDivElement>(null)
  const [sheetWidth, setSheetWidth] = useState<number | null>(null)
  const [picture, setPicture] = useState<Picture | null>(null)

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

  // 고른 탭의 회차 이름 — 파일 이름에 붙인다. 회차가 하나뿐인 날은 없다
  const rounds = session ? [...new Set(session.exercises.map((exercise) => exercise.round))] : []
  const viewName = view !== 'all' && rounds.includes(view) ? roundLabel(view) : rounds.length > 1 ? '전체' : null

  useEffect(() => {
    const node = sheetRef.current
    if (sheetWidth === null || node === null || session === null) return
    let alive = true
    void (async () => {
      try {
        const blob = await renderJournalImage(node)
        if (!alive) return
        const fileName = `밸런스핏_${session.performedOn}${viewName ? `_${viewName}` : ''}.png`
        setPicture({ blob, url: URL.createObjectURL(blob), fileName })
      } catch (err) {
        console.error('일지 이미지 실패', err)
        if (alive) showToast({ message: '이미지를 만들지 못했어요', tone: 'danger' })
      } finally {
        if (alive) setSheetWidth(null)
      }
    })()
    return () => {
      alive = false
    }
  }, [sheetWidth, session, viewName, showToast])

  // 미리보기를 닫으면 그림을 놓아준다
  useEffect(() => {
    if (!picture) return
    return () => URL.revokeObjectURL(picture.url)
  }, [picture])

  // 기록 화면에서 왔으면 거기로 돌아간다. 주소로 바로 열었으면(돌아갈 곳이 없으면) 그날 기록 화면을 연다
  const back = () => {
    if (location.key !== 'default') navigate(-1)
    else navigate(paths.sessionDetail(sessionId), { replace: true })
  }

  const save = async () => {
    if (!picture) return
    try {
      if (await saveImage(picture.blob, picture.fileName)) showToast({ message: '이미지를 저장했어요' })
      setPicture(null)
    } catch (err) {
      console.error('일지 이미지 저장 실패', err)
      showToast({ message: '저장하지 못했어요', tone: 'danger' })
    }
  }

  const label = session ? formatDayLabel(session.performedOn) : ''
  const title = user && session ? `${user.nickname} · ${label}` : label
  const hasSets = session !== null && session.exercises.length > 0
  const making = sheetWidth !== null

  return (
    <>
      <ScreenHeader
        title={title}
        onBack={back}
        trailing={
          <button
            type="button"
            className={styles.iconButton}
            disabled={!hasSets || making}
            onClick={() => pageRef.current && setSheetWidth(pageRef.current.clientWidth)}
            aria-label="이미지로 저장"
          >
            {/* 아래로 내려받기 */}
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 19h14"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        }
      />

      {/* 그룹 하루 기록은 탭 화면이라 AppShell이 스크롤해 주지만, 여기는 전체 화면이라 스스로 스크롤한다 */}
      <div className={full.scroll}>
        <div ref={pageRef} className={styles.page}>
          {session && session.exercises.length === 0 && (
            <div className={styles.empty}>
              <div className={styles.emptyText}>이날은 기록이 없어요.</div>
            </div>
          )}

          {session && hasSets && (
            <DaySessionView
              startedAt={session.startedAt}
              durationSec={session.effectiveDurationSec}
              exercises={session.exercises}
              rounds={session.rounds}
              view={view}
              onViewChange={setView}
            />
          )}
        </div>
      </div>

      {/* 저장용 일지 — 화면 밖에 그린다. 탭 대신 위에 이름·날짜, 아래에 앱 이름 */}
      {session && making && (
        <div ref={sheetRef} className={`${styles.page} ${full.captureSheet}`} style={{ width: sheetWidth }} aria-hidden="true">
          <div className={full.captureTitle}>{title}</div>
          <DaySessionView
            startedAt={session.startedAt}
            durationSec={session.effectiveDurationSec}
            exercises={session.exercises}
            rounds={session.rounds}
            view={view}
            hideTabs
          />
          <div className={full.captureFooter}>밸런스핏</div>
        </div>
      )}

      <BottomSheet
        open={picture !== null}
        onClose={() => setPicture(null)}
        title={<span className={full.previewTitle}>이미지로 저장</span>}
      >
        {picture && (
          <div className={full.preview}>
            <img src={picture.url} alt="저장할 운동 일지" className={full.previewImage} />
            <button type="button" className={styles.primary} onClick={() => void save()}>
              저장
            </button>
          </div>
        )}
      </BottomSheet>
    </>
  )
}
