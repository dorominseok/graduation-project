import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, ScreenHeader, useToast } from '../../components'
import { ErrorCodes, exerciseApi, importApi, isApiError } from '../../api'
import type {
  CategoryCount,
  Exercise,
  ImportExerciseBrief,
  ImportPreview,
  ImportPreviewExercise,
  ImportStatus,
  MeasureType,
} from '../../api'
import { formatDayLabel } from '../../app/dates'
import { paths } from '../../app/paths'
import { ExercisePicker } from './ExercisePicker'
import type { PickerStep } from './ExercisePicker'
import { CustomExerciseForm } from './CustomExerciseForm'
import { SetGrid } from './SetGrid'
import type { GridSet } from './SetGrid'
import group from '../group/group.module.css'
import session from './session.module.css'
import full from './fullScreen.module.css'
import styles from './import.module.css'

/** 내 종목 이름 상한(「내종목_설계서」). 원래 이름이 더 길면 앞부분으로 채운다 */
const NAME_LIMIT = 50

const START_STEP: PickerStep = { category: null, group: null }

const PLACEHOLDER = `2026-11-17 바디캘린더 488번째 운동 일지

가슴2 등3 어깨3 이두1 삼두2

자유 운동 110분 18.4톤

풀업: 맨몸
1세트 81kg 19회
2세트 81kg 9회
…`

/**
 * 원래 이름마다 사용자가 고르거나 만든 종목. 여러 날에 같은 이름이 나오면 한 번 고르면 모두 바뀐다 —
 * 서버가 기억하는 짝(V17)도 원래 이름 하나에 종목 하나다.
 */
type Choices = Record<string, ImportExerciseBrief>

/** 지금 이 종목에 쓸 것 — 고른 종목이 이 세트를 담을 수 있으면 그것, 아니면 미리보기가 맞춘 것 */
function resolve(exercise: ImportPreviewExercise, choices: Choices) {
  const chosen = choices[exercise.source]
  if (chosen && exercise.allowedMeasureTypes.includes(chosen.measureType)) {
    return { brief: chosen, status: 'MATCHED' as ImportStatus, chosen: true }
  }
  return { brief: exercise.exercise, status: exercise.status, chosen: false }
}

function briefOf(exercise: Exercise): ImportExerciseBrief {
  return { id: exercise.id, nameKo: exercise.nameKo, measureType: exercise.measureType, mine: exercise.mine }
}

/** 고르러 연 자리 — 어느 원래 이름의 종목을 바꾸는지 */
interface Target {
  source: string
  allowed: MeasureType[]
  guess: ImportPreviewExercise['guess']
}

/**
 * 다른 앱 기록 붙여넣기(「기록가져오기_설계서」 5장). 첫 판은 바디캘린더의 "운동 일지 내보내기" 글.
 *
 * <p>붙여넣기 → 미리보기 → 추가. 읽기와 이름 맞추기는 서버가 하고, 화면은 맞춘 결과를 고치게 한다 —
 * "바꾸기"·"비슷한 종목 고르기"는 종목 고르기 시트(그 세트를 담을 수 있는 기록 방식만),
 * "+ 새 종목 만들기"는 원래 이름과 짐작한 기구·기록 방식이 채워진 내 종목 만들기 창.
 * 미등록 운동이 남으면 "추가"를 누를 수 없다.
 */
export function ImportScreen() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [text, setText] = useState('')
  const [pasteError, setPasteError] = useState<string | null>(null)
  const [reading, setReading] = useState(false)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [choices, setChoices] = useState<Choices>({})
  const [saving, setSaving] = useState(false)

  const [picking, setPicking] = useState<Target | null>(null)
  const [pickerStep, setPickerStep] = useState<PickerStep>(START_STEP)
  const [creating, setCreating] = useState<Target | null>(null)
  const [categories, setCategories] = useState<CategoryCount[]>([])

  // 만들기 창의 부위 13개
  useEffect(() => {
    exerciseApi
      .categories()
      .then(setCategories)
      .catch((err: unknown) => {
        if (!isApiError(err)) throw err
      })
  }, [])

  const read = async () => {
    if (reading) return
    setReading(true)
    setPasteError(null)
    try {
      setPreview(await importApi.preview(text))
      setChoices({})
    } catch (err) {
      if (!isApiError(err)) throw err
      // 형식이 아니거나 너무 길면 붙여넣기 칸 아래에 — 무엇이 잘못됐는지 그 자리에서
      if (err.code === ErrorCodes.IMPORT_FORMAT_UNKNOWN || err.code === ErrorCodes.VALIDATION_ERROR) {
        setPasteError(err.message)
      } else {
        showToast({ message: err.message, tone: 'danger' })
      }
    } finally {
      setReading(false)
    }
  }

  const choose = (source: string, exercise: Exercise) => {
    setChoices((prev) => ({ ...prev, [source]: briefOf(exercise) }))
    setPicking(null)
    setCreating(null)
  }

  if (preview === null) {
    return (
      <>
        <ScreenHeader title="다른 앱 기록 붙여넣기" />
        {/* 전체 화면 레이아웃은 스스로 스크롤하지 않는다 — 머리줄 아래를 감싸 스크롤한다 */}
        <div className={full.scroll}>
          <div className={styles.page}>
            <p className={styles.lead}>
              바디캘린더의 <b>운동 일지 내보내기</b> 글을 그대로 붙여넣으세요. 여러 날을 이어 붙여도 날짜별로 나눠
              읽어요.
            </p>
            <textarea
              className={styles.textarea}
              value={text}
              onChange={(e) => {
                setText(e.target.value)
                setPasteError(null)
              }}
              placeholder={PLACEHOLDER}
              aria-label="붙여넣을 기록"
              aria-invalid={pasteError !== null}
            />
            {pasteError && <p className={styles.error}>{pasteError}</p>}
            <button
              type="button"
              className={styles.primary}
              disabled={text.trim() === '' || reading}
              onClick={() => void read()}
            >
              {reading ? '읽는 중…' : '미리보기'}
            </button>
            <p className={styles.note}>유산소는 기록하지 않아요. 웜업은 워밍업 세트로, 드랍·실패는 본세트로 넣어요.</p>
          </div>
        </div>
      </>
    )
  }

  const all = preview.days.flatMap((day) => day.exercises)
  const unresolved = new Set(all.filter((e) => resolve(e, choices).brief === null).map((e) => e.source)).size
  const setTotal = all.reduce((sum, e) => sum + e.sets.length, 0)

  const add = async () => {
    if (saving || unresolved > 0 || preview.days.length === 0) return
    setSaving(true)
    try {
      const result = await importApi.save({
        days: preview.days.map((day) => ({
          date: day.date,
          minutes: day.minutes,
          exercises: day.exercises.map((exercise) => {
            const { brief, chosen } = resolve(exercise, choices)
            // 미등록 운동이 남으면 버튼이 잠겨 여기에 오지 않는다
            return { source: exercise.source, exerciseId: brief!.id, remember: chosen, sets: exercise.sets }
          }),
        })),
      })
      const added = result.days.reduce((sum, day) => sum + day.added, 0)
      const latest = result.days.reduce((a, b) => (a.date > b.date ? a : b))
      showToast({ message: added === 0 ? '이미 가져온 기록이에요' : `${result.days.length}일 기록을 가져왔어요` })
      // 붙여넣기 화면은 지나간 단계라 바꿔 간다 — 뒤로 가면 원래 기록 화면
      navigate(paths.sessionDetail(latest.sessionId), { replace: true })
    } catch (err) {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setSaving(false)
    }
  }

  const openPicker = (exercise: ImportPreviewExercise) => {
    setPickerStep(START_STEP)
    setPicking({ source: exercise.source, allowed: exercise.allowedMeasureTypes, guess: exercise.guess })
  }

  return (
    <>
      <ScreenHeader
        title="미리보기"
        onBack={() => setPreview(null)}
        trailing={
          <button
            type="button"
            className={styles.add}
            disabled={unresolved > 0 || preview.days.length === 0 || saving}
            onClick={() => void add()}
          >
            추가
          </button>
        }
      />

      <div className={full.scroll}>
        <div className={group.page}>
          {preview.days.length === 0 ? (
            <div className={`${group.card} ${styles.summary}`}>
              <span className={styles.summaryTitle}>가져올 근력 운동 기록이 없어요</span>
              <span className={styles.summaryNote}>유산소와 오늘 이후 날짜의 일지는 가져오지 않아요</span>
            </div>
          ) : (
            <div className={`${group.card} ${styles.summary}`}>
              <span className={styles.summaryTitle}>
                바디캘린더 기록 {preview.days.length}일 · {all.length}종목 · {setTotal}세트를 읽었어요
              </span>
              {/* 다 맞췄을 때는 아무 말도 붙이지 않는다 — 초록으로 바뀌는 "추가" 버튼이 말한다(설계서 결정 11) */}
              {unresolved > 0 && (
                <span className={styles.summaryWarn}>미등록 운동 {unresolved}개를 만들거나 고르면 추가할 수 있어요</span>
              )}
            </div>
          )}

          {preview.days.map((day) => (
            <section key={day.date} className={group.session}>
              <div className={group.sessionHead}>
                <span className={group.sessionMeta}>{formatDayLabel(day.date)}</span>
                <span className={group.sessionSets}>
                  {day.minutes ? `${day.minutes}분 · ` : ''}
                  {day.exercises.reduce((sum, e) => sum + e.sets.length, 0)}세트
                </span>
              </div>
              {/* 그날 기록이 이미 있어도 따로 알리지 않고 그 뒤에 이어 붙인다(설계서 결정 6) */}

              {day.exercises.map((exercise, index) => {
                const { brief, status } = resolve(exercise, choices)
                const measureType: MeasureType =
                  brief?.measureType ?? exercise.guess?.measureType ?? exercise.allowedMeasureTypes[0] ?? 'WEIGHT_REPS'
                const sets: GridSet[] = exercise.sets.map((set) => ({
                  weightKg: set.weightKg,
                  reps: set.reps,
                  durationSec: set.durationSec,
                  isWarmup: set.kind === 'WARMUP',
                  targetReps: set.kind === 'FAIL' ? set.targetReps : null,
                }))
                return (
                  <div key={index} className={`${group.card} ${group.exerciseCard}`}>
                    <div className={`${group.exerciseHead} ${styles.head}`}>
                      <span className={styles.names}>
                        <span className={group.exerciseName}>{brief?.nameKo ?? exercise.source}</span>
                        <span className={styles.source}>바디캘린더 · {exercise.source}</span>
                      </span>
                      {brief === null ? (
                        <span className={`${styles.badge} ${styles.badgeMissing}`}>미등록 운동</span>
                      ) : (
                        <span className={styles.headRight}>
                          {status === 'SIMILAR' && <span className={`${styles.badge} ${styles.badgeSimilar}`}>비슷한 종목</span>}
                          {brief.mine && <span className={`${styles.badge} ${styles.badgeMine}`}>내 종목</span>}
                          <button type="button" className={styles.link} onClick={() => openPicker(exercise)}>
                            바꾸기
                          </button>
                        </span>
                      )}
                    </div>
                    {brief === null && (
                      <div className={styles.missingActions}>
                        <button
                          type="button"
                          className={styles.createBtn}
                          onClick={() =>
                            setCreating({
                              source: exercise.source,
                              allowed: exercise.allowedMeasureTypes,
                              guess: exercise.guess,
                            })
                          }
                        >
                          + 새 종목 만들기
                        </button>
                        <button type="button" className={styles.pickBtn} onClick={() => openPicker(exercise)}>
                          비슷한 종목 고르기
                        </button>
                      </div>
                    )}
                    <SetGrid measureType={measureType} sets={sets} />
                  </div>
                )
              })}
            </section>
          ))}
        </div>
      </div>

      <BottomSheet
        open={picking !== null}
        onClose={() => setPicking(null)}
        title={<span className={styles.sheetTitle}>종목 고르기</span>}
      >
        {picking && (
          <div className={session.pickerBox}>
            <ExercisePicker
              step={pickerStep}
              onStepChange={setPickerStep}
              onPick={(exercise) => choose(picking.source, exercise)}
              measureTypes={picking.allowed}
            />
          </div>
        )}
      </BottomSheet>

      <BottomSheet
        open={creating !== null}
        onClose={() => setCreating(null)}
        title={<span className={styles.sheetTitle}>새 종목 만들기</span>}
      >
        {creating && (
          <CustomExerciseForm
            categories={categories}
            origin={{ category: null, group: null }}
            prefill={{
              name: creating.source.slice(0, NAME_LIMIT),
              equipment: creating.guess?.equipment ?? null,
              measureType: creating.guess?.measureType ?? null,
            }}
            allowedMeasureTypes={creating.allowed}
            submitLabel="만들고 이 기록에 쓰기"
            onSaved={(exercise) => choose(creating.source, exercise)}
          />
        )}
      </BottomSheet>
    </>
  )
}
