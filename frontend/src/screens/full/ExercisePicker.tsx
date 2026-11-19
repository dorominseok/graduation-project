import { useCallback, useEffect, useState } from 'react'
import { useToast } from '../../components'
import { exerciseApi, isApiError, workoutApi } from '../../api'
import type { BrowseCategory, CategoryCount, Exercise, GroupCount, MeasureType, MuscleGroupKey } from '../../api'
import { BODY_PART_LABEL, EQUIPMENT_LABEL, MEASURE_CHIP_LABEL } from './exerciseLabels'
import { exerciseImage, hideOnError } from './exerciseImage'
import { CustomExerciseForm } from './CustomExerciseForm'
import styles from './exercise.module.css'
import cx from './customExercise.module.css'

const PAGE_SIZE = 30

/**
 * 부위 화면에서 계열 없이 만든 내 종목을 모은 칸. 계열처럼 단계(`group`)로 들어가고 주소에도 그대로 실린다.
 * 기본 종목의 계열 이름과 겹치지 않는다
 */
export const MINE_GROUP = '내 종목'

function Dumbbell({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="1" y="9" width="4" height="6" rx="1.5" fill="currentColor" />
      <rect x="19" y="9" width="4" height="6" rx="1.5" fill="currentColor" />
      <rect x="7" y="10.5" width="10" height="3" rx="1" fill="currentColor" />
      <rect x="4" y="7.5" width="2" height="9" rx="1" fill="currentColor" />
      <rect x="18" y="7.5" width="2" height="9" rx="1" fill="currentColor" />
    </svg>
  )
}
/** 최근 사용에 올릴 종목 수. 목록이 길어지면 "최근"이 아니게 된다 */
const RECENT_LIMIT = 8

/** 지금 보고 있는 단계. 바깥에서 URL과 맞출 수 있도록 값으로 주고받는다 */
export interface PickerStep {
  category: BrowseCategory | null
  group: string | null
  /**
   * 판정 부위로 바로 좁힌 목록. 홈의 약점에서 "오늘 운동에 추가"로 들어올 때만 채운다.
   * 탐색 분류와 축이 달라(어깨 앞·뒤, 두 분류에 걸친 뒤허벅지·둔근) 부위 → 계열 단계로는 못 연다
   */
  muscleGroup?: MuscleGroupKey | null
  /** 머리줄에 적을 이름. "어깨 후면" */
  muscleLabel?: string
}

type PickerTab = 'recent' | 'part' | 'favorite'

interface ExercisePickerProps {
  step: PickerStep
  onStepChange: (next: PickerStep) => void
  /** 종목을 골랐을 때. 기록 화면은 세트 행을 열고, 둘러보기는 상세로 간다 */
  onPick: (exercise: Exercise) => void
  /**
   * ⓘ를 눌렀을 때. 화면을 떠나도 되는 곳에서만 넘긴다 — 기록 중 시트에서는
   * 화면이 사라지면 아직 체크하지 않은 종목이 같이 사라지므로 넘기지 않는다.
   */
  onInfo?: (exercise: Exercise) => void
  /**
   * 기록 화면에서 오늘 이미 넣은 종목. 시트를 열어 둔 채 여러 종목을 넣으므로 무엇을 넣었는지 알약으로
   * 보인다. 같은 종목을 또 넣는 것은 막지 않는다
   */
  addedIds?: ReadonlySet<number>
  /**
   * 고른 뒤 검색어를 비운다. 기록 화면은 시트를 닫지 않고 처음 단계로 돌아가 다음 종목을 고르게 한다 —
   * 단계(부위·계열)는 바깥이 되돌리고, 검색어는 여기 있어 여기서 비운다. 탭은 그대로 둔다
   * (즐겨찾기에서 연달아 고르는 사람을 부위별로 옮기지 않게)
   */
  clearSearchOnPick?: boolean
  /**
   * 내 종목 만들기 창의 버튼 이름. 기록 화면은 "만들고 오늘 기록에 넣기", 둘러보기는 "만들기".
   * 만든 종목은 {@code onPick}으로 넘긴다 — 기록 화면은 오늘 기록에 넣고, 둘러보기는 상세로 간다
   */
  createLabel?: string
  /**
   * 이 기록 방식의 종목만 보인다 — 붙여넣기의 "바꾸기"는 가져온 세트를 담을 수 있는 종목만 고른다.
   * 넘기면 만들기 버튼은 두지 않는다(붙여넣기 화면에 글로 미리 채우는 만들기가 따로 있다)
   */
  measureTypes?: readonly MeasureType[]
}

/**
 * 종목 고르기 — 부위 → 계열 → 변형 3단.
 *
 * <p>화면과 시트 양쪽에서 쓴다. 기록 중에는 시트로 띄워야 아직 체크하지 않은 종목이
 * 사라지지 않는다. 단계 상태를 밖에서 받는 것은 둘러보기 화면은 URL에 두어 뒤로가기가
 * 통하게 하고, 시트는 지역 상태로 두기 때문이다.
 */
/**
 * 부위 그림. 대상 근육만 붉게 칠한 해부도로, `public/parts/`에 분류 이름대로 둔다
 * (SHOULDERS_FRONT → shoulders-front.webp).
 */
function partImage(category: BrowseCategory): string {
  return `/parts/${category.toLowerCase().replaceAll('_', '-')}.webp`
}

export function ExercisePicker({
  step,
  onStepChange,
  onPick,
  onInfo,
  addedIds,
  clearSearchOnPick = false,
  createLabel = '만들기',
  measureTypes,
}: ExercisePickerProps) {
  // 하나뿐이면 서버가 거른다(쪽 나눔이 맞게). 여럿이면 받은 뒤 여기서 거른다
  const onlyMeasure = measureTypes?.length === 1 ? measureTypes[0] : undefined
  const { showToast } = useToast()
  // 내 종목 만들기 창. 연 자리(부위·계열)를 기억해 그 부위를 미리 고르고, 만든 종목이 그 자리에 들어간다
  const [creating, setCreating] = useState<{ category: BrowseCategory | null; group: string | null } | null>(null)

  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [tab, setTab] = useState<PickerTab>('part')

  const [categories, setCategories] = useState<CategoryCount[]>([])
  const [groups, setGroups] = useState<GroupCount[]>([])
  const [items, setItems] = useState<Exercise[]>([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)

  const searching = debouncedKeyword.length > 0
  const categoryLabel = categories.find((c) => c.category === step.category)?.label ?? '부위'

  // 검색어를 칠 때마다 요청이 나가지 않도록 잠시 모아서 보낸다.
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [keyword])

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  const loadList = useCallback(
    async (nextPage: number, append: boolean) => {
      try {
        const result = await exerciseApi.search({
          q: debouncedKeyword || undefined,
          category: !searching && tab === 'part' && step.category ? step.category : undefined,
          group: !searching && tab === 'part' && step.group && step.group !== MINE_GROUP ? step.group : undefined,
          ungroupedMine: !searching && tab === 'part' && step.group === MINE_GROUP ? true : undefined,
          muscleGroup: !searching && tab === 'part' && step.muscleGroup ? step.muscleGroup : undefined,
          favorite: !searching && tab === 'favorite' ? true : undefined,
          measureType: onlyMeasure,
          page: nextPage,
          size: PAGE_SIZE,
        })
        setItems((prev) => (append ? [...prev, ...result.content] : result.content))
        setHasNext(!result.page.last)
        setPage(result.page.number)
      } catch (err) {
        fail(err)
      } finally {
        setLoading(false)
      }
    },
    [debouncedKeyword, searching, tab, step.category, step.group, step.muscleGroup, onlyMeasure, fail],
  )

  /**
   * 최근 사용 — 히스토리에 담긴 종목을 최신 순으로 훑어 중복을 걷어낸다.
   *
   * <p>"최근 쓴 종목" 전용 API는 두지 않았다. 기록이 이미 그 답을 들고 있어서
   * 따로 저장할 이유가 없다.
   */
  const loadRecent = useCallback(async () => {
    try {
      const history = await workoutApi.getHistory({ size: 10 })
      const ids: number[] = []
      for (const session of history.content) {
        for (const exercise of session.exercises) {
          if (!ids.includes(exercise.id)) ids.push(exercise.id)
        }
      }
      const found = await Promise.all(ids.slice(0, RECENT_LIMIT).map((id) => exerciseApi.get(id)))
      setItems(found)
      setHasNext(false)
    } catch (err) {
      fail(err)
    } finally {
      setLoading(false)
    }
  }, [fail])

  const loadCategories = useCallback(async () => {
    try {
      const found = await exerciseApi.categories()
      setCategories(found)
    } catch (err) {
      fail(err)
    } finally {
      setLoading(false)
    }
  }, [fail])

  const loadGroups = useCallback(
    async (target: BrowseCategory) => {
      try {
        const found = await exerciseApi.groups(target)
        setGroups(found)
      } catch (err) {
        fail(err)
      } finally {
        setLoading(false)
      }
    },
    [fail],
  )

  // 부위 이름과 개수는 어느 탭에 있어도 쓰이므로 한 번은 읽어둔다.
  useEffect(() => {
    const run = async () => {
      await loadCategories()
    }
    void run()
  }, [loadCategories])

  // 지금 보이는 것이 무엇이냐에 따라 읽을 것이 달라진다.
  useEffect(() => {
    void (async () => {
      if (searching || tab === 'favorite' || (tab === 'part' && (step.group || step.muscleGroup))) {
        await loadList(0, false)
      } else if (tab === 'recent') {
        await loadRecent()
      } else if (tab === 'part' && step.category) {
        await loadGroups(step.category)
      }
    })()
  }, [searching, tab, step.category, step.group, step.muscleGroup, loadList, loadGroups, loadRecent])

  const toggleFavorite = async (exercise: Exercise) => {
    const next = !exercise.isFavorite
    // 별표는 연타되는 자리라 응답을 기다리지 않고 먼저 뒤집는다.
    setItems((prev) => prev.map((it) => (it.id === exercise.id ? { ...it, isFavorite: next } : it)))
    try {
      if (next) {
        await exerciseApi.addFavorite(exercise.id)
      } else {
        await exerciseApi.removeFavorite(exercise.id)
        // 즐겨찾기 탭에서 해제했으면 그 목록에 남아 있을 이유가 없다.
        if (tab === 'favorite' && !searching) {
          setItems((prev) => prev.filter((it) => it.id !== exercise.id))
        }
      }
    } catch (err) {
      setItems((prev) =>
        prev.map((it) => (it.id === exercise.id ? { ...it, isFavorite: !next } : it)),
      )
      fail(err)
    }
  }

  const changeTab = (next: PickerTab) => {
    if (next === tab) return
    setItems([])
    setHasNext(false)
    setTab(next)
  }

  const byMuscle = !searching && tab === 'part' && !!step.muscleGroup
  const showParts = !searching && tab === 'part' && step.category === null && !byMuscle
  const showGroups = !searching && tab === 'part' && step.category !== null && step.group === null
  const showItems = !showParts && !showGroups
  /** 부위로 좁혀 들어온 목록. 부위 알약은 이미 아는 값이라 넣지 않는다 */
  const scopedToPart = (!searching && tab === 'part' && step.group !== null) || byMuscle
  const inMine = !searching && tab === 'part' && step.group === MINE_GROUP
  /** 만들기 버튼을 둘 수 있는 곳 — 부위별 탭의 세 단계. 검색·최근·즐겨찾기·약점에서 온 목록은 부위를 미리 고를 수 없다 */
  const canCreate = !searching && tab === 'part' && !byMuscle && !measureTypes
  const shownItems = measureTypes ? items.filter((item) => measureTypes.includes(item.measureType)) : items

  const openCreate = () => setCreating({ category: step.category, group: step.group === MINE_GROUP ? null : step.group })

  /** 만든 종목은 고른 것과 같다 — 기록 화면은 오늘 기록에 넣고, 둘러보기는 상세로 간다. 칸 숫자도 다시 읽는다 */
  const handleCreated = (exercise: Exercise) => {
    setCreating(null)
    void loadCategories()
    if (step.category !== null && step.group === null) void loadGroups(step.category)
    onPick(exercise)
  }

  const createButton = (wide = false) => (
    <button type="button" className={`${cx.newButton} ${wide ? cx.span2 : ''}`} onClick={openCreate}>
      + 새 종목 만들기
    </button>
  )

  const emptyMessage = measureTypes
    ? '이 기록에 맞는 종목이 없어요'
    : searching
    ? '검색 결과가 없어요'
    : tab === 'recent'
      ? '최근 사용한 종목이 없어요'
      : tab === 'favorite'
        ? '즐겨찾기한 종목이 없어요'
        : '찾는 종목이 없어요'

  const pick = (exercise: Exercise) => {
    onPick(exercise)
    if (clearSearchOnPick) {
      setKeyword('')
      setDebouncedKeyword('')
    }
  }

  /** 종목 한 장. 시작·끝 자세를 나란히 올리고 그 아래 이름과 알약을 둔다 */
  const renderItem = (exercise: Exercise) => (
    <div key={exercise.id} className={styles.item}>
      <button type="button" className={styles.itemMain} onClick={() => pick(exercise)}>
        <span className={styles.shots}>
          {([1, 2] as const).map((shot) => {
            const src = exerciseImage(exercise.nameEn, shot)
            return src ? (
              <img
                key={shot}
                className={styles.shot}
                src={src}
                alt=""
                loading="lazy"
                onError={hideOnError}
              />
            ) : (
              <span key={shot} className={styles.shot} />
            )
          })}
        </span>
        <span className={styles.itemBody}>
          <span className={styles.itemName}>{exercise.nameKo}</span>
          <span className={styles.chips}>
            {addedIds?.has(exercise.id) && <span className={`${styles.chip} ${styles.chipAdded}`}>추가됨</span>}
            {exercise.mine && <span className={`${styles.chip} ${styles.chipMine}`}>내 종목</span>}
            {!scopedToPart && (
              <span className={styles.chip}>
                {BODY_PART_LABEL[exercise.bodyPart] ?? exercise.bodyPart}
              </span>
            )}
            <span className={styles.chip}>
              {MEASURE_CHIP_LABEL[exercise.measureType] ?? exercise.measureType}
            </span>
            <span className={styles.chip}>
              {EQUIPMENT_LABEL[exercise.equipment] ?? exercise.equipment}
            </span>
          </span>
        </span>
      </button>

      {/* 카드를 누르는 것과 다른 동작이라 카드 아래에 따로 둔다 */}
      <div className={styles.itemActions}>
        {onInfo && (
          <button
            type="button"
            className={styles.itemAction}
            onClick={() => onInfo(exercise)}
            aria-label="종목 상세"
          >
            ⓘ
          </button>
        )}
        <button
          type="button"
          className={`${styles.itemAction} ${exercise.isFavorite ? styles.starOn : ''}`}
          onClick={() => void toggleFavorite(exercise)}
          aria-label={exercise.isFavorite ? '즐겨찾기 해제' : '즐겨찾기'}
        >
          {exercise.isFavorite ? '★' : '☆'}
        </button>
      </div>
    </div>
  )

  return (
    <div className={styles.page}>
      <input
        className={styles.search}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        placeholder="종목 검색"
        aria-label="종목 검색"
      />

      {/* 검색 중에는 탭이 무엇이든 결과가 우선이라 탭 자체를 감춘다. */}
      {!searching && (
        <div className={styles.tabs}>
          <button
            type="button"
            className={`${styles.tab} ${tab === 'recent' ? styles.tabOn : ''}`}
            onClick={() => changeTab('recent')}
          >
            최근 사용
          </button>
          <button
            type="button"
            className={`${styles.tab} ${tab === 'part' ? styles.tabOn : ''}`}
            onClick={() => changeTab('part')}
          >
            부위별
          </button>
          <button
            type="button"
            className={`${styles.tab} ${tab === 'favorite' ? styles.tabOn : ''}`}
            onClick={() => changeTab('favorite')}
          >
            즐겨찾기
          </button>
        </div>
      )}

      {/* 내 종목 만들기 창. 탭 아래를 이 창이 차지하고, ←로 연 자리에 돌아간다 */}
      {creating !== null && (
        <>
          <div className={styles.crumb}>
            <button
              type="button"
              className={styles.crumbBack}
              onClick={() => setCreating(null)}
              aria-label="만들기 닫기"
            >
              ←
            </button>
            <span className={styles.crumbLabel}>새 종목 만들기</span>
          </div>
          <div className={styles.scroll}>
            <CustomExerciseForm
              categories={categories}
              origin={creating}
              submitLabel={createLabel}
              onSaved={handleCreated}
            />
          </div>
        </>
      )}

      {/* 약점에서 바로 들어온 목록. 뒤로 가면 부위 그리드다 */}
      {creating === null && byMuscle && (
        <div className={styles.crumb}>
          <button
            type="button"
            className={styles.crumbBack}
            onClick={() => onStepChange({ category: null, group: null, muscleGroup: null })}
            aria-label="부위 목록으로"
          >
            ←
          </button>
          <span className={styles.crumbLabel}>{step.muscleLabel ?? '부위'} 종목</span>
        </div>
      )}

      {/* 부위로 들어온 뒤에는 나가는 길과 지금 자리를 같은 줄에 둔다. */}
      {creating === null && !searching && tab === 'part' && step.category !== null && (
        <div className={styles.crumb}>
          <button
            type="button"
            className={styles.crumbBack}
            onClick={() =>
              onStepChange(
                step.group ? { category: step.category, group: null } : { category: null, group: null },
              )
            }
            aria-label="한 단계 위로"
          >
            ←
          </button>
          <span className={styles.crumbLabel}>
            {step.group ? `${categoryLabel} · ${step.group}` : categoryLabel}
          </span>
        </div>
      )}

      {creating === null && showParts && (
        <div className={styles.partGrid}>
          {categories.map((item) => (
            <button
              key={item.category}
              type="button"
              className={styles.partCard}
              onClick={() => {
                setGroups([])
                onStepChange({ category: item.category, group: null })
              }}
              disabled={item.count === 0}
            >
              {/* 이름이 바로 아래 있어 그림은 꾸밈으로 둔다 */}
              <img className={styles.partShot} src={partImage(item.category)} alt="" width={480} height={360} />
              <span className={styles.partName}>
                {item.label}
                <span className={styles.partCount}>{item.count}</span>
              </span>
            </button>
          ))}
          {/* 부위 고르기 맨 아래 — 부위를 미리 고르지 않는다 */}
          {canCreate && categories.length > 0 && createButton(true)}
        </div>
      )}

      {creating === null && !showParts && (
        <div className={styles.scroll}>
          {showGroups &&
            groups.map((item) =>
              item.mine ? (
                // 계열 없이 만든 내 종목을 모은 칸. 계열 카드와 같은 모양으로 맨 끝에 하나
                <button
                  key="mine"
                  type="button"
                  className={styles.groupCard}
                  onClick={() => {
                    setItems([])
                    onStepChange({ category: step.category, group: MINE_GROUP })
                  }}
                >
                  <span className={cx.groupNoShot}>
                    <Dumbbell size={26} />
                  </span>
                  <span className={`${styles.groupName} ${styles.groupNameMine}`}>{MINE_GROUP}</span>
                  <span className={styles.chip}>{item.count}가지</span>
                  <span className={styles.groupArrow}>›</span>
                </button>
              ) : (
                <button
                  key={item.groupName}
                  type="button"
                  className={styles.groupCard}
                  onClick={() => {
                    setItems([])
                    onStepChange({ category: step.category, group: item.groupName })
                  }}
                >
                  {/* 계열을 대표하는 종목 한 장. 이름만으로는 어떤 동작인지 모른다 */}
                  <img
                    className={styles.groupThumb}
                    src={exerciseImage(item.representativeNameEn) ?? ''}
                    alt=""
                    loading="lazy"
                    onError={hideOnError}
                  />
                  <span className={styles.groupName}>{item.groupName}</span>
                  <span className={styles.chip}>{item.count}가지</span>
                  <span className={styles.groupArrow}>›</span>
                </button>
              ),
            )}

          {/* 계열 목록 맨 아래 — 이 부위가 골라진 채 열린다. 만든 종목은 "내 종목" 칸으로 */}
          {showGroups && canCreate && groups.length > 0 && createButton()}

          {showItems && <div className={styles.itemGrid}>{shownItems.map(renderItem)}</div>}

          {loading && <div className={styles.empty}>불러오는 중…</div>}

          {!loading && showItems && shownItems.length === 0 && (
            <div className={styles.empty}>{inMine ? '아직 만든 종목이 없어요' : emptyMessage}</div>
          )}

          {/* 종목 목록 맨 아래 — 이 부위와 계열이 골라진 채 열린다. 만든 종목은 이 계열에(내 종목 칸이면 그 칸에) */}
          {showItems && scopedToPart && canCreate && !loading && createButton()}

          {!loading && showGroups && groups.length === 0 && (
            <div className={styles.empty}>{emptyMessage}</div>
          )}

          {showItems && hasNext && !loading && (
            <button
              type="button"
              className={styles.more}
              onClick={() => {
                setLoading(true)
                void loadList(page + 1, true)
              }}
            >
              더 보기
            </button>
          )}
        </div>
      )}
    </div>
  )
}
