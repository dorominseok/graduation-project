import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { BottomSheet, NumberPad, useToast } from '../../components'
import { ErrorCodes, exerciseApi, isApiError, statsApi, workoutApi } from '../../api'
import type { Exercise, MeasureType, MuscleGroupKey, SessionIntensity, WorkoutSession } from '../../api'
import { paths } from '../../app/paths'
import { ExercisePicker } from './ExercisePicker'
import type { PickerStep } from './ExercisePicker'
import { BODY_PART_LABEL, EQUIPMENT_LABEL, MEASURE_CHIP_LABEL } from './exerciseLabels'
import styles from './session.module.css'

/** 화면에만 있는 세트 행. 완료 체크를 해야 서버로 간다(LOG-05). */
interface SetRow {
  /** 멱등 키이자 React key. 완료 체크 시점이 아니라 행을 만들 때 미리 만든다 */
  clientSetId: string
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  isWarmup: boolean
  /**
   * 세트 사이 쉬는 시간. 서버에는 보내지 않는다 — 기록이 아니라 운동 중에만
   * 쓰는 타이머 값이고, workout_sets에 그 칸이 없다(명세 6.10).
   */
  restSec: number
  /** 저장된 세트의 서버 id. null이면 아직 계획 행이다 */
  savedId: number | null
  /** 직전 기록에서 채운 행인지. 값의 출처를 줄 아래에 적어준다 */
  fromPrev: boolean
}

interface ExerciseCard {
  exerciseId: number
  name: string
  measureType: MeasureType
  rows: SetRow[]
  /** "9월 24일 (수) · 60kg×10" 같은 직전 기록 한 줄. 없으면 처음 하는 종목이다 */
  prevLabel: string | null
}

/** 어떤 값을 입력받는 중인지. 숫자 패드가 열릴 때만 채워진다 */
interface Editing {
  cardIndex: number
  rowIndex: number
  field: 'weightKg' | 'reps' | 'durationSec'
}

const FIELD_LABEL = { weightKg: '중량', reps: '횟수', durationSec: '시간' } as const
const FIELD_UNIT = { weightKg: 'kg', reps: '회', durationSec: '초' } as const

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 원판 한 쌍이 보통 2.5kg다. 헬스장에서 실제로 올릴 수 있는 단위로 움직인다 */
const STEP_WEIGHT = 2.5
const STEP_REST = 30
const DEFAULT_REST_SEC = 90
/** 저장된 줄이 초록으로 물들어 있는 시간 */
const FLASH_MS = 600
/** "휴식 끝" 띠를 띄워두는 시간 */
const REST_DONE_MS = 3000

function emptyRow(): SetRow {
  return {
    clientSetId: crypto.randomUUID(),
    weightKg: null,
    reps: null,
    durationSec: null,
    isWarmup: false,
    restSec: DEFAULT_REST_SEC,
    savedId: null,
    fromPrev: false,
  }
}

/** 측정 유형이 실제로 쓰는 입력 칸만 고른다(명세 5.1 표). */
function fieldsOf(measureType: MeasureType): Editing['field'][] {
  if (measureType === 'TIME') return ['durationSec']
  if (measureType === 'BODYWEIGHT_REPS') return ['reps']
  return ['weightKg', 'reps']
}

/** 로컬 날짜를 YYYY-MM-DD로. toISOString은 UTC로 바꿔 하루가 밀린다 */
function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

function shiftDate(key: string, delta: number): string {
  const [year, month, day] = key.split('-').map(Number)
  return toDateString(new Date(year, month - 1, day + delta))
}

function formatDayLabel(key: string): string {
  const [year, month, day] = key.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()]
  return `${month}월 ${day}일 (${weekday})`
}

function formatElapsed(startedAt: string | null, now: number): string {
  if (!startedAt) return '00:00'
  const seconds = Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000))
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0')
  const ss = String(seconds % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function formatRest(seconds: number): string {
  const mm = Math.floor(seconds / 60)
  const ss = String(seconds % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

/** 중량은 2.5처럼 소수가 나오지만 60.0으로 적지는 않는다 */
function trimNumber(value: number): string {
  return String(Math.round(value * 10) / 10)
}

/** 세트 한 줄을 "60kg×10" 꼴로. 카드 요약과 직전 기록 줄이 같은 말투를 쓴다 */
function describeSet(
  measureType: MeasureType,
  set: { weightKg: number | null; reps: number | null; durationSec: number | null },
): string {
  if (measureType === 'TIME') return `${set.durationSec ?? 0}초`
  if (measureType === 'BODYWEIGHT_REPS') return `${set.reps ?? 0}회`
  const sign = measureType === 'WEIGHTED_BODYWEIGHT' ? '+' : ''
  return `${sign}${trimNumber(set.weightKg ?? 0)}kg×${set.reps ?? 0}`
}

/**
 * 운동 기록 (명세 6.2~6.4).
 *
 * <p>두 단계다. 목록에서는 그날 무슨 종목을 했는지만 보고, 종목을 누르면 그
 * 종목의 세트만 남는다 — 종목 셋에 세트 다섯이면 한 화면에 열다섯 줄이 되어
 * 어느 줄을 누르는지 알 수 없다.
 *
 * <p>세션은 화면에 들어올 때가 아니라 <b>첫 세트를 완료 체크할 때</b> 만든다 —
 * 들어왔다 그냥 나가면 빈 세션이 남기 때문이다. 종목을 고르고 행을 채우는
 * 동안은 서버에 아무것도 가지 않는다.
 */
export function SessionScreen() {
  const navigate = useNavigate()
  // 경로에 id가 있으면 지난 기록을 여는 것이고, 없으면 오늘 운동이다.
  const { sessionId: sessionIdParam } = useParams()
  const { showToast } = useToast()

  // 어느 종목을 펼쳐 보고 있는지. 화면을 갈아끼우지 않고 URL만 바꾸므로
  // 입력 중이던 미저장 행이 그대로 남는다.
  const [params, setParams] = useSearchParams()
  const openedId = params.get('exercise')
  // 달력에서 기록 없는 날을 눌러 들어온 경우. 그 날짜로 사후 입력을 만든다(명세 6.2).
  const backfillDate = params.get('date')

  const [session, setSession] = useState<WorkoutSession | null>(null)
  const [cards, setCards] = useState<ExerciseCard[]>([])
  const [editing, setEditing] = useState<Editing | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  // 펼친 세트와 방금 저장된 세트. 둘 다 clientSetId로 가리킨다.
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [flashId, setFlashId] = useState<string | null>(null)

  // 휴식 타이머. 기록이 아니라 운동 중 보조라 화면에만 있다.
  const [rest, setRest] = useState<{ left: number; total: number } | null>(null)
  const [restDone, setRestDone] = useState(false)

  // 종목 고르기는 화면을 옮기지 않고 시트로 띄운다. 기록 화면이 살아 있어야
  // 아직 체크하지 않은 종목들이 그대로 남는다 — 루틴을 미리 세팅해두고
  // 하나씩 수행하는 흐름이 이걸로 가능해진다.
  //
  // 홈의 약점에서 "오늘 운동에 추가"로 들어오면(?pick=판정 부위) 그 부위 종목만 띄운 채
  // 시트를 연다(LOG-29). 무슨 종목이 있는지 구경하는 게 아니라 오늘 할 운동에 바로 넣는 길이다.
  const pickParam = params.get('pick') as MuscleGroupKey | null
  const [pickerOpen, setPickerOpen] = useState(pickParam !== null)
  const [pickerStep, setPickerStep] = useState<PickerStep>(() =>
    pickParam
      ? { category: null, group: null, muscleGroup: pickParam, muscleLabel: params.get('pickLabel') ?? undefined }
      : { category: null, group: null },
  )

  // 종목 정보와 일괄 수정도 같은 이유로 시트다.
  const [info, setInfo] = useState<Exercise | null>(null)
  const [batchOpen, setBatchOpen] = useState(false)
  const [batchPicked, setBatchPicked] = useState<Record<string, boolean>>({})
  const [batchPrimary, setBatchPrimary] = useState(0)
  const [batchReps, setBatchReps] = useState(0)
  // 빼려는 종목의 자리. 기록된 세트가 있을 때만 채워진다
  const [removeTarget, setRemoveTarget] = useState<number | null>(null)

  // 그날 추정 1RM(명세 7.2). 세트가 저장·수정·삭제될 때마다 다시 읽는다 —
  // 화면에서 Epley를 계산하면 서버와 공식이 둘이 된다.
  const [intensity, setIntensity] = useState<SessionIntensity | null>(null)
  const [statsVersion, setStatsVersion] = useState(0)
  const bumpStats = () => setStatsVersion((v) => v + 1)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  // 시트를 연 뒤에는 주소에서 지운다. 남겨두면 새로고침할 때마다 시트가 다시 열린다
  useEffect(() => {
    if (!pickParam) return
    const next = new URLSearchParams(params)
    next.delete('pick')
    next.delete('pickLabel')
    setParams(next, { replace: true })
  }, [pickParam, params, setParams])

  // 지난 기록이면 그 세션을, 아니면 진행 중인 세션을 연다(명세 6.5·6.7).
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const current = sessionIdParam
          ? await workoutApi.getSession(Number(sessionIdParam))
          : backfillDate
            ? null // 지난 날짜에 새로 적는 중이라 이어쓸 세션이 없다
            : await workoutApi.getCurrent()
        if (!alive) return
        setSession(current)
        setCards(
          (current?.exercises ?? []).map((group) => ({
            exerciseId: group.exerciseId,
            name: group.exerciseName,
            measureType: group.measureType,
            prevLabel: null,
            rows: group.sets.map((set) => ({
              clientSetId: set.clientSetId,
              weightKg: set.weightKg,
              reps: set.reps,
              durationSec: set.durationSec,
              isWarmup: set.isWarmup,
              restSec: DEFAULT_REST_SEC,
              savedId: set.id,
              fromPrev: false,
            })),
          })),
        )
      } catch (err) {
        if (isApiError(err)) showToast({ message: err.message, tone: 'danger' })
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [sessionIdParam, backfillDate, showToast])

  // 종목을 펼쳤을 때만 읽는다. 목록 화면에서는 지표를 보여주지 않는다.
  const sessionId = session?.id ?? null
  useEffect(() => {
    if (sessionId === null || openedId === null) return
    let alive = true
    const load = async () => {
      try {
        const result = await statsApi.getSessionIntensity(sessionId)
        if (alive) setIntensity(result)
      } catch {
        // 지표는 보조 정보다. 못 읽어도 기록은 계속할 수 있어야 해서 알리지 않는다
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [sessionId, openedId, statsVersion])

  // 경과 시간은 서버가 주지 않는다. 시작 시각부터의 벽시계 차이를 화면이 센다.
  useEffect(() => {
    if (!session?.startedAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [session?.startedAt])

  // 휴식 카운트다운. 0이 되면 "휴식 끝" 띠로 바뀐다.
  useEffect(() => {
    if (!rest) return
    const timer = window.setInterval(() => {
      setRest((prev) => {
        if (!prev) return prev
        if (prev.left <= 1) {
          setRestDone(true)
          return null
        }
        return { ...prev, left: prev.left - 1 }
      })
    }, 1000)
    return () => window.clearInterval(timer)
  }, [rest])

  useEffect(() => {
    if (!restDone) return
    const timer = window.setTimeout(() => setRestDone(false), REST_DONE_MS)
    return () => window.clearTimeout(timer)
  }, [restDone])

  useEffect(() => {
    if (!flashId) return
    const timer = window.setTimeout(() => setFlashId(null), FLASH_MS)
    return () => window.clearTimeout(timer)
  }, [flashId])

  const addExercise = useCallback(async (exercise: Exercise) => {
    // 직전 기록으로 행을 채운다(명세 5.5). 없으면 빈 행 하나로 시작한다.
    let rows: SetRow[] = [emptyRow()]
    let prevLabel: string | null = null
    try {
      const last = await exerciseApi.getLastPerformance(exercise.id)
      if (last && last.sets.length > 0) {
        rows = last.sets.map((set) => ({
          clientSetId: crypto.randomUUID(),
          weightKg: set.weightKg,
          reps: set.reps,
          durationSec: set.durationSec,
          isWarmup: set.isWarmup,
          restSec: DEFAULT_REST_SEC,
          savedId: null,
          fromPrev: true,
        }))
        const working = last.sets.filter((set) => !set.isWarmup)
        const shown = working.length > 0 ? working : last.sets
        prevLabel = `${formatDayLabel(last.performedOn)} · ${shown
          .map((set) => describeSet(last.measureType, set))
          .join(', ')}`
      }
    } catch (err) {
      // 프리필은 편의 기능이라 실패해도 기록은 계속할 수 있어야 한다.
      if (!isApiError(err)) throw err
    }

    setCards((prev) => [
      ...prev,
      {
        exerciseId: exercise.id,
        name: exercise.nameKo,
        measureType: exercise.measureType,
        rows,
        prevLabel,
      },
    ])
  }, [])

  const handlePick = (exercise: Exercise) => {
    setPickerOpen(false)
    setPickerStep({ category: null, group: null })
    void addExercise(exercise)
  }

  /**
   * 세션이 없으면 만든다. 이미 진행 중인 게 있으면 그걸 이어 쓴다.
   *
   * <p>날짜를 들고 들어왔으면 그 날짜의 사후 입력이다 — 저장 시각이 실제 운동
   * 시각과 다르므로 운동 시간을 산출하지 않는다(기록 방식 4.3).
   */
  const ensureSession = async (): Promise<WorkoutSession> => {
    if (session) return session
    try {
      const created = await workoutApi.createSession(
        backfillDate
          ? { performedOn: backfillDate, source: 'BACKFILL' }
          : { performedOn: toDateString(new Date()), source: 'LIVE' },
      )
      setSession(created)
      return created
    } catch (err) {
      if (isApiError(err) && err.code === ErrorCodes.DRAFT_SESSION_EXISTS) {
        const current = await workoutApi.getCurrent()
        if (current) {
          setSession(current)
          return current
        }
      }
      throw err
    }
  }

  const updateRow = (cardIndex: number, rowIndex: number, patch: Partial<SetRow>) => {
    setCards((prev) =>
      prev.map((card, ci) =>
        ci !== cardIndex
          ? card
          : {
              ...card,
              rows: card.rows.map((row, ri) => (ri !== rowIndex ? row : { ...row, ...patch })),
            },
      ),
    )
  }

  const toggleSaved = async (cardIndex: number, rowIndex: number) => {
    if (busy) return
    const card = cards[cardIndex]
    const row = card.rows[rowIndex]

    // 이미 저장된 행을 다시 누르면 기록을 취소한다.
    if (row.savedId !== null) {
      if (!session) return
      setBusy(true)
      try {
        await workoutApi.deleteSet(session.id, row.savedId)
        updateRow(cardIndex, rowIndex, { savedId: null })
        bumpStats()
      } catch (err) {
        fail(err)
      } finally {
        setBusy(false)
      }
      return
    }

    // 값이 아직 없는 줄은 체크할 것이 아니라 채울 것이다. 패널을 열어준다.
    const missing = fieldsOf(card.measureType).filter((field) => row[field] == null)
    if (missing.length > 0) {
      setExpandedId(row.clientSetId)
      return
    }

    setBusy(true)
    try {
      const target = await ensureSession()
      const saved = await workoutApi.addSet(target.id, {
        clientSetId: row.clientSetId,
        exerciseId: card.exerciseId,
        weightKg: row.weightKg,
        reps: row.reps,
        durationSec: row.durationSec,
        isWarmup: row.isWarmup,
      })
      updateRow(cardIndex, rowIndex, { savedId: saved.id })
      bumpStats()
      setFlashId(row.clientSetId)
      setExpandedId(null)

      // 본세트를 하나 끝냈으면 쉴 차례다. 워밍업은 쉬지 않고 이어간다.
      if (!row.isWarmup && row.restSec > 0) {
        setRestDone(false)
        setRest({ left: row.restSec, total: row.restSec })
      }

      // 첫 세트가 저장되면 서버가 startedAt을 채운다. 경과 시간을 띄우려면 다시 읽어야 한다.
      if (!session?.startedAt) {
        setSession(await workoutApi.getSession(target.id))
      }
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  /**
   * 워밍업 표시 (LOG-02).
   *
   * 워밍업 세트는 볼륨 집계에서 빠진다 — ACSM 기준이 본세트 기준이라 단위를 맞춰야
   * 하고, 준비 세트까지 세면 부족한 부위가 충분한 것으로 보인다. 표시할 방법이
   * 없으면 그 규칙 자체가 동작하지 않는다.
   */
  const toggleWarmup = async (cardIndex: number, rowIndex: number) => {
    const row = cards[cardIndex].rows[rowIndex]
    const next = !row.isWarmup
    updateRow(cardIndex, rowIndex, { isWarmup: next })

    // 이미 저장된 세트면 서버 값도 맞춘다(명세 6.11).
    if (row.savedId !== null && session) {
      try {
        await workoutApi.updateSet(session.id, row.savedId, { isWarmup: next })
      } catch (err) {
        updateRow(cardIndex, rowIndex, { isWarmup: !next })
        fail(err)
      }
    }
  }

  /** 값 하나를 고친다. 이미 저장된 세트면 서버 값까지 맞춘다(명세 6.11) */
  const patchValue = async (
    cardIndex: number,
    rowIndex: number,
    field: Editing['field'],
    value: number,
  ) => {
    const row = cards[cardIndex].rows[rowIndex]
    const before = row[field]
    updateRow(cardIndex, rowIndex, { [field]: value })
    if (row.savedId === null || !session) return
    try {
      await workoutApi.updateSet(session.id, row.savedId, { [field]: value })
      bumpStats()
    } catch (err) {
      updateRow(cardIndex, rowIndex, { [field]: before })
      fail(err)
    }
  }

  const addRow = (cardIndex: number) => {
    setCards((prev) =>
      prev.map((card, ci) => {
        if (ci !== cardIndex) return card
        // 직전 세트를 복사해 채운다(기록 방식 3.4 ②). 보통 같은 무게로 이어간다.
        const last = card.rows[card.rows.length - 1]
        const next = last
          ? { ...last, clientSetId: crypto.randomUUID(), savedId: null, fromPrev: false }
          : emptyRow()
        return { ...card, rows: [...card.rows, next] }
      }),
    )
  }

  const removeRow = async (cardIndex: number, rowIndex: number) => {
    const row = cards[cardIndex].rows[rowIndex]
    if (row.savedId !== null && session) {
      try {
        await workoutApi.deleteSet(session.id, row.savedId)
        bumpStats()
      } catch (err) {
        fail(err)
        return
      }
    }
    setCards((prev) =>
      prev.map((card, ci) =>
        ci !== cardIndex ? card : { ...card, rows: card.rows.filter((_, ri) => ri !== rowIndex) },
      ),
    )
  }

  /**
   * 종목 빼기.
   *
   * <p>아직 체크한 세트가 없으면 잃을 것이 없어 바로 뺀다. 기록이 있으면
   * 되돌릴 수 없는 삭제라 한 번 물어본다 — 이 버튼이 카드를 여는 자리
   * 바로 옆에 있어서 잘못 누르기 쉽다.
   */
  const askRemoveCard = (cardIndex: number) => {
    if (cards[cardIndex].rows.some((row) => row.savedId !== null)) {
      setRemoveTarget(cardIndex)
      return
    }
    setCards((prev) => prev.filter((_, ci) => ci !== cardIndex))
  }

  const removeCard = async (cardIndex: number) => {
    const card = cards[cardIndex]
    if (session) {
      // 세트를 하나씩 지우면 중간에 끊겼을 때 일부만 남는다(LOG-22).
      try {
        await workoutApi.deleteExercise(session.id, card.exerciseId)
      } catch (err) {
        fail(err)
        return
      }
    }
    setCards((prev) => prev.filter((_, ci) => ci !== cardIndex))
    setRemoveTarget(null)
  }

  const finish = async () => {
    if (!session || busy) return
    setBusy(true)
    try {
      await workoutApi.complete(session.id)
      showToast({ message: '운동을 기록했어요' })
      navigate(paths.home, { replace: true })
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const openInfo = async (exerciseId: number) => {
    try {
      setInfo(await exerciseApi.get(exerciseId))
    } catch (err) {
      fail(err)
    }
  }

  /**
   * 다른 날짜의 기록으로 옮긴다.
   *
   * <p>세션 id는 날짜만으로 알 수 없어 그 날짜로 한 번 조회한다. 기록이 없으면
   * 그 날짜의 사후 입력으로 들어간다 — 달력에서 날짜를 누르는 것과 같은 규칙이다.
   */
  const goToDay = async (delta: number) => {
    const today = toDateString(new Date())
    const base = session?.performedOn ?? backfillDate ?? today
    const target = shiftDate(base, delta)
    if (target > today) {
      showToast({ message: '아직 오지 않은 날은 기록할 수 없어요', tone: 'danger' })
      return
    }
    try {
      const page = await workoutApi.getHistory({ from: target, to: target, size: 5 })
      if (page.content.length > 0) {
        navigate(paths.sessionDetail(page.content[0].id), { replace: true })
      } else {
        navigate(`${paths.session}?date=${target}`, { replace: true })
      }
    } catch (err) {
      fail(err)
    }
  }

  const savedCount = cards.reduce(
    (sum, card) => sum + card.rows.filter((row) => row.savedId !== null).length,
    0,
  )

  const openedIndex = cards.findIndex((card) => String(card.exerciseId) === openedId)
  const opened = openedIndex >= 0 ? cards[openedIndex] : null

  const backToList = () => setParams(backfillDate ? { date: backfillDate } : {})
  const openCard = (exerciseId: number) =>
    setParams(
      backfillDate
        ? { date: backfillDate, exercise: String(exerciseId) }
        : { exercise: String(exerciseId) },
    )

  /** 지난 기록을 보는 중인지. 그때는 진행 시간 대신 날짜 이동을 띄운다 */
  const viewingPast = sessionIdParam !== undefined || session?.status === 'DONE'
  const showDayNav = viewingPast || backfillDate !== null
  const dayKey = session?.performedOn ?? backfillDate ?? toDateString(new Date())
  /** 휴식 띠가 떠 있으면 스크롤 끝이 가려진다 */
  const restShowing = rest !== null || restDone

  const restOverlay = (
    <>
      {rest && (
        <div className={styles.rest}>
          <span className={styles.restTime}>{formatRest(rest.left)}</span>
          <div className={styles.restMain}>
            <div className={styles.restLabel}>휴식 중</div>
            <div className={styles.restTrack}>
              <div
                className={styles.restBar}
                style={{ width: `${Math.round((rest.left / rest.total) * 100)}%` }}
              />
            </div>
          </div>
          <div className={styles.restButtons}>
            <button
              type="button"
              className={styles.restButton}
              onClick={() => setRest({ ...rest, left: Math.max(1, rest.left - STEP_REST) })}
            >
              −30s
            </button>
            <button
              type="button"
              className={styles.restButton}
              onClick={() =>
                setRest({
                  left: rest.left + STEP_REST,
                  total: Math.max(rest.total, rest.left + STEP_REST),
                })
              }
            >
              +30s
            </button>
            <button type="button" className={styles.restSkip} onClick={() => setRest(null)}>
              건너뛰기
            </button>
          </div>
        </div>
      )}

      {restDone && <div className={styles.restDone}>휴식 끝 · 다음 세트 시작</div>}
    </>
  )

  /* 기록 중에는 상세 화면으로 나갈 수 없다 — 나가면 체크 안 한 종목이 사라진다 */
  const infoSheet = (
    <BottomSheet
      open={info !== null}
      onClose={() => setInfo(null)}
      title={<span className={styles.sheetTitle}>종목 정보</span>}
    >
      {info && (
        <>
          <div className={styles.infoName}>{info.nameKo}</div>
          <div className={styles.infoChips}>
            <span className={styles.typeTag}>
              {BODY_PART_LABEL[info.bodyPart] ?? info.bodyPart}
            </span>
            <span className={styles.typeTag}>
              {MEASURE_CHIP_LABEL[info.measureType] ?? info.measureType}
            </span>
            <span className={styles.typeTag}>
              {EQUIPMENT_LABEL[info.equipment] ?? info.equipment}
            </span>
            {info.groupName && <span className={styles.typeTag}>{info.groupName}</span>}
          </div>
          <p className={styles.infoNote}>
            {info.nameEn ?? '영문 이름이 등록되지 않은 종목이에요'}
            <br />
            동작 설명과 그림은 아직 준비 중이에요.
          </p>
        </>
      )}
    </BottomSheet>
  )

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.header}>
          <div className={styles.headerSpacer} />
          <div className={styles.headerNav}>
            <span className={styles.headerTitle}>운동 기록</span>
          </div>
          <div className={styles.headerSpacer} />
        </div>
        <div className={styles.loading}>불러오는 중…</div>
      </div>
    )
  }

  // ── 2단계: 종목 하나의 세트
  if (opened) {
    const fields = fieldsOf(opened.measureType)
    const primaryField = fields[0]
    const hasReps = fields.includes('reps') && fields.length > 1
    const showStats =
      opened.measureType === 'WEIGHT_REPS' || opened.measureType === 'WEIGHTED_BODYWEIGHT'
    const step = primaryField === 'weightKg' ? STEP_WEIGHT : 1
    const prevCard = openedIndex > 0 ? cards[openedIndex - 1] : null
    const nextCard = openedIndex < cards.length - 1 ? cards[openedIndex + 1] : null

    const savedRows = opened.rows.filter((row) => row.savedId !== null)
    const savedWeights = savedRows
      .map((row) => row.weightKg)
      .filter((weight): weight is number => weight != null)
    const savedMaxWeight = savedWeights.length > 0 ? Math.max(...savedWeights) : null
    const savedWorkingSets = savedRows.filter((row) => !row.isWarmup).length
    // 중량 딥스 같은 종목의 무게는 몸무게가 아니라 매단 무게라 1RM을 내지 않는다(명세 7.1)
    const isAddedWeight = opened.measureType === 'WEIGHTED_BODYWEIGHT'
    // 다른 세션의 응답이 남아 있을 수 있어 세션 번호까지 맞춰 본다
    const openedOneRm =
      intensity && intensity.sessionId === session?.id
        ? (intensity.exercises.find((e) => e.exerciseId === opened.exerciseId)?.estimatedOneRm ?? null)
        : null

    // 워밍업은 세트 번호에서 빠진다. 본세트만 1, 2, 3으로 센다.
    const badges = opened.rows.map((row, index) =>
      row.isWarmup
        ? 'W'
        : String(opened.rows.slice(0, index + 1).filter((it) => !it.isWarmup).length),
    )

    return (
      <>
        <div className={styles.page}>
          <div className={styles.header}>
            <button
              type="button"
              className={styles.headerBack}
              onClick={backToList}
              aria-label="종목 목록으로"
            >
              ←
            </button>
            <div className={styles.headerNav}>
              <button
                type="button"
                className={styles.navArrow}
                disabled={!prevCard}
                onClick={() => prevCard && openCard(prevCard.exerciseId)}
                aria-label="이전 종목"
              >
                ‹
              </button>
              <span className={styles.headerTitle}>{opened.name}</span>
              <button
                type="button"
                className={styles.navArrow}
                disabled={!nextCard}
                onClick={() => nextCard && openCard(nextCard.exerciseId)}
                aria-label="다음 종목"
              >
                ›
              </button>
            </div>
            <button
              type="button"
              className={styles.headerBack}
              onClick={() => void openInfo(opened.exerciseId)}
              aria-label="종목 정보"
            >
              ⓘ
            </button>
          </div>

          {/*
            지표 3종. 저장된 세트만 센다 — 아직 체크하지 않은 줄은 한 것이 아니다.
            목업의 "볼륨(kg)"은 톤 방식이라 쓰지 않고(분석 설계서 1.1), 이 앱이
            볼륨이라 부르는 본세트 수로 바꿨다.
          */}
          {showStats && (
            <div className={styles.stats}>
              {[
                {
                  label: '최대 무게',
                  value:
                    savedMaxWeight == null
                      ? '—'
                      : `${isAddedWeight ? '+' : ''}${trimNumber(savedMaxWeight)}`,
                  unit: savedMaxWeight == null ? '' : 'kg',
                  note: isAddedWeight ? '추가 중량 기준' : '이번 기록',
                },
                {
                  label: '최대 1RM',
                  value: openedOneRm == null ? '—' : String(openedOneRm),
                  unit: openedOneRm == null ? '' : 'kg',
                  note: isAddedWeight ? '중량 종목만 추정' : '12회 이하 세트 기준',
                },
                {
                  label: '볼륨',
                  value: String(savedWorkingSets),
                  unit: '세트',
                  note: '워밍업 제외',
                },
              ].map((stat) => (
                <div key={stat.label} className={styles.stat}>
                  <div className={styles.statLabel}>{stat.label}</div>
                  <div className={styles.statValueRow}>
                    <span className={styles.statValue}>{stat.value}</span>
                    <span className={styles.statUnit}>{stat.unit}</span>
                  </div>
                  <div className={styles.statNote}>{stat.note}</div>
                </div>
              ))}
            </div>
          )}

          {opened.prevLabel && <div className={styles.prevBar}>지난 기록 {opened.prevLabel}</div>}

          <div className={`${styles.setScroll} ${restShowing ? styles.scrollWithRest : ''}`}>
            <div className={styles.setList}>
              {opened.rows.map((row, rowIndex) => {
                const done = row.savedId !== null
                const expanded = expandedId === row.clientSetId
                return (
                  <div
                    key={row.clientSetId}
                    className={`${styles.setWrap} ${
                      flashId === row.clientSetId ? styles.setWrapFlash : ''
                    }`}
                  >
                    {/*
                      줄 전체가 완료 체크다 — 운동 중에는 작은 체크박스를 정확히
                      누르기 어렵다. ✕·⌄는 그 안에서 다른 일을 하므로 클릭이
                      위로 올라가지 않게 막는다.
                    */}
                    <div
                      role="button"
                      tabIndex={0}
                      onClick={() => void toggleSaved(openedIndex, rowIndex)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          void toggleSaved(openedIndex, rowIndex)
                        }
                      }}
                      aria-label={
                        done ? `${badges[rowIndex]}세트 기록 취소` : `${badges[rowIndex]}세트 완료`
                      }
                    >
                      <div className={styles.setRow}>
                        <span
                          className={[
                            styles.check,
                            done ? (row.isWarmup ? styles.checkOnWarmup : styles.checkOn) : '',
                          ].join(' ')}
                          aria-hidden="true"
                        >
                          {done && (
                            <svg width="15" height="11" viewBox="0 0 16 12">
                              <path
                                d="M1 6l4.5 4.5L15 1"
                                stroke={row.isWarmup ? 'var(--warn)' : 'var(--on-fill)'}
                                strokeWidth="2.5"
                                fill="none"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          )}
                        </span>

                        <span
                          className={[
                            styles.numPill,
                            done ? styles.numPillDone : '',
                            row.isWarmup ? styles.numPillWarmup : '',
                          ].join(' ')}
                        >
                          {badges[rowIndex]}
                        </span>

                        <span className={styles.values}>
                          {fields.map((field, fieldIndex) => (
                            <span key={field} className={fieldIndex === 0 ? styles.colA : styles.colB}>
                              <span
                                className={[
                                  styles.bigVal,
                                  done ? styles.bigValDone : '',
                                  row[field] == null ? styles.bigValEmpty : '',
                                ].join(' ')}
                              >
                                {row[field] == null
                                  ? '–'
                                  : `${
                                      field === 'weightKg' &&
                                      opened.measureType === 'WEIGHTED_BODYWEIGHT'
                                        ? '+'
                                        : ''
                                    }${trimNumber(row[field] as number)}`}
                              </span>
                              <span className={styles.unit}>{FIELD_UNIT[field]}</span>
                            </span>
                          ))}
                        </span>

                        <span
                          className={styles.rowActions}
                          role="presentation"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <button
                            type="button"
                            className={styles.iconBtn}
                            onClick={() => void removeRow(openedIndex, rowIndex)}
                            aria-label="세트 삭제"
                          >
                            ✕
                          </button>
                          <button
                            type="button"
                            className={`${styles.chevron} ${expanded ? styles.chevronOpen : ''}`}
                            onClick={() => setExpandedId(expanded ? null : row.clientSetId)}
                            aria-label={expanded ? '값 접기' : '값 고치기'}
                          >
                            <svg width="17" height="10" viewBox="0 0 18 11">
                              <path
                                d="M1.5 1.5L9 9l7.5-7.5"
                                stroke="currentColor"
                                strokeWidth="2"
                                fill="none"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          </button>
                        </span>
                      </div>

                      <div className={styles.subLine}>
                        <span>휴식 {formatRest(row.restSec)}</span>
                        {row.isWarmup && <span className={styles.subWarmup}>워밍업 · 분석 제외</span>}
                        {row.fromPrev && !done && <span>이전 기록에서 채움</span>}
                      </div>
                    </div>

                    {expanded && (
                      <div className={styles.panel}>
                        <div className={styles.fieldRow}>
                          <span className={styles.fieldLabel}>{FIELD_LABEL[primaryField]}</span>
                          <button
                            type="button"
                            className={styles.fieldValue}
                            onClick={() =>
                              setEditing({ cardIndex: openedIndex, rowIndex, field: primaryField })
                            }
                          >
                            {row[primaryField] == null
                              ? '–'
                              : trimNumber(row[primaryField] as number)}
                          </button>
                          <span className={styles.fieldUnit}>{FIELD_UNIT[primaryField]}</span>
                          <button
                            type="button"
                            className={styles.pillBtn}
                            onClick={() =>
                              void patchValue(
                                openedIndex,
                                rowIndex,
                                primaryField,
                                Math.max(0, (row[primaryField] ?? 0) - step),
                              )
                            }
                          >
                            −{trimNumber(step)}
                          </button>
                          <button
                            type="button"
                            className={styles.pillBtn}
                            onClick={() =>
                              void patchValue(
                                openedIndex,
                                rowIndex,
                                primaryField,
                                (row[primaryField] ?? 0) + step,
                              )
                            }
                          >
                            +{trimNumber(step)}
                          </button>
                        </div>

                        {hasReps && (
                          <div className={styles.fieldRow}>
                            <span className={styles.fieldLabel}>횟수</span>
                            <button
                              type="button"
                              className={styles.fieldValue}
                              onClick={() =>
                                setEditing({ cardIndex: openedIndex, rowIndex, field: 'reps' })
                              }
                            >
                              {row.reps == null ? '–' : row.reps}
                            </button>
                            <span className={styles.fieldUnit}>회</span>
                            <button
                              type="button"
                              className={styles.pillBtn}
                              onClick={() =>
                                void patchValue(
                                  openedIndex,
                                  rowIndex,
                                  'reps',
                                  Math.max(0, (row.reps ?? 0) - 1),
                                )
                              }
                            >
                              −1
                            </button>
                            <button
                              type="button"
                              className={styles.pillBtn}
                              onClick={() =>
                                void patchValue(openedIndex, rowIndex, 'reps', (row.reps ?? 0) + 1)
                              }
                            >
                              +1
                            </button>
                          </div>
                        )}

                        <div className={styles.fieldRow}>
                          <span className={styles.fieldLabel}>휴식</span>
                          <span className={styles.restValue}>{formatRest(row.restSec)}</span>
                          <span className={styles.fieldUnit} />
                          <button
                            type="button"
                            className={styles.pillBtn}
                            onClick={() =>
                              updateRow(openedIndex, rowIndex, {
                                restSec: Math.max(0, row.restSec - STEP_REST),
                              })
                            }
                          >
                            −30
                          </button>
                          <button
                            type="button"
                            className={styles.pillBtn}
                            onClick={() =>
                              updateRow(openedIndex, rowIndex, { restSec: row.restSec + STEP_REST })
                            }
                          >
                            +30
                          </button>
                        </div>

                        <div className={styles.fieldRow}>
                          <span className={styles.fieldLabel}>분석</span>
                          <span className={styles.warmupHint}>
                            {row.isWarmup ? '볼륨 집계에서 제외됨' : '볼륨 집계에 포함'}
                          </span>
                          <button
                            type="button"
                            className={`${styles.warmupPill} ${
                              row.isWarmup ? styles.warmupPillOn : ''
                            }`}
                            onClick={() => void toggleWarmup(openedIndex, rowIndex)}
                          >
                            {row.isWarmup ? '워밍업 해제' : '워밍업 설정'}
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>

            <div className={styles.focusActions}>
              <button type="button" className={styles.addSet} onClick={() => addRow(openedIndex)}>
                + 세트 추가
              </button>
              <button
                type="button"
                className={styles.batchButton}
                onClick={() => {
                  const base = opened.rows[0]
                  setBatchPrimary(base?.[primaryField] ?? 0)
                  setBatchReps(base?.reps ?? 0)
                  setBatchPicked({})
                  setBatchOpen(true)
                }}
              >
                일괄 수정
              </button>
            </div>
          </div>

          {restOverlay}
        </div>

        <NumberPad
          open={editing !== null}
          onClose={() => setEditing(null)}
          label={editing ? `${opened.name} · ${FIELD_LABEL[editing.field]}` : undefined}
          initialValue={editing ? (opened.rows[editing.rowIndex][editing.field] ?? 0) : 0}
          unit={editing ? FIELD_UNIT[editing.field] : undefined}
          allowDecimal={editing?.field === 'weightKg'}
          onApply={(value) => {
            if (!editing) return
            void patchValue(editing.cardIndex, editing.rowIndex, editing.field, value)
            setEditing(null)
          }}
        />

        {/*
          일괄 수정. 같은 무게로 다섯 세트를 채우는 일이 흔해서, 한 줄씩 고치는
          것보다 고칠 줄을 먼저 정하고 값을 한 번에 넣는 쪽이 빠르다.
        */}
        <BottomSheet
          open={batchOpen}
          onClose={() => setBatchOpen(false)}
          title={<span className={styles.sheetTitle}>일괄 수정</span>}
        >
          <div className={styles.batchList}>
            {opened.rows.map((row, rowIndex) => (
              <button
                key={row.clientSetId}
                type="button"
                className={styles.batchRow}
                onClick={() =>
                  setBatchPicked((prev) => ({
                    ...prev,
                    [row.clientSetId]: !prev[row.clientSetId],
                  }))
                }
              >
                <span
                  className={`${styles.batchCheck} ${
                    batchPicked[row.clientSetId] ? styles.batchCheckOn : ''
                  }`}
                  aria-hidden="true"
                >
                  {batchPicked[row.clientSetId] ? '✓' : ''}
                </span>
                <span className={styles.batchLabel}>
                  {badges[rowIndex]} · {describeSet(opened.measureType, row)}
                </span>
              </button>
            ))}
          </div>

          <div className={styles.panel}>
            <div className={styles.fieldRow}>
              <span className={styles.fieldLabel}>{FIELD_LABEL[primaryField]}</span>
              <span className={styles.restValue}>{trimNumber(batchPrimary)}</span>
              <span className={styles.fieldUnit}>{FIELD_UNIT[primaryField]}</span>
              <button
                type="button"
                className={styles.pillBtn}
                onClick={() => setBatchPrimary(Math.max(0, batchPrimary - step))}
              >
                −{trimNumber(step)}
              </button>
              <button
                type="button"
                className={styles.pillBtn}
                onClick={() => setBatchPrimary(batchPrimary + step)}
              >
                +{trimNumber(step)}
              </button>
            </div>

            {hasReps && (
              <div className={styles.fieldRow}>
                <span className={styles.fieldLabel}>횟수</span>
                <span className={styles.restValue}>{batchReps}</span>
                <span className={styles.fieldUnit}>회</span>
                <button
                  type="button"
                  className={styles.pillBtn}
                  onClick={() => setBatchReps(Math.max(0, batchReps - 1))}
                >
                  −1
                </button>
                <button
                  type="button"
                  className={styles.pillBtn}
                  onClick={() => setBatchReps(batchReps + 1)}
                >
                  +1
                </button>
              </div>
            )}
          </div>

          <button
            type="button"
            className={styles.batchApply}
            disabled={!opened.rows.some((row) => batchPicked[row.clientSetId])}
            onClick={() => {
              opened.rows.forEach((row, rowIndex) => {
                if (!batchPicked[row.clientSetId]) return
                void patchValue(openedIndex, rowIndex, primaryField, batchPrimary)
                if (hasReps) void patchValue(openedIndex, rowIndex, 'reps', batchReps)
              })
              setBatchOpen(false)
            }}
          >
            고른 세트에 적용
          </button>
        </BottomSheet>

        {infoSheet}
      </>
    )
  }

  // ── 1단계: 그날 무슨 운동을 했는지. 세트 값은 종목을 눌러야 보인다.
  return (
    <>
      <div className={styles.page}>
        <div className={styles.header}>
          <button
            type="button"
            className={styles.headerBack}
            onClick={() => navigate(-1)}
            aria-label="뒤로"
          >
            ←
          </button>

          {showDayNav ? (
            <>
              <div className={styles.headerNav}>
                <button
                  type="button"
                  className={styles.navArrow}
                  onClick={() => void goToDay(-1)}
                  aria-label="하루 전"
                >
                  ‹
                </button>
                <span className={styles.dayTitle}>{formatDayLabel(dayKey)}</span>
                <button
                  type="button"
                  className={styles.navArrow}
                  onClick={() => void goToDay(1)}
                  aria-label="하루 뒤"
                >
                  ›
                </button>
              </div>
              {/* 사후 입력은 아직 끝내지 않은 기록이라 마무리 버튼이 필요하다 */}
              {backfillDate && !viewingPast ? (
                <button
                  type="button"
                  className={styles.endButtonSmall}
                  disabled={savedCount === 0 || busy}
                  onClick={() => void finish()}
                >
                  완료
                </button>
              ) : (
                <div className={styles.headerSpacer} />
              )}
            </>
          ) : (
            <>
              <div className={styles.liveCenter}>
                <div className={styles.liveDate}>{formatDayLabel(dayKey)}</div>
                <div className={styles.liveElapsed}>
                  {savedCount > 0
                    ? `${formatElapsed(session?.startedAt ?? null, now)} · ${savedCount}세트`
                    : '완료 체크한 세트만 저장돼요'}
                </div>
              </div>
              <button
                type="button"
                className={styles.endButton}
                disabled={savedCount === 0 || busy}
                onClick={() => void finish()}
              >
                운동 종료
              </button>
            </>
          )}
        </div>

        {cards.length === 0 ? (
          <div className={styles.emptyBox}>
            <div>
              <div className={styles.emptyTitle}>기록된 운동이 없어요</div>
              <div className={styles.emptyNote}>종목을 추가하면 세트를 기록할 수 있어요</div>
            </div>
            <button type="button" className={styles.emptyAdd} onClick={() => setPickerOpen(true)}>
              + 종목 추가
            </button>
          </div>
        ) : (
          <div className={`${styles.listScroll} ${restShowing ? styles.scrollWithRest : ''}`}>
            {cards.map((card, cardIndex) => {
              const saved = card.rows.filter((row) => row.savedId !== null)
              const planned = card.rows.length - saved.length
              const summary =
                saved.length === 0
                  ? `예정 ${planned}세트 · 아직 기록 없음`
                  : `${saved.length}세트 기록${planned > 0 ? ` · ${planned}세트 예정` : ''} · 최근 ${describeSet(
                      card.measureType,
                      saved[saved.length - 1],
                    )}`
              return (
                <div key={`${card.exerciseId}-${cardIndex}`} className={styles.exRow}>
                  {/* 카드 전체가 세부 기록으로 가는 문이라 따로 화살표를 두지 않는다 */}
                  <button
                    type="button"
                    className={styles.exCard}
                    onClick={() => openCard(card.exerciseId)}
                  >
                    <span className={styles.exCardHead}>
                      <span className={styles.exName}>{card.name}</span>
                      <span className={styles.typeTag}>
                        {card.measureType === 'BODYWEIGHT_REPS'
                          ? '체중'
                          : card.measureType === 'TIME'
                            ? '시간'
                            : '웨이트'}
                      </span>
                    </span>
                    <span className={styles.exSummary}>{summary}</span>
                  </button>
                  <button
                    type="button"
                    className={styles.exDelete}
                    onClick={() => askRemoveCard(cardIndex)}
                    aria-label={`${card.name} 빼기`}
                  >
                    ✕
                  </button>
                </div>
              )
            })}

            <button type="button" className={styles.addExercise} onClick={() => setPickerOpen(true)}>
              + 종목 추가
            </button>
          </div>
        )}

        {restOverlay}
      </div>

      <BottomSheet
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        title={<span className={styles.sheetTitle}>종목 추가</span>}
      >
        <div className={styles.pickerBox}>
          <ExercisePicker step={pickerStep} onStepChange={setPickerStep} onPick={handlePick} />
        </div>
      </BottomSheet>

      {/* 기록이 있는 종목을 뺄 때만 열린다. 세트까지 함께 지워지는 일이라 한 번 묻는다 */}
      <BottomSheet
        open={removeTarget !== null}
        onClose={() => setRemoveTarget(null)}
        title={<span className={styles.sheetTitle}>종목 빼기</span>}
      >
        {removeTarget !== null && cards[removeTarget] && (
          <>
            <p className={styles.confirmText}>
              <b>{cards[removeTarget].name}</b>에 기록한{' '}
              {cards[removeTarget].rows.filter((row) => row.savedId !== null).length}세트가 함께
              지워집니다. 되돌릴 수 없어요.
            </p>
            <button
              type="button"
              className={styles.confirmDanger}
              onClick={() => void removeCard(removeTarget)}
            >
              빼기
            </button>
          </>
        )}
      </BottomSheet>

      {infoSheet}
    </>
  )
}
