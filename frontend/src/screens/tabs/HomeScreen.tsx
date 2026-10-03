import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, Chevron, useToast } from '../../components'
import { analysisApi, isApiError, workoutApi } from '../../api'
import type { Balance, MuscleVolume, SessionSummary, WorkoutSession } from '../../api'
import { useAuth } from '../../auth'
import { paths } from '../../app/paths'
import styles from './home.module.css'

const WEEKDAYS_MON = ['월', '화', '수', '목', '금', '토', '일']

/** 로컬 날짜를 YYYY-MM-DD로. toISOString은 UTC로 바꿔 하루가 밀린다 */
function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

/** 이번 주 월요일. 주는 월요일에 시작한다(명세 1.2) */
function mondayOf(date: Date): Date {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
  return monday
}

function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}월 ${day}일`
}

/**
 * 인사말 아래 큰 줄. 0을 제목 자리에 크게 띄우지 않는다 — 월초마다 "10월 0회"가
 * 뜨고, 막 가입한 사람에게는 "누적 0회"가 첫 화면이 된다.
 */
function greetingStats(today: Date, counts: Counts): string {
  if (counts.total === 0) return '첫 운동을 기록해볼까요'
  if (counts.month === 0) return `누적 ${counts.total}회`
  return `${today.getMonth() + 1}월 ${counts.month}회 · 누적 ${counts.total}회`
}

interface Counts {
  month: number
  total: number
  week: SessionSummary[]
}

/**
 * 홈 (목업 홈 탭).
 *
 * <p>목업의 카드 넷 중 "오늘의 루틴 추천"은 뺐다. 루틴은 10월5주에 들어오는데,
 * 그전에 "준비 중" 카드를 두면 검증 참여자에게 메인 기능이 고장 난 앱으로 보인다.
 * 그동안은 "운동 시작"이 주 버튼이다.
 *
 * <p>횟수는 전부 <b>종료한</b> 운동만 센다. 캘린더 응답은 진행 중 세션까지 세서
 * 쓰지 않고 히스토리를 `DONE`으로 거른다.
 */
export function HomeScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { showToast } = useToast()

  const [current, setCurrent] = useState<WorkoutSession | null>(null)
  const [counts, setCounts] = useState<Counts | null>(null)
  const [analysis, setAnalysis] = useState<{ volume: MuscleVolume; balance: Balance } | null>(null)
  const [loading, setLoading] = useState(true)
  const [ending, setEnding] = useState(false)
  const [busy, setBusy] = useState(false)
  // 진행 중 세션을 끝내면 횟수·약점이 바뀌므로 다시 읽는다
  const [version, setVersion] = useState(0)

  const today = new Date()
  const todayKey = toDateString(today)
  const monday = mondayOf(today)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  useEffect(() => {
    let alive = true
    const now = new Date()
    const load = async () => {
      try {
        const monthStart = toDateString(new Date(now.getFullYear(), now.getMonth(), 1))
        const [session, month, total, week, volume, balance] = await Promise.all([
          workoutApi.getCurrent(),
          workoutApi.getHistory({ from: monthStart, to: toDateString(now), status: 'DONE', size: 1 }),
          // 날짜를 생략하면 서버가 최근 30일만 보므로 시작일을 아주 앞으로 둔다
          workoutApi.getHistory({ from: '2000-01-01', to: toDateString(now), status: 'DONE', size: 1 }),
          workoutApi.getHistory({
            from: toDateString(mondayOf(now)),
            to: toDateString(now),
            status: 'DONE',
            size: 50,
          }),
          analysisApi.getMuscleVolume(),
          analysisApi.getBalance(),
        ])
        if (!alive) return
        setCurrent(session)
        setCounts({
          month: month.page.totalElements,
          total: total.page.totalElements,
          week: week.content,
        })
        setAnalysis({ volume, balance })
      } catch (err) {
        fail(err)
      } finally {
        if (alive) setLoading(false)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [fail, version])

  const currentSets = current?.exercises.reduce((sum, group) => sum + group.sets.length, 0) ?? 0

  /**
   * 진행 중 세션을 홈에서 끝낸다.
   *
   * <p>세트가 하나도 없으면 종료가 아니라 삭제다 — 서버가 빈 세션 종료를
   * 422로 막는다(명세 6.3). 지우지 않으면 그 세션이 남아 새 운동을 계속 막는다.
   */
  const endCurrent = async () => {
    if (!current || busy) return
    setBusy(true)
    try {
      if (currentSets === 0) await workoutApi.deleteSession(current.id)
      else await workoutApi.complete(current.id)
      setEnding(false)
      setVersion((v) => v + 1)
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const weekDays = new Set(counts?.week.map((s) => s.performedOn))
  const weekSets = counts?.week.reduce((sum, s) => sum + s.setCount, 0) ?? 0

  return (
    <div className={styles.page}>
      {/* 헤더는 두지 않는다. "홈"이라는 글자는 탭바가 이미 말하고 있다 */}
      <div className={styles.greeting}>
        <div className={styles.greetingName}>{user ? `${user.nickname}님` : '안녕하세요'}</div>
        <div className={styles.greetingStats}>{counts ? greetingStats(today, counts) : ' '}</div>
      </div>

      {!loading && current && (
        <div className={styles.resume}>
          <div className={styles.resumeText}>
            <span className={styles.resumeLabel}>
              {current.performedOn === todayKey
                ? '진행 중인 운동이 있어요'
                : `${formatMonthDay(current.performedOn)} 운동이 끝나지 않았어요`}
            </span>
            <span className={styles.resumeMeta}>
              {current.exercises.length}종목 · {currentSets}세트 기록됨
            </span>
          </div>
          <div className={styles.resumeActions}>
            <button type="button" className={styles.resumePrimary} onClick={() => navigate(paths.session)}>
              이어서 기록
            </button>
            <button type="button" className={styles.resumeSecondary} onClick={() => setEnding(true)}>
              여기서 종료
            </button>
          </div>
        </div>
      )}

      {!loading && !current && (
        <button type="button" className={styles.start} onClick={() => navigate(paths.session)}>
          운동 시작
        </button>
      )}

      {analysis && <WeaknessCard volume={analysis.volume} balance={analysis.balance} />}

      {counts && (
        <div className={styles.card}>
          <div className={styles.cardLabel}>이번 주 운동</div>
          <div className={styles.weekCount}>{counts.week.length}회</div>
          <div className={styles.weekStrip}>
            {WEEKDAYS_MON.map((label, i) => {
              const day = new Date(monday)
              day.setDate(monday.getDate() + i)
              const key = toDateString(day)
              return (
                <div key={label} className={styles.weekDay}>
                  <span className={styles.weekDayLabel}>{label}</span>
                  <span
                    className={[
                      styles.weekDot,
                      weekDays.has(key) ? styles.weekDotOn : '',
                      key === todayKey ? styles.weekDotToday : '',
                    ].join(' ')}
                    aria-label={`${label}요일 ${weekDays.has(key) ? '운동함' : '기록 없음'}`}
                  />
                </div>
              )
            })}
          </div>
          {/* 시간은 적지 않는다. 분석에 쓰지 않고, 사후 입력한 운동은 시간이 없어 합계가 틀려 보인다 */}
          <div className={styles.weekLine}>이번 주 총 {weekSets}세트</div>
        </div>
      )}

      <button type="button" className={styles.browse} onClick={() => navigate(paths.exerciseList)}>
        운동 종목 둘러보기 →
      </button>

      <BottomSheet
        open={ending}
        onClose={() => setEnding(false)}
        title={currentSets === 0 ? '이 운동을 지울까요?' : '여기서 종료할까요?'}
      >
        <div className={styles.sheetBody}>
          {currentSets === 0
            ? '기록한 세트가 없어 종료할 수 없어요. 지우면 새 운동을 시작할 수 있어요.'
            : `${current?.exercises.length ?? 0}종목 ${currentSets}세트를 기록하고 종료해요. 체크하지 않은 세트는 남지 않아요.`}
        </div>
        <div className={styles.sheetActions}>
          <button
            type="button"
            className={currentSets === 0 ? styles.sheetDanger : styles.sheetPrimary}
            disabled={busy}
            onClick={() => void endCurrent()}
          >
            {currentSets === 0 ? '지우기' : '종료'}
          </button>
          <button type="button" className={styles.sheetCancel} onClick={() => setEnding(false)}>
            취소
          </button>
        </div>
      </BottomSheet>
    </div>
  )
}

/**
 * 약점 요약.
 *
 * <p>목업은 여기에 상위 6종 막대를 판정색으로 칠했다. 그러면 분석 탭에서 막은
 * "팔 · 최적"(삼두 0세트를 가림)이 홈에서 다시 생긴다. 규칙대로 그리면 분석 탭을
 * 그대로 복사한 셈이 되어, 홈은 부족한 하위 부위 이름과 균형 경고만 적는다.
 *
 * <p>"이번 주" 토글도 뺐다. 7일로 판정하면 수요일에 열었을 때 전부 부족이 된다
 * (분석 설계서 2.1이 버린 방식).
 */
function WeaknessCard({ volume, balance }: { volume: MuscleVolume; balance: Balance }) {
  const navigate = useNavigate()
  const go = () => navigate(paths.analysis)

  if (volume.confidence.doneSessionCount === 0) {
    return (
      <button type="button" className={styles.card} onClick={go}>
        <div className={styles.cardHead}>
          <span className={styles.cardLabel}>약점</span>
          <Chevron />
        </div>
        <div className={styles.weakEmpty}>운동을 기록하고 종료하면 부족한 부위를 알려드려요</div>
      </button>
    )
  }

  const lacking = volume.tiers.flatMap((t) => t.children).filter((c) => c.verdict === 'INSUFFICIENT')
  const imbalanced = balance.pairs.filter((p) => p.verdict === 'IMBALANCED')

  return (
    <button type="button" className={styles.card} onClick={go}>
      <div className={styles.cardHead}>
        <span className={styles.cardLabel}>약점 · 최근 {volume.periodWeeks}주</span>
        <Chevron />
      </div>

      {lacking.length > 0 ? (
        <>
          <div className={styles.weakTitle}>부족한 곳 {lacking.length}</div>
          <div className={styles.weakChips}>
            {lacking.map((c) => (
              <span key={c.key} className={styles.weakChip}>
                {c.label}
              </span>
            ))}
          </div>
        </>
      ) : (
        <div className={styles.weakTitle}>부족한 부위가 없어요</div>
      )}

      {imbalanced.map((p) => (
        <div key={p.key} className={styles.weakBalance}>
          {p.label} 불균형
        </div>
      ))}

      {volume.confidence.level === 'LOW' && (
        <div className={styles.weakNote}>
          완료한 운동이 {volume.confidence.doneSessionCount}회라 참고만 해주세요
        </div>
      )}
    </button>
  )
}
