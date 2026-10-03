import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, Chevron, useToast } from '../../components'
import { analysisApi, exerciseApi, isApiError, statsApi, workoutApi } from '../../api'
import type { Balance, MuscleVolume, OneRmTrend, SessionSummary, WorkoutSession } from '../../api'
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

const WEEKDAYS_SUN = ['일', '월', '화', '수', '목', '금', '토']

function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${month}월 ${day}일`
}

/** "9월 29일 (화)" */
function formatDayWithWeekday(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${month}월 ${day}일 (${WEEKDAYS_SUN[new Date(year, month - 1, day).getDay()]})`
}

/** 오늘과 며칠 떨어졌는지. 시각이 아니라 날짜로 센다 */
function daysSince(isoDate: string, today: Date): number {
  const [year, month, day] = isoDate.split('-').map(Number)
  const then = Date.UTC(year, month - 1, day)
  const now = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.round((now - then) / 86_400_000)
}

/** "랫풀다운: 와이드 그립, 바벨 컬 외 1" — 카드 한 줄에 들어가게 둘까지만 */
function exerciseLine(session: SessionSummary): string {
  const names = session.exercises.map((e) => e.nameKo)
  return names.length > 2 ? `${names.slice(0, 2).join(', ')} 외 ${names.length - 2}` : names.join(', ')
}

/** 성장 카드 후보를 고를 기간. 1RM 추이의 기본 구간과 같다 */
const GROWTH_LOOKBACK_DAYS = 12 * 7 - 1
/** 성장 카드 후보 수. 많이 볼수록 요청이 는다 */
const GROWTH_CANDIDATES = 3

/** 첫 점 대비 마지막 점. 점이 하나면 비교할 것이 없어 0 */
function gainOf(trend: OneRmTrend): number {
  const points = trend.points
  return points[points.length - 1].estimatedOneRm - points[0].estimatedOneRm
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
  /** 가장 최근에 종료한 운동 둘. 누적을 세는 요청에서 같이 받는다 */
  recent: SessionSummary[]
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
          workoutApi.getHistory({ from: '2000-01-01', to: toDateString(now), status: 'DONE', size: 2 }),
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
          recent: total.content,
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

      {/*
        목업의 루틴 추천 카드 자리. 루틴이 들어오는 10월5주에는 이 카드가 루틴 카드가 된다 —
        그전까지는 얼마나 쉬었는지와 시작 버튼을 둔다.
      */}
      {!loading && !current && counts && (
        <div className={styles.hero}>
          <div className={styles.heroTitle}>오늘 운동</div>
          <div className={styles.heroSub}>{restLine(counts.recent[0], today)}</div>
          <button type="button" className={styles.start} onClick={() => navigate(paths.session)}>
            운동 시작
          </button>
        </div>
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

      <GrowthCard version={version} />

      {counts && (
        <div className={styles.card}>
          <div className={styles.cardLabel}>최근 기록</div>
          {counts.recent.length === 0 ? (
            <div className={styles.weakEmpty}>종료한 운동이 여기 쌓여요</div>
          ) : (
            <ul className={styles.recentList}>
              {counts.recent.map((session) => (
                <li key={session.id}>
                  <button
                    type="button"
                    className={styles.recentItem}
                    onClick={() => navigate(paths.sessionDetail(session.id))}
                  >
                    <span className={styles.recentText}>
                      <span className={styles.recentDate}>{formatDayWithWeekday(session.performedOn)}</span>
                      <span className={styles.recentMeta}>
                        {exerciseLine(session)} · {session.setCount}세트
                      </span>
                    </span>
                    <Chevron />
                  </button>
                </li>
              ))}
            </ul>
          )}
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

/**
 * 오늘 운동 카드의 한 줄. 무엇을 했는지는 최근 기록 카드가 말하므로 여기서는
 * 얼마나 쉬었는지만 적는다.
 */
function restLine(last: SessionSummary | undefined, today: Date): string {
  if (!last) return '첫 운동을 시작해보세요'
  const days = daysSince(last.performedOn, today)
  if (days === 0) return '오늘 이미 한 번 운동했어요'
  if (days === 1) return '마지막 운동은 어제예요'
  return `마지막 운동 ${days}일 전 · ${formatMonthDay(last.performedOn)}`
}

/**
 * 성장 — 가장 자주 한 중량·횟수 종목의 추정 1RM 변화.
 *
 * <p>약점 카드는 부족한 것만 말한다. 늘고 있는 것을 하나 같이 보여줘야 홈이
 * 경고판이 되지 않는다. 최근 12주에 자주 한 중량·횟수 종목 셋 중 가장 많이 오른
 * 것을 고른다 — 자주 한 종목이어야 사용자가 신경 쓰는 종목이고, 그중에서 고르지
 * 않으면 횟수가 같을 때 변화 0kg인 종목이 뽑힌다(실제로 그랬다).
 *
 * <p>홈의 다른 카드와 따로 읽는다. 요청이 여러 번 이어져 늦게 오는데, 그동안
 * 나머지 홈이 기다릴 이유가 없다.
 */
function GrowthCard({ version }: { version: number }) {
  const navigate = useNavigate()
  const [trend, setTrend] = useState<OneRmTrend | 'none' | null>(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const from = new Date()
        from.setDate(from.getDate() - GROWTH_LOOKBACK_DAYS)
        const history = await workoutApi.getHistory({ from: toDateString(from), status: 'DONE', size: 50 })

        // 기록한 횟수(세션 수)로 줄 세운다
        const frequency = new Map<number, number>()
        history.content.forEach((session) =>
          session.exercises.forEach((e) => frequency.set(e.id, (frequency.get(e.id) ?? 0) + 1)),
        )
        const ranked = [...frequency.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => id)

        // 맨몸·시간 종목은 1RM이 없다. 위에서부터 중량·횟수 종목 셋을 모은다
        const candidates: OneRmTrend[] = []
        for (const id of ranked.slice(0, 8)) {
          if (candidates.length === GROWTH_CANDIDATES) break
          const exercise = await exerciseApi.get(id)
          if (exercise.measureType !== 'WEIGHT_REPS') continue
          const result = await statsApi.getOneRmTrend(id)
          if (result.points.length > 0) candidates.push(result)
        }
        if (!alive) return
        if (candidates.length === 0) {
          setTrend('none')
          return
        }
        setTrend(candidates.reduce((best, t) => (gainOf(t) > gainOf(best) ? t : best)))
      } catch (err) {
        // 보조 카드라 실패해도 알리지 않고 비운다
        if (!isApiError(err)) throw err
        if (alive) setTrend('none')
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [version])

  if (trend === null) return null

  if (trend === 'none') {
    return (
      <div className={styles.card}>
        <div className={styles.cardLabel}>성장</div>
        <div className={styles.weakEmpty}>무게와 횟수를 함께 기록하면 추정 1RM 변화를 보여드려요</div>
      </div>
    )
  }

  const points = trend.points
  const first = points[0]
  const last = points[points.length - 1]
  const delta = Math.round((last.estimatedOneRm - first.estimatedOneRm) * 10) / 10

  // 축 없는 작은 추세선. 값은 숫자로 적고, 선은 방향만 보여준다
  const values = points.map((p) => p.estimatedOneRm)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const line = points
    .map((p, i) => {
      const x = (i / (points.length - 1)) * 100
      const y = max === min ? 50 : 90 - ((p.estimatedOneRm - min) / (max - min)) * 80
      return `${x},${y}`
    })
    .join(' ')

  return (
    <button
      type="button"
      className={styles.card}
      onClick={() => navigate(`${paths.analysis}?view=stats&exercise=${trend.exerciseId}`)}
    >
      <div className={styles.cardHead}>
        <span className={styles.cardLabel}>성장 · {trend.exerciseName}</span>
        <Chevron />
      </div>
      <div className={styles.growthRow}>
        <div>
          <div className={styles.growthValue}>
            {last.estimatedOneRm}
            <span className={styles.growthUnit}>kg</span>
          </div>
          {points.length < 2 ? (
            <div className={styles.growthSub}>추정 1RM · 기록이 더 쌓이면 변화가 보여요</div>
          ) : delta === 0 ? (
            <div className={styles.growthSub}>추정 1RM · {formatMonthDay(first.date)}과 같아요</div>
          ) : (
            <div className={styles.growthSub}>
              추정 1RM · {formatMonthDay(first.date)}보다{' '}
              <span className={delta > 0 ? styles.up : styles.down}>
                {delta > 0 ? '+' : ''}
                {delta}kg
              </span>
            </div>
          )}
        </div>
        {points.length >= 2 && (
          <svg className={styles.spark} viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            <polyline points={line} />
          </svg>
        )}
      </div>
    </button>
  )
}
