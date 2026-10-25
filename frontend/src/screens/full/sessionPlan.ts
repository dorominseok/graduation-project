import { exerciseApi, isApiError } from '../../api'
import type { LastPerformance, MeasureType, Routine, RoutineItem, WorkoutSession } from '../../api'
import { activeItems, repRangeLabel } from '../routine/format'

/**
 * 기록 화면의 카드 만들기 — 체크한 세트 + 오늘 루틴의 남은 계획 + 브라우저 임시 저장.
 *
 * <p>계획 줄은 서버에 저장하지 않는다. 루틴 계획은 `routine_items`에 있으니 열 때마다 "계획 − 체크한
 * 세트"로 다시 만들고, 루틴 밖에서 추가한 종목과 체크 전에 고친 값만 브라우저에 그날까지 둔다.
 * 계획이 `workout_sets`에 섞이지 않아야 분석이 하지 않은 운동을 세지 않는다.
 */

/** 화면에만 있는 세트 행. 완료 체크를 해야 서버로 간다. */
export interface SetRow {
  /** 멱등 키이자 React key. 완료 체크 시점이 아니라 행을 만들 때 미리 만든다 */
  clientSetId: string
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  isWarmup: boolean
  /**
   * 세트 사이 쉬는 시간. 서버에는 보내지 않는다 — 기록이 아니라 운동 중에만
   * 쓰는 타이머 값이고, workout_sets에 그 칸이 없다.
   */
  restSec: number
  /** 저장된 세트의 서버 id. null이면 아직 계획 행이다 */
  savedId: number | null
}

export interface ExerciseCard {
  exerciseId: number
  name: string
  measureType: MeasureType
  rows: SetRow[]
  /** 오늘 루틴의 종목이면 "4세트 × 8~12회". 루틴 밖 종목은 null */
  routineTarget: string | null
}

export const DEFAULT_REST_SEC = 90

export function emptyRow(): SetRow {
  return {
    clientSetId: crypto.randomUUID(),
    weightKg: null,
    reps: null,
    durationSec: null,
    isWarmup: false,
    restSec: DEFAULT_REST_SEC,
    savedId: null,
  }
}

/** 직전 기록의 세트로 계획 행을 만든다. 저장 전이라 id는 새로 만든다 */
export function rowsFromLast(sets: LastPerformance['sets']): SetRow[] {
  return sets.map((set) => ({
    clientSetId: crypto.randomUUID(),
    weightKg: set.weightKg,
    reps: set.reps,
    durationSec: set.durationSec,
    isWarmup: set.isWarmup,
    restSec: DEFAULT_REST_SEC,
    savedId: null,
  }))
}

/** 직전 기록. 프리필은 편의 기능이라 못 읽어도 기록은 계속할 수 있어야 한다 */
export async function lastPerformanceOf(exerciseId: number): Promise<LastPerformance | null> {
  try {
    return await exerciseApi.getLastPerformance(exerciseId)
  } catch (err) {
    if (!isApiError(err)) throw err
    return null
  }
}

/** 그날 기록의 저장된 세트를 카드로 */
export function savedCards(session: WorkoutSession): ExerciseCard[] {
  return session.exercises.map((group) => ({
    exerciseId: group.exerciseId,
    name: group.exerciseName,
    measureType: group.measureType,
    routineTarget: null,
    rows: group.sets.map((set) => ({
      clientSetId: set.clientSetId,
      weightKg: set.weightKg,
      reps: set.reps,
      durationSec: set.durationSec,
      isWarmup: set.isWarmup,
      restSec: DEFAULT_REST_SEC,
      savedId: set.id,
    })),
  }))
}

function routineTargetOf(item: RoutineItem): string {
  return `${item.plannedSets}세트 × ${repRangeLabel(item)}`
}

/**
 * 루틴의 모양. 후보정으로 종목·세트가 바뀌면 달라진다 — 그때는 임시 저장의 루틴 카드를 버리고
 * 계획을 새로 만든다. 그대로면 임시 저장(고친 중량, 뺀 카드, 순서)을 그대로 살린다.
 */
export function routineKeyOf(routine: Routine | null): string | null {
  if (!routine) return null
  return `${routine.id}/${activeItems(routine)
    .map((item) => `${item.id}:${item.exerciseId}:${item.plannedSets}`)
    .join(',')}`
}

/**
 * 루틴 항목 하나를 카드로. 이미 체크한 세트는 그대로 두고, 덜 한 만큼 계획 행을 붙인다.
 * 해 본 종목은 지난 본세트 값으로 채우고(세트가 더 필요하면 마지막 세트를 이어서), 처음인 종목은
 * 빈 칸이다.
 */
async function routineCard(item: RoutineItem, saved: ExerciseCard | undefined): Promise<ExerciseCard> {
  const savedRows = saved?.rows ?? []
  const remaining = Math.max(0, item.plannedSets - savedRows.filter((row) => !row.isWarmup).length)
  const last = item.firstTime ? null : await lastPerformanceOf(item.exerciseId)
  const working = last?.sets.filter((set) => !set.isWarmup) ?? []

  const planRows: SetRow[] = []
  for (let i = 0; i < remaining; i++) {
    const source = working[Math.min(i, working.length - 1)]
    planRows.push(source ? rowsFromLast([{ ...source, isWarmup: false }])[0] : emptyRow())
  }

  return {
    exerciseId: item.exerciseId,
    name: item.exerciseName,
    measureType: item.measureType,
    routineTarget: routineTargetOf(item),
    rows: [...savedRows, ...planRows],
  }
}

// ── 브라우저 임시 저장

interface Draft {
  /** 저장한 날. 자정이 지나면 버린다 — 임시 저장은 그날만이다 */
  savedOn: string
  routineKey: string | null
  /** 카드 순서와 체크하지 않은 행. 저장된 행은 서버가 진실이라 넣지 않는다 */
  cards: ExerciseCard[]
}

const DRAFT_PREFIX = 'session-draft'

function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

function draftKey(userId: number, performedOn: string): string {
  return `${DRAFT_PREFIX}:${userId}:${performedOn}`
}

/** 그날 임시 저장. 없거나, 어제 저장한 것이거나, 읽을 수 없으면 null */
export function loadDraft(userId: number, performedOn: string): Draft | null {
  try {
    const raw = localStorage.getItem(draftKey(userId, performedOn))
    if (!raw) return null
    const draft = JSON.parse(raw) as Draft
    return draft.savedOn === toDateString(new Date()) && Array.isArray(draft.cards) ? draft : null
  } catch {
    return null
  }
}

/** 체크하지 않은 행만 남긴다. 오늘 아닌 날 저장한 다른 임시 저장은 이참에 지운다 */
export function saveDraft(userId: number, performedOn: string, routineKey: string | null, cards: ExerciseCard[]) {
  const today = toDateString(new Date())
  const draft: Draft = {
    savedOn: today,
    routineKey,
    cards: cards.map((card) => ({ ...card, rows: card.rows.filter((row) => row.savedId === null) })),
  }
  try {
    localStorage.setItem(draftKey(userId, performedOn), JSON.stringify(draft))
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i)
      if (!key?.startsWith(`${DRAFT_PREFIX}:`)) continue
      const stale = JSON.parse(localStorage.getItem(key) ?? '{}') as Partial<Draft>
      if (stale.savedOn !== today) localStorage.removeItem(key)
    }
  } catch {
    // 저장하지 못해도 기록은 계속된다. 화면을 떠나면 체크하지 않은 행만 사라진다
  }
}

/**
 * 카드를 만든다.
 *
 * <ol>
 *   <li>임시 저장이 있고 루틴이 그대로면 — 임시 저장의 순서·행에 저장된 세트를 끼운다. "이 루틴으로 기록"을
 *       눌러 들어왔으면({@code restoreRoutine}) 기록 화면에서 뺀 루틴 종목을 뒤에 다시 붙인다.</li>
 *   <li>아니면 — 루틴 종목(루틴 순서) → 루틴 밖에서 체크한 종목(한 순서) → 임시 저장의 루틴 밖 종목.</li>
 * </ol>
 *
 * 어느 쪽이든 서버에 저장된 세트는 빠짐없이 들어간다. 다른 기기에서 체크한 세트도 보여야 한다.
 */
export async function buildCards(
  session: WorkoutSession,
  routine: Routine | null,
  draft: Draft | null,
  restoreRoutine = false,
): Promise<ExerciseCard[]> {
  const saved = new Map(savedCards(session).map((card) => [card.exerciseId, card]))
  const take = (exerciseId: number) => {
    const card = saved.get(exerciseId)
    saved.delete(exerciseId)
    return card
  }

  if (draft && draft.routineKey === routineKeyOf(routine)) {
    const cards = draft.cards.map((card) => ({ ...card, rows: [...(take(card.exerciseId)?.rows ?? []), ...card.rows] }))
    const shown = new Set(cards.map((card) => card.exerciseId))
    const restored =
      restoreRoutine && routine
        ? await Promise.all(
            activeItems(routine)
              .filter((item) => !shown.has(item.exerciseId))
              .map((item) => routineCard(item, take(item.exerciseId))),
          )
        : []
    return [...cards, ...restored, ...saved.values()]
  }

  const routineCards = routine
    ? await Promise.all(activeItems(routine).map((item) => routineCard(item, take(item.exerciseId))))
    : []
  const cards = [...routineCards, ...saved.values()]

  // 루틴이 바뀌어 다시 만들었어도, 루틴 밖에서 추가해 둔 종목은 살린다
  for (const extra of draft?.cards.filter((card) => card.routineTarget === null) ?? []) {
    const existing = cards.find((card) => card.exerciseId === extra.exerciseId)
    if (existing) existing.rows = [...existing.rows, ...extra.rows]
    else cards.push(extra)
  }
  return cards
}
