import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Chevron, useToast } from '../../components'
import { analysisApi, isApiError, workoutApi } from '../../api'
import type { Balance, MuscleVolume, SessionSummary } from '../../api'
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
  /** 가장 최근에 운동한 날 둘. 누적을 세는 요청에서 같이 받는다 */
  recent: SessionSummary[]
}

/**
 * 홈 (목업 홈 탭).
 *
 * <p>목업의 카드 넷 중 "오늘의 루틴 추천"은 뺐다. 루틴은 10월5주에 들어오는데,
 * 그전에 "준비 중" 카드를 두면 검증 참여자에게 메인 기능이 고장 난 앱으로 보인다.
 * 그동안은 "운동 시작"이 주 버튼이다.
 *
 * <p>횟수는 운동한 날을 센다. 하루에 두 번 운동해도 한 번이다 — 기록이 하루에 하나다(LOG-39).
 * 체크한 세트는 바로 저장되므로 종료를 기다리지 않고 바로 센다.
 */
export function HomeScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { showToast } = useToast()

  const [counts, setCounts] = useState<Counts | null>(null)
  const [analysis, setAnalysis] = useState<{ volume: MuscleVolume; balance: Balance } | null>(null)

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
        const [month, total, week, volume, balance] = await Promise.all([
          workoutApi.getHistory({ from: monthStart, to: toDateString(now), size: 1 }),
          // 날짜를 생략하면 서버가 최근 30일만 보므로 시작일을 아주 앞으로 둔다
          workoutApi.getHistory({ from: '2000-01-01', to: toDateString(now), size: 2 }),
          workoutApi.getHistory({ from: toDateString(mondayOf(now)), to: toDateString(now), size: 7 }),
          analysisApi.getMuscleVolume(),
          analysisApi.getBalance(),
        ])
        if (!alive) return
        setCounts({
          month: month.page.totalElements,
          total: total.page.totalElements,
          week: week.content,
          recent: total.content,
        })
        setAnalysis({ volume, balance })
      } catch (err) {
        fail(err)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [fail])

  const weekDays = new Set(counts?.week.map((s) => s.performedOn))
  // 오늘 기록. 세트가 있는 날만 히스토리에 오므로, 있으면 오늘 이미 운동한 것이다
  const todayRecord = counts?.week.find((s) => s.performedOn === todayKey)

  return (
    <div className={styles.page}>
      {/* 헤더는 두지 않는다. "홈"이라는 글자는 탭바가 이미 말하고 있다 */}
      <div className={styles.greeting}>
        <div className={styles.greetingName}>{user ? `${user.nickname}님` : '안녕하세요'}</div>
        <div className={styles.greetingStats}>{counts ? greetingStats(today, counts) : ' '}</div>
      </div>

      {/*
        목업의 루틴 추천 카드 자리. 루틴이 들어오는 10월5주에는 이 카드가 루틴 카드가 된다 —
        그전까지는 오늘 한 것(또는 얼마나 쉬었는지)과 기록 버튼을 둔다. 시작·종료가 없어서
        오늘 이미 운동했으면 같은 기록에 이어서 쌓는다.
      */}
      {counts && (
        <div className={styles.hero}>
          <div className={styles.heroTitle}>오늘 운동</div>
          <div className={styles.heroSub}>
            {todayRecord
              ? `${todayRecord.exerciseCount}종목 · ${todayRecord.setCount}세트 기록했어요`
              : restLine(counts.recent[0], today)}
          </div>
          <button type="button" className={styles.start} onClick={() => navigate(paths.session)}>
            {todayRecord ? '이어서 기록' : '운동 시작'}
          </button>
        </div>
      )}

      {analysis && <WeaknessCard volume={analysis.volume} />}
      {analysis && analysis.volume.confidence.workoutDayCount > 0 && (
        <BalanceCard volume={analysis.volume} balance={analysis.balance} />
      )}

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
        </div>
      )}

      {counts && (
        <div className={styles.card}>
          <div className={styles.cardLabel}>최근 기록</div>
          {counts.recent.length === 0 ? (
            <div className={styles.weakEmpty}>운동한 날이 여기 쌓여요</div>
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
    </div>
  )
}

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
/** 접힌 상태에서 보일 막대 수. 아홉 곳이 다 나오면 홈의 나머지 카드가 한참 밀린다 */
const WEAK_ROWS = 3

function WeaknessCard({ volume }: { volume: MuscleVolume }) {
  const navigate = useNavigate()
  const [expanded, setExpanded] = useState(false)
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

  if (volume.confidence.workoutDayCount === 0) {
    return (
      <div className={styles.card}>
        {head('약점')}
        <div className={styles.weakEmpty}>운동을 기록하면 부족한 부위를 알려드려요</div>
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

  return (
    <div className={styles.card}>
      {head(`약점 · ${basisLabel(volume)}`)}

      {lacking.length === 0 ? (
        <div className={styles.weakTitle}>모든 부위가 권장 구간이에요</div>
      ) : (
        <>
          <div className={styles.weakTitle}>{title}</div>
          <ul className={styles.weakList}>
            {/* 펼치면 전부 막대로 그린다. 권장 이하야말로 10까지 얼마 남았는지가 막대로 보여야
                한다 — 나머지를 칩으로 줄였더니 같은 정보를 두 가지로 그리게 됐다. 기본은 접어둔다 */}
            {(expanded ? lacking : lacking.slice(0, WEAK_ROWS)).map((c) => {
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
          {lacking.length > WEAK_ROWS && (
            <button
              type="button"
              className={styles.weakToggle}
              aria-expanded={expanded}
              onClick={() => setExpanded((v) => !v)}
            >
              {expanded ? '접기' : `${lacking.length - WEAK_ROWS}곳 더 보기`}
              <span className={`${styles.weakToggleIcon} ${expanded ? styles.weakToggleOpen : ''}`}>
                <Chevron />
              </span>
            </button>
          )}
          <div className={styles.weakScale}>눈금 주 4 · 10 · 20세트 — 10세트부터 권장 구간</div>
        </>
      )}

      {volume.confidence.level === 'LOW' && (
        <div className={styles.weakNote}>
          운동한 날이 {volume.confidence.workoutDayCount}일이라 참고만 해주세요
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
  if (days === 1) return '마지막 운동은 어제예요'
  return `마지막 운동 ${days}일 전 · ${formatMonthDay(last.performedOn)}`
}

type Pair = Balance['pairs'][number]

const PAIR_VERDICT_CLASS: Record<Pair['verdict'], string> = {
  BALANCED: styles.pillGood,
  IMBALANCED: styles.pillDanger,
  INSUFFICIENT_DATA: styles.pillNeutral,
}

/** 배지 옆 한 줄. 처방("당기기를 늘리세요")은 하지 않고 비율만 적는다 */
function pairLine(pair: Pair): string {
  if (pair.verdict === 'INSUFFICIENT_DATA') return '두 쪽 모두 기록이 없어요'
  const bigger = pair.biggerSide === pair.left.key ? pair.left : pair.right
  const smaller = bigger === pair.left ? pair.right : pair.left
  if (pair.smallerSideZero) return `${smaller.label} 기록이 없어요`
  return `${bigger.label}가 ${pair.ratio}배`
}

/**
 * 균형 — 밀기/당기기, 상체/하체 (명세 8.3).
 *
 * <p>약점 카드에서 따로 뺐다. 처음엔 불균형일 때만 약점 카드 밑에 빨간 한 줄로
 * 붙였는데, 그러면 정상일 때는 균형을 봤다는 흔적조차 없고, "어느 부위가 부족한가"와
 * "양쪽이 맞는가"라는 다른 질문이 한 카드에 섞였다. 두 쌍을 늘 같이 보여준다.
 */
function BalanceCard({ volume, balance }: { volume: MuscleVolume; balance: Balance }) {
  const navigate = useNavigate()

  return (
    <button type="button" className={styles.card} onClick={() => navigate(paths.analysis)}>
      <div className={styles.cardHead}>
        <span className={styles.cardLabel}>균형 · {basisLabel(volume)}</span>
        <Chevron />
      </div>

      {balance.pairs.map((pair) => {
        const max = Math.max(pair.left.weeklySets, pair.right.weeklySets, 1)
        return (
          <div key={pair.key} className={styles.pair}>
            <div className={styles.pairHead}>
              <span className={styles.pairTitle}>{pair.label}</span>
              <span className={styles.pairLine}>{pairLine(pair)}</span>
              <span className={`${styles.pill} ${PAIR_VERDICT_CLASS[pair.verdict]}`}>{pair.verdictLabel}</span>
            </div>
            {/* 가운데 축에서 양쪽으로 자란다. 축 쪽은 각지고 바깥 끝만 둥글다 */}
            <div className={styles.diverge} aria-hidden="true">
              <span className={styles.half}>
                <span
                  className={`${styles.sideBar} ${styles.sideLeft}`}
                  style={{ width: `${(pair.left.weeklySets / max) * 100}%` }}
                />
              </span>
              <span className={styles.axis} />
              <span className={`${styles.half} ${styles.halfRight}`}>
                <span
                  className={`${styles.sideBar} ${styles.sideRight}`}
                  style={{ width: `${(pair.right.weeklySets / max) * 100}%` }}
                />
              </span>
            </div>
            <div className={styles.sideLabels}>
              <span>
                {pair.left.label} {pair.left.weeklySets}
              </span>
              <span>
                {pair.right.label} {pair.right.weeklySets}
              </span>
            </div>
          </div>
        )
      })}

      <div className={styles.weakScale}>주당 평균 세트 · {balance.ratioThreshold}배까지 정상</div>
    </button>
  )
}
