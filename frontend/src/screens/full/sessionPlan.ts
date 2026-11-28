import { exerciseApi, isApiError } from '../../api'
import type { CardRef, LastPerformance, MeasureType, Routine, RoutineItem, WorkoutSession } from '../../api'
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
  /** 실패 세트의 목표 횟수(결정 기록 D-15). 있으면 실패 세트 — reps는 해낸 횟수이고 목표보다 적다 */
  targetReps: number | null
  /**
   * 세트 사이 쉬는 시간. 서버에는 보내지 않는다 — 기록이 아니라 운동 중에만
   * 쓰는 타이머 값이고, workout_sets에 그 칸이 없다.
   */
  restSec: number
  /** 저장된 세트의 서버 id. null이면 아직 계획 행이다 */
  savedId: number | null
}

/**
 * 카드 하나 — (종목, 회차). 하루 기록 안을 회차로 나누면(결정 기록 D-22) 같은 종목도 회차가 다르면 카드가 따로다.
 */
export interface ExerciseCard {
  exerciseId: number
  /** 하루 기록 안의 회차. 나누지 않았으면 1, 0은 GTG */
  round: number
  name: string
  measureType: MeasureType
  rows: SetRow[]
  /** 오늘 루틴의 종목이면 "4세트 × 8~12회". 루틴 밖 종목은 null */
  routineTarget: string | null
}

export const DEFAULT_REST_SEC = 90

/** 하루에 나눌 수 있는 회차 수. 서버(V23)와 같다 */
export const MAX_ROUND = 20

/**
 * 그날의 GTG(틈틈이 하는 가벼운 세트) 회차(서버 V24, 결정 기록 D-23). 하루 하나이고 탭은 맨 뒤다. 운동 시간·분석·직전 기록에
 * 넣지 않는다
 */
export const GTG_ROUND = 0

/** 회차를 늘어놓는 순위 — 일반 회차는 번호 순, GTG는 맨 뒤 */
export function roundRank(round: number): number {
  return round === GTG_ROUND ? Number.MAX_SAFE_INTEGER : round
}

/** 탭·머리에 쓰는 회차 이름 */
export function roundLabel(round: number): string {
  return round === GTG_ROUND ? 'GTG' : `${round}회차`
}

/** 카드를 가리키는 열쇠 */
export function cardKey(card: { exerciseId: number; round: number }): string {
  return `${card.exerciseId}:${card.round}`
}

/** 회차 순으로 세운다(GTG는 맨 뒤). 회차 안의 순서는 그대로다(안정 정렬) */
export function byRound<T extends { round: number }>(cards: T[]): T[] {
  return [...cards].sort((a, b) => roundRank(a.round) - roundRank(b.round))
}

export function emptyRow(): SetRow {
  return {
    clientSetId: crypto.randomUUID(),
    weightKg: null,
    reps: null,
    durationSec: null,
    isWarmup: false,
    targetReps: null,
    restSec: DEFAULT_REST_SEC,
    savedId: null,
  }
}

/**
 * 직전 기록의 세트로 계획 행을 만든다. 저장 전이라 id는 새로 만든다.
 * 지난번 실패 세트는 목표 횟수의 메인 세트로 채운다 — 다시 도전하는 자리다(결정 기록 D-15).
 */
export function rowsFromLast(sets: LastPerformance['sets']): SetRow[] {
  return sets.map((set) => ({
    clientSetId: crypto.randomUUID(),
    weightKg: set.weightKg,
    reps: set.targetReps ?? set.reps,
    durationSec: set.durationSec,
    isWarmup: set.isWarmup,
    targetReps: null,
    restSec: DEFAULT_REST_SEC,
    savedId: null,
  }))
}

/** 직전 기록(GTG 카드면 지난 GTG). 프리필은 편의 기능이라 못 읽어도 기록은 계속할 수 있어야 한다 */
export async function lastPerformanceOf(exerciseId: number, gtg = false): Promise<LastPerformance | null> {
  try {
    return await exerciseApi.getLastPerformance(exerciseId, gtg)
  } catch (err) {
    if (!isApiError(err)) throw err
    return null
  }
}

/** 그날 기록의 저장된 세트를 카드로 */
export function savedCards(session: WorkoutSession): ExerciseCard[] {
  return session.exercises.map((group) => ({
    exerciseId: group.exerciseId,
    round: group.round ?? 1,
    name: group.exerciseName,
    measureType: group.measureType,
    routineTarget: null,
    rows: group.sets.map((set) => ({
      clientSetId: set.clientSetId,
      weightKg: set.weightKg,
      reps: set.reps,
      durationSec: set.durationSec,
      isWarmup: set.isWarmup,
      targetReps: set.targetReps,
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

  // 오늘의 루틴은 1회차다
  return {
    exerciseId: item.exerciseId,
    round: 1,
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
    if (draft.savedOn !== toDateString(new Date()) || !Array.isArray(draft.cards)) return null
    // 실패 세트(2026-11-28) 전에 남긴 임시 저장에는 목표 칸이, 회차(D-22) 전에 남긴 것에는 회차가 없다
    return {
      ...draft,
      cards: draft.cards.map((card) => ({
        ...card,
        round: card.round ?? 1,
        rows: card.rows.map((row) => ({ ...row, targetReps: row.targetReps ?? null })),
      })),
    }
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
 * 사용자가 끌어서 정한 순서(서버의 `cardOrder`)대로 놓는다. 거기 없는 카드는 뒤에 지금 순서대로.
 * 임시 저장이 있으면 그 순서가 이미 이 기기에서 정한 것이라 쓰지 않는다 — 다른 기기에서 열거나 다음 날 지난 기록을
 * 열 때 쓴다(임시 저장은 그날 하루만이다)
 */
function byCardOrder(cards: ExerciseCard[], order: CardRef[] | undefined): ExerciseCard[] {
  if (!order || order.length === 0) return cards
  const rank = new Map<string, number>()
  order.forEach((card, index) => {
    if (!rank.has(cardKey(card))) rank.set(cardKey(card), index)
  })
  // 정렬은 안정적이라 순서에 없는 카드끼리는 지금 순서가 지켜진다
  const rankOf = (card: ExerciseCard) => rank.get(cardKey(card)) ?? Number.MAX_SAFE_INTEGER
  return [...cards].sort((a, b) => rankOf(a) - rankOf(b))
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
 * 카드는 (종목, 회차)로 맞추고, 마지막에 회차 순으로 세운다.
 */
export async function buildCards(
  session: WorkoutSession,
  routine: Routine | null,
  draft: Draft | null,
  restoreRoutine = false,
): Promise<ExerciseCard[]> {
  const saved = new Map(savedCards(session).map((card) => [cardKey(card), card]))
  // 저장 응답이 오기 전에 화면을 떠나면 그 줄이 체크 전 상태로 임시 저장에 남는다. 서버에 있는 줄은
  // 빼서 같은 세트가 두 번 나오지 않게 한다
  const savedIds = new Set(session.exercises.flatMap((group) => group.sets.map((set) => set.clientSetId)))
  const unsaved = (rows: SetRow[]) => rows.filter((row) => !savedIds.has(row.clientSetId))
  const take = (key: string) => {
    const card = saved.get(key)
    saved.delete(key)
    return card
  }

  if (draft && draft.routineKey === routineKeyOf(routine)) {
    const cards = draft.cards.map((card) => ({
      ...card,
      rows: [...(take(cardKey(card))?.rows ?? []), ...unsaved(card.rows)],
    }))
    const shown = new Set(cards.map(cardKey))
    const restored =
      restoreRoutine && routine
        ? await Promise.all(
            activeItems(routine)
              .filter((item) => !shown.has(cardKey({ exerciseId: item.exerciseId, round: 1 })))
              .map((item) => routineCard(item, take(cardKey({ exerciseId: item.exerciseId, round: 1 })))),
          )
        : []
    return byRound([...cards, ...restored, ...saved.values()])
  }

  const routineCards = routine
    ? await Promise.all(
        activeItems(routine).map((item) => routineCard(item, take(cardKey({ exerciseId: item.exerciseId, round: 1 })))),
      )
    : []
  const cards = byCardOrder([...routineCards, ...saved.values()], session.cardOrder)

  // 루틴이 바뀌어 다시 만들었어도, 루틴 밖에서 추가해 둔 종목은 살린다
  for (const extra of draft?.cards.filter((card) => card.routineTarget === null) ?? []) {
    const existing = cards.find((card) => cardKey(card) === cardKey(extra))
    if (existing) existing.rows = [...existing.rows, ...unsaved(extra.rows)]
    else cards.push({ ...extra, rows: unsaved(extra.rows) })
  }
  return byRound(cards)
}
