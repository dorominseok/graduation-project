import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, Chevron, useToast } from '../../components'
import { analysisApi, isApiError, workoutApi } from '../../api'
import type { Balance, MuscleVolume, SessionSummary, WorkoutSession } from '../../api'
import { BAR_TICKS, barPercent, basisLabel } from '../analysis/format'
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

/** 홈에 펼쳐 보일 부족한 부위 수. 나머지는 "외 N곳"으로 접는다 */
const WEAK_ROWS = 3

/**
 * 약점.
 *
 * <p>얼마나 부족한지(주당 세트 + 막대)와, 그래서 뭘 할 수 있는지를 한 줄에 둔다.
 * 행을 누르면 오늘 기록 화면이 그 부위 종목 시트를 연 채로 열린다 — 종목 목록을
 * 구경시키면 거기서 기록 화면으로 돌아와 같은 종목을 다시 골라야 한다.
 *
 * <p>막대는 <b>하위 부위</b>에만 그린다. 하위가 판정 단위라 판정색을 칠해도 되고,
 * 목업처럼 상위 6종에 칠하면 "팔 · 최적"이 삼두 0세트를 가린다(LOG-09).
 *
 * <p>종목으로 이어주되 "보완하세요"라고 적지는 않는다. 판정은 부족하다는 데까지만
 * 알린다(분석 설계서 2.4) — 무엇을 할지는 루틴 추천의 몫이다.
 */
function WeaknessCard({ volume, balance }: { volume: MuscleVolume; balance: Balance }) {
  const navigate = useNavigate()
  const goAnalysis = () => navigate(paths.analysis)
  // 오늘 기록 화면을 그 부위 종목 시트가 열린 채로 연다
  const openPick = (key: string, label: string) =>
    navigate(`${paths.session}?pick=${key}&pickLabel=${encodeURIComponent(label)}`)

  const head = (label: string) => (
    <button type="button" className={styles.cardHeadButton} onClick={goAnalysis}>
      <span className={styles.cardLabel}>{label}</span>
      <span className={styles.cardMore}>
        분석 보기
        <Chevron />
      </span>
    </button>
  )

  if (volume.confidence.doneSessionCount === 0) {
    return (
      <div className={styles.card}>
        {head('약점')}
        <div className={styles.weakEmpty}>운동을 기록하고 종료하면 부족한 부위를 알려드려요</div>
      </div>
    )
  }

  // 부족과 권장 이하를 같이 띄운다 — 둘 다 더 해야 하는 건 같다. 가장 덜 한 곳부터
  // 세우면 부족(주 4세트 미만)이 자연히 위로 온다. 같은 "부족"이어도 0세트와 3세트는 다르다
  const lacking = volume.tiers
    .flatMap((t) => t.children)
    .filter((c) => c.verdict === 'INSUFFICIENT' || c.verdict === 'BELOW_RECOMMENDED')
    .sort((a, b) => a.weeklySets - b.weeklySets)
  const insufficientCount = lacking.filter((c) => c.verdict === 'INSUFFICIENT').length
  const belowCount = lacking.length - insufficientCount
  const title = [
    insufficientCount > 0 ? `부족 ${insufficientCount}` : null,
    belowCount > 0 ? `권장 이하 ${belowCount}` : null,
  ]
    .filter(Boolean)
    .join(' · ')
  const shown = lacking.slice(0, WEAK_ROWS)
  const imbalanced = balance.pairs.filter((p) => p.verdict === 'IMBALANCED')

  return (
    <div className={styles.card}>
      {head(`약점 · ${basisLabel(volume)}`)}

      {lacking.length === 0 ? (
        <div className={styles.weakTitle}>모든 부위가 권장 구간이에요</div>
      ) : (
        <>
          <div className={styles.weakTitle}>{title}</div>
          <ul className={styles.weakList}>
            {shown.map((c) => {
              const tone = c.verdict === 'INSUFFICIENT' ? styles.toneDanger : styles.toneWarn
              return (
                <li key={c.key}>
                  <button
                    type="button"
                    className={styles.weakRow}
                    onClick={() => openPick(c.key, c.label)}
                  >
                    <span className={styles.weakRowHead}>
                      <span className={styles.weakName}>{c.label}</span>
                      {/* 색만으로 가르지 않고 판정 이름을 같이 적는다 */}
                      <span className={`${styles.weakSets} ${tone}`}>
                        주 {c.weeklySets}세트 · {c.verdictLabel}
                      </span>
                      <span className={styles.weakLink}>
                        오늘 운동에 추가
                        <Chevron />
                      </span>
                    </span>
                    <span className={styles.weakTrack} aria-hidden="true">
                      {BAR_TICKS.map((tick) => (
                        <span key={tick} className={styles.weakTick} style={{ left: `${barPercent(tick)}%` }} />
                      ))}
                      {c.weeklySets > 0 && (
                        <span
                          className={`${styles.weakFill} ${tone}`}
                          style={{ width: `${barPercent(c.weeklySets)}%` }}
                        />
                      )}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
          {/* 나머지도 눌러서 바로 추가할 수 있어야 한다 — 글자로만 두면 권장 이하는 손댈 길이 없다 */}
          {lacking.length > WEAK_ROWS && (
            <div className={styles.weakMore}>
              {lacking.slice(WEAK_ROWS).map((c) => (
                <button
                  key={c.key}
                  type="button"
                  className={`${styles.weakChip} ${
                    c.verdict === 'INSUFFICIENT' ? styles.toneDanger : styles.toneWarn
                  }`}
                  onClick={() => openPick(c.key, c.label)}
                >
                  {c.label} {c.weeklySets}
                </button>
              ))}
            </div>
          )}
          <div className={styles.weakScale}>눈금 주 4 · 10 · 20세트 — 10세트부터 권장 구간</div>
        </>
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
    </div>
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
