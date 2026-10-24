import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { BottomSheet, ScreenHeader, Stepper, useToast } from '../../components'
import { exerciseApi, isApiError, routineApi } from '../../api'
import type { Routine, RoutineAlternative, RoutineItem } from '../../api'
import { paths } from '../../app/paths'
import { EQUIPMENT_LABEL } from '../full/exerciseLabels'
import { activeItems, basisLine, formatRoutineDay, lastPerformanceLine, repRangeLabel } from './format'
import styles from './routine.module.css'

/** 세트 조정 범위. 서버가 같은 범위를 검증한다 */
const MIN_SETS = 1
const MAX_SETS = 10

function toDateString(date: Date): string {
  return date.toLocaleDateString('sv-SE')
}

/**
 * 루틴.
 *
 * <p>맨 위가 조합 설명이고, 항목마다 근거를 숫자로 적는다. 숫자는 규칙이 쓰고 설명은 종목·순서의
 * 이유만 말한다 — 섞이면 설명이 숫자를 틀리게 말할 수 있다.
 *
 * <p>다시 만들기는 없고 여기서 고친다 — 교체 · 세트 · 빼기. 지난 루틴은 읽기만 된다. 고치면
 * 이미 쌓인 수행률이 바뀐다.
 */
export function RoutineScreen() {
  const navigate = useNavigate()
  const { routineId: routineIdParam } = useParams()
  const routineId = Number(routineIdParam)
  const { showToast } = useToast()

  const [routine, setRoutine] = useState<Routine | null>(null)
  const [missing, setMissing] = useState(false)
  /** 종목별 지난 기록 한 줄. 처음 하는 종목은 없다 */
  const [lastLines, setLastLines] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState(false)

  const [swapItem, setSwapItem] = useState<RoutineItem | null>(null)
  const [alternatives, setAlternatives] = useState<RoutineAlternative[] | null>(null)
  const [removeItem, setRemoveItem] = useState<RoutineItem | null>(null)

  // 세트를 연달아 누르면 요청이 겹친다. 마지막 요청의 응답만 화면에 쓴다
  const setsSeq = useRef(0)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  useEffect(() => {
    let alive = true
    routineApi
      .get(routineId)
      .then((result) => {
        if (alive) setRoutine(result)
      })
      .catch((err: unknown) => {
        if (!alive) return
        if (isApiError(err) && err.status === 404) setMissing(true)
        else fail(err)
      })
    return () => {
      alive = false
    }
  }, [routineId, fail])

  // 해 본 종목만 지난 기록을 읽는다. 처음인 종목은 그 자리에 "동작 보기"가 온다
  const knownIds = routine
    ? activeItems(routine)
        .filter((item) => !item.firstTime)
        .map((item) => item.exerciseId)
        .join(',')
    : ''
  useEffect(() => {
    if (!knownIds) return
    let alive = true
    const ids = knownIds.split(',').map(Number)
    void Promise.all(
      ids.map(async (id) => {
        try {
          const last = await exerciseApi.getLastPerformance(id)
          return [id, last ? lastPerformanceLine(last) : null] as const
        } catch {
          // 지난 기록은 보조 정보다. 못 읽어도 루틴은 볼 수 있다
          return [id, null] as const
        }
      }),
    ).then((pairs) => {
      if (!alive) return
      setLastLines((prev) => {
        const next = { ...prev }
        for (const [id, line] of pairs) if (line) next[id] = line
        return next
      })
    })
    return () => {
      alive = false
    }
  }, [knownIds])

  if (missing) {
    return (
      <>
        <ScreenHeader title="루틴" />
        <div className={styles.empty}>루틴을 찾을 수 없어요</div>
      </>
    )
  }

  if (!routine) {
    return (
      <>
        <ScreenHeader title="루틴" />
        <div className={styles.empty}>불러오는 중…</div>
      </>
    )
  }

  const editable = routine.routineDate === toDateString(new Date())
  const active = activeItems(routine)
  const removed = routine.items.filter((item) => item.removed)

  const openSwap = async (item: RoutineItem) => {
    setSwapItem(item)
    setAlternatives(null)
    try {
      setAlternatives(await routineApi.getAlternatives(routine.id, item.id))
    } catch (err) {
      setSwapItem(null)
      fail(err)
    }
  }

  const swap = async (alternative: RoutineAlternative) => {
    if (!swapItem || busy) return
    setBusy(true)
    try {
      setRoutine(await routineApi.updateItem(routine.id, swapItem.id, { exerciseId: alternative.exerciseId }))
      setSwapItem(null)
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const changeSets = async (item: RoutineItem, plannedSets: number) => {
    const seq = ++setsSeq.current
    // 누르는 대로 바로 움직인다. 서버 응답은 마지막 것만 덮어쓴다
    setRoutine((prev) =>
      prev && {
        ...prev,
        items: prev.items.map((it) => (it.id === item.id ? { ...it, plannedSets, adjusted: true } : it)),
      },
    )
    try {
      const updated = await routineApi.updateItem(routine.id, item.id, { plannedSets })
      if (seq === setsSeq.current) setRoutine(updated)
    } catch (err) {
      if (seq === setsSeq.current) {
        setRoutine(await routineApi.get(routine.id).catch(() => routine))
      }
      fail(err)
    }
  }

  const remove = async () => {
    if (!removeItem || busy) return
    setBusy(true)
    try {
      setRoutine(await routineApi.removeItem(routine.id, removeItem.id))
      setRemoveItem(null)
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const otherEquipment = alternatives?.filter((a) => !a.sameEquipment) ?? []
  const sameEquipment = alternatives?.filter((a) => a.sameEquipment) ?? []

  return (
    <>
      <ScreenHeader title={editable ? '오늘 루틴' : `${formatRoutineDay(routine.routineDate)} 루틴`} />

      <div className={styles.screen}>
        <div className={styles.page}>
          <div className={styles.comment}>
            <div className={styles.commentText}>{routine.comment}</div>
            {active.length > 0 && (
              <div className={styles.commentMeta}>
                {active.length}종목 · {routine.totalSets}세트
                {routine.doneSets > 0 && ` · ${routine.doneSets}세트 했어요`}
              </div>
            )}
          </div>

          {routine.lowConfidence && (
            <div className={styles.notice}>아직 기록이 적어 큰 근육부터 고르게 짰어요. 기록이 쌓일수록 내 운동에 맞춰져요</div>
          )}

          <ol className={styles.items}>
            {active.map((item, index) => (
              <li key={item.id} className={styles.item}>
                <div className={styles.itemHead}>
                  <span className={styles.itemNo}>{index + 1}</span>
                  <span className={styles.itemName}>{item.exerciseName}</span>
                  {item.firstTime && <span className={styles.firstBadge}>처음</span>}
                  {item.doneSets >= item.plannedSets && <span className={styles.doneBadge}>완료</span>}
                </div>

                <div className={styles.itemPlan}>
                  {item.plannedSets}세트 × {repRangeLabel(item)}
                  {item.doneSets > 0 && item.doneSets < item.plannedSets && (
                    <span className={styles.itemDone}> · {item.doneSets}세트 했어요</span>
                  )}
                </div>
                <div className={styles.itemBasis}>{basisLine(item)}</div>

                {item.firstTime ? (
                  <button
                    type="button"
                    className={styles.motionLink}
                    onClick={() => navigate(paths.exerciseDetail(item.exerciseId))}
                  >
                    동작 보기 →
                  </button>
                ) : (
                  lastLines[item.exerciseId] && (
                    <div className={styles.itemLast}>지난 기록 {lastLines[item.exerciseId]}</div>
                  )
                )}

                {editable && (
                  <div className={styles.itemActions}>
                    <button type="button" className={styles.actionButton} onClick={() => void openSwap(item)}>
                      교체
                    </button>
                    <Stepper
                      value={item.plannedSets}
                      min={MIN_SETS}
                      max={MAX_SETS}
                      unit="세트"
                      onChange={(next) => void changeSets(item, next)}
                      aria-label={`${item.exerciseName} 세트 수`}
                    />
                    <button
                      type="button"
                      className={`${styles.actionButton} ${styles.actionQuiet}`}
                      onClick={() => setRemoveItem(item)}
                    >
                      빼기
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ol>

          {removed.length > 0 && (
            <div className={styles.removed}>뺀 종목 · {removed.map((item) => item.exerciseName).join(', ')}</div>
          )}
        </div>

        {editable && active.length > 0 && (
          <div className={styles.actionBar}>
            <button type="button" className={styles.primary} onClick={() => navigate(paths.session)}>
              이 루틴으로 기록
            </button>
          </div>
        )}
      </div>

      <BottomSheet
        open={swapItem !== null}
        onClose={() => setSwapItem(null)}
        title={<span className={styles.sheetTitle}>{swapItem?.muscleLabel} 종목 바꾸기</span>}
      >
        {alternatives === null ? (
          <div className={styles.empty}>불러오는 중…</div>
        ) : alternatives.length === 0 ? (
          <div className={styles.empty}>바꿀 수 있는 종목이 없어요</div>
        ) : (
          <div className={styles.altScroll}>
            {[
              { label: '다른 기구', list: otherEquipment },
              { label: '같은 기구', list: sameEquipment },
            ]
              .filter((group) => group.list.length > 0)
              .map((group) => (
                <div key={group.label}>
                  <div className={styles.altGroup}>{group.label}</div>
                  {group.list.map((alt) => (
                    <button
                      key={alt.exerciseId}
                      type="button"
                      className={styles.altRow}
                      disabled={busy}
                      onClick={() => void swap(alt)}
                    >
                      <span className={styles.altName}>{alt.exerciseName}</span>
                      {alt.firstTime && <span className={styles.firstBadge}>처음</span>}
                      <span className={styles.altEquipment}>{EQUIPMENT_LABEL[alt.equipment] ?? alt.equipment}</span>
                    </button>
                  ))}
                </div>
              ))}
          </div>
        )}
      </BottomSheet>

      <BottomSheet
        open={removeItem !== null}
        onClose={() => setRemoveItem(null)}
        title={<span className={styles.sheetTitle}>종목 빼기</span>}
      >
        {removeItem && (
          <>
            <p className={styles.confirmText}>
              <b>{removeItem.exerciseName}</b>을(를) 오늘 루틴에서 뺄까요? 다시 넣을 수는 없어요. 기록 화면에서
              직접 추가할 수는 있어요.
            </p>
            <button type="button" className={styles.confirmDanger} disabled={busy} onClick={() => void remove()}>
              빼기
            </button>
          </>
        )}
      </BottomSheet>
    </>
  )
}
