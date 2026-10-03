import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { exerciseApi, isApiError } from '../../api'
import type { BrowseCategory, Exercise, MuscleGroupKey } from '../../api'
import { paths } from '../../app/paths'
import { ExercisePicker } from './ExercisePicker'
import type { PickerStep } from './ExercisePicker'
import { EQUIPMENT_LABEL, MEASURE_CHIP_LABEL } from './exerciseLabels'
import { exerciseImage, hideOnError } from './exerciseImage'
import styles from './exercise.module.css'

/**
 * 종목 둘러보기 (명세 5.2).
 *
 * 고르는 동작 자체는 ExercisePicker가 하고, 여기서는 단계를 URL에 실어
 * 뒤로가기가 한 단계씩 거슬러 올라가게 한다.
 *
 * 기록 중에 종목을 고를 때는 이 화면이 아니라 기록 화면의 시트를 쓴다 —
 * 화면을 옮기면 아직 체크하지 않은 종목이 사라지기 때문이다.
 *
 * 주소에 `muscleGroup`이 있으면 3단 탐색 대신 그 판정 부위의 종목만 늘어놓는다.
 * 홈의 약점에서 들어오는 길이다(LOG-29).
 */
export function ExerciseListScreen() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const muscleGroup = params.get('muscleGroup') as MuscleGroupKey | null
  if (muscleGroup) {
    return <MuscleGroupExercises muscleGroup={muscleGroup} label={params.get('label') ?? '부위'} />
  }

  const step: PickerStep = {
    category: (params.get('category') as BrowseCategory | null) ?? null,
    group: params.get('group'),
  }

  const handleStepChange = (next: PickerStep) => {
    const query: Record<string, string> = {}
    if (next.category) query.category = next.category
    if (next.group) query.group = next.group
    setParams(query)
  }

  return (
    <>
      {/*
        뒤로가기는 히스토리를 그대로 되돌린다. 단계를 옮길 때마다 URL이 바뀌므로
        한 번 누르면 한 단계씩 올라가고, 1단계에서 누르면 들어온 화면으로 나간다.
      */}
      <ScreenHeader title="운동 종목" />
      <ExercisePicker
        step={step}
        onStepChange={handleStepChange}
        onPick={(exercise: Exercise) => navigate(paths.exerciseDetail(exercise.id))}
        onInfo={(exercise: Exercise) => navigate(paths.exerciseDetail(exercise.id))}
      />
    </>
  )
}

/**
 * 판정 부위 하나의 종목.
 *
 * <p>탐색 분류(12종)로는 못 여는 묶음이다 — 어깨 분류엔 앞·뒤가 섞여 있고,
 * 뒤허벅지·둔근은 하체와 엉덩이 두 분류에 걸쳐 있다. 그래서 판정 부위로 조회한다.
 *
 * <p>많아야 십수 종이라 계열로 묶지 않고 한 화면에 다 보여준다.
 */
function MuscleGroupExercises({ muscleGroup, label }: { muscleGroup: MuscleGroupKey; label: string }) {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [items, setItems] = useState<Exercise[] | null>(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const page = await exerciseApi.search({ muscleGroup, size: 100 })
        if (alive) setItems(page.content)
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        if (alive) setItems([])
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [muscleGroup, showToast])

  return (
    <>
      <ScreenHeader title={`${label} 종목`} />
      <div className={styles.page}>
        <div className={styles.scroll}>
          {items === null && <div className={styles.empty}>불러오는 중…</div>}
          {items?.length === 0 && <div className={styles.empty}>이 부위 종목이 없어요</div>}
          {items && items.length > 0 && (
            <div className={styles.itemGrid}>
              {items.map((exercise) => (
                <div key={exercise.id} className={styles.item}>
                  <button
                    type="button"
                    className={styles.itemMain}
                    onClick={() => navigate(paths.exerciseDetail(exercise.id))}
                  >
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
                        <span className={styles.chip}>
                          {MEASURE_CHIP_LABEL[exercise.measureType] ?? exercise.measureType}
                        </span>
                        <span className={styles.chip}>
                          {EQUIPMENT_LABEL[exercise.equipment] ?? exercise.equipment}
                        </span>
                      </span>
                    </span>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
