import { useState } from 'react'
import { useToast } from '../../components'
import { ErrorCodes, exerciseApi, isApiError } from '../../api'
import type { BrowseCategory, CategoryCount, Equipment, Exercise, MeasureType } from '../../api'
import styles from './customExercise.module.css'

type Side = 'FRONT' | 'BACK'

const EQUIPMENT_CHOICES: { value: Equipment; label: string }[] = [
  { value: 'BARBELL', label: '바벨' },
  { value: 'DUMBBELL', label: '덤벨' },
  { value: 'MACHINE', label: '머신' },
  { value: 'CABLE', label: '케이블' },
  { value: 'BODYWEIGHT', label: '맨몸' },
  { value: 'PULLUP_BAR', label: '풀업바' },
]

const MEASURE_CHOICES: { value: MeasureType; label: string }[] = [
  { value: 'WEIGHT_REPS', label: '무게 × 횟수' },
  { value: 'BODYWEIGHT_REPS', label: '횟수만' },
  { value: 'WEIGHTED_BODYWEIGHT', label: '맨몸 + 추가 무게' },
  { value: 'TIME', label: '시간' },
]

/** 분석이 앞·뒤를 나눠 보는 부위 — 칸 하나로는 어느 쪽인지 몰라 한 번 더 고른다 */
const SIDE_CHOICES: Partial<Record<BrowseCategory, { hint: string; choices: { value: Side; label: string }[] }>> = {
  LEGS: {
    hint: '하체는 분석에서 앞·뒤를 나눠 봐요',
    choices: [
      { value: 'FRONT', label: '앞허벅지' },
      { value: 'BACK', label: '뒤허벅지' },
    ],
  },
  NECK: {
    hint: '목은 분석에서 앞·뒤를 나눠 봐요 (옆·돌리기는 앞)',
    choices: [
      { value: 'FRONT', label: '목 앞' },
      { value: 'BACK', label: '목 뒤' },
    ],
  },
}

/** 기구로 고르는 기록 방식의 기본값 — 대부분 이 짝이라 한 번 덜 누른다(설계서 결정 12) */
function measureFor(equipment: Equipment): MeasureType {
  return equipment === 'BODYWEIGHT' || equipment === 'PULLUP_BAR' ? 'BODYWEIGHT_REPS' : 'WEIGHT_REPS'
}

/** 저장된 칸(근육·어깨 앞뒤)을 고르는 이름으로 — 고치기 창을 값이 채워진 채 연다(설계서 4장 대응표의 거꾸로) */
function targetOf(exercise: Exercise): { category: BrowseCategory | null; side: Side | null } {
  switch (exercise.primaryMuscle) {
    case 'chest':
      return { category: 'CHEST', side: null }
    case 'lats':
    case 'middle back':
      return { category: 'BACK', side: null }
    case 'shoulders':
      return { category: exercise.deltRegion === 'REAR' ? 'SHOULDERS_REAR' : 'SHOULDERS_FRONT', side: null }
    case 'traps':
      return { category: 'TRAPS', side: null }
    case 'triceps':
      return { category: 'TRICEPS', side: null }
    case 'biceps':
      return { category: 'BICEPS', side: null }
    case 'forearms':
      return { category: 'FOREARMS', side: null }
    case 'abdominals':
      return { category: 'ABS', side: null }
    case 'lower back':
      return { category: 'LOWER_BACK', side: null }
    case 'glutes':
      return { category: 'GLUTES', side: null }
    case 'quadriceps':
      return { category: 'LEGS', side: 'FRONT' }
    case 'hamstrings':
      return { category: 'LEGS', side: 'BACK' }
    case 'calves':
      return { category: 'CALVES', side: null }
    case 'front neck':
      return { category: 'NECK', side: 'FRONT' }
    case 'back neck':
      return { category: 'NECK', side: 'BACK' }
    default:
      return { category: null, side: null }
  }
}

interface CustomExerciseFormProps {
  /** 부위 버튼의 이름과 순서 — 부위 고르기 화면과 같은 13개 */
  categories: CategoryCount[]
  /** 연 자리. 그 부위(와 계열)가 미리 골라진다. 계열 안에서 열었으면 만든 종목이 그 계열에 들어간다 */
  origin: { category: BrowseCategory | null; group: string | null }
  /** 고치기면 그 종목. 값이 채워진 채 열린다 */
  editing?: Exercise
  /** 붙여넣기에서 열 때 — 글의 이름과, 글에서 짐작한 기구·기록 방식으로 미리 채운다 */
  prefill?: { name: string; equipment: Equipment | null; measureType: MeasureType | null }
  /** 고를 수 있는 기록 방식. 붙여넣기에서 열면 그 세트를 담을 수 있는 방식만 — 나머지는 누를 수 없다 */
  allowedMeasureTypes?: readonly MeasureType[]
  /** "만들고 오늘 기록에 넣기"·"만들기"·"저장" */
  submitLabel: string
  onSaved: (exercise: Exercise) => void
}

/**
 * 내 종목 만들기·고치기 창(「내종목_설계서」 3.3). 종목 고르기 시트 안과 종목 상세의 고치기에서 같이 쓴다.
 *
 * <p>부위는 부위 고르기 화면과 같은 14개로 고르고, 하체·목은 앞·뒤를 한 번 더 고른다. 아래 안내에 만든 종목이
 * 들어갈 자리를 적는다 — 계열 안에서 열었고 부위를 그대로 두면 그 계열, 아니면 그 부위의 "내 종목" 칸.
 */
export function CustomExerciseForm({
  categories,
  origin,
  editing,
  prefill,
  allowedMeasureTypes,
  submitLabel,
  onSaved,
}: CustomExerciseFormProps) {
  const allows = (value: MeasureType) => !allowedMeasureTypes || allowedMeasureTypes.includes(value)
  const { showToast } = useToast()
  const initialTarget = editing ? targetOf(editing) : { category: origin.category, side: null }

  const [name, setName] = useState(editing?.nameKo ?? prefill?.name ?? '')
  const [category, setCategory] = useState<BrowseCategory | null>(initialTarget.category)
  const [side, setSide] = useState<Side | null>(initialTarget.side)
  const sideChoice = category === null ? undefined : SIDE_CHOICES[category]
  const [equipment, setEquipment] = useState<Equipment | null>(editing?.equipment ?? prefill?.equipment ?? null)
  const [measureType, setMeasureType] = useState<MeasureType | null>(
    editing?.measureType ?? prefill?.measureType ?? null,
  )
  const [nameError, setNameError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const labelOf = (value: BrowseCategory | null) => categories.find((c) => c.category === value)?.label ?? ''
  // 계열은 부위에 딸려 있다 — 부위를 바꾸면 계열은 버리고 바꾼 부위의 "내 종목" 칸으로 간다
  const keepsGroup = origin.group !== null && category === origin.category
  const groupName = keepsGroup ? origin.group : null
  const landing = category === null ? null : `${labelOf(category)} › ${groupName ?? '내 종목'}`
  const movedTarget =
    editing !== undefined && (category !== initialTarget.category || side !== initialTarget.side)
  const ready =
    name.trim().length > 0 &&
    category !== null &&
    (sideChoice === undefined || side !== null) &&
    equipment !== null &&
    measureType !== null

  // 기구의 기본 방식을 이 기록에 쓸 수 없으면 고른 방식을 그대로 두거나, 쓸 수 있는 첫 방식으로
  const pickEquipment = (value: Equipment) => {
    setEquipment(value)
    const preferred = measureFor(value)
    setMeasureType((current) =>
      allows(preferred) ? preferred : current !== null && allows(current) ? current : (allowedMeasureTypes?.[0] ?? null),
    )
  }

  const submit = async () => {
    if (!ready || category === null || equipment === null || measureType === null || saving) return
    setSaving(true)
    setNameError(null)
    const body = {
      nameKo: name,
      category,
      side: sideChoice ? side : null,
      equipment,
      measureType,
      // 고치기는 계열을 그대로 둔다 — 부위를 바꾸면 계열은 버린다
      groupName: editing ? (category === initialTarget.category ? editing.groupName : null) : groupName,
    }
    try {
      const saved = editing ? await exerciseApi.update(editing.id, body) : await exerciseApi.create(body)
      onSaved(saved)
    } catch (err) {
      if (!isApiError(err)) throw err
      // 이름 겹침·상한·검증은 이름 칸 아래에 — 무엇을 고쳐야 하는지 그 자리에서 보이게
      if (
        err.code === ErrorCodes.EXERCISE_NAME_TAKEN ||
        err.code === ErrorCodes.EXERCISE_LIMIT_EXCEEDED ||
        err.code === ErrorCodes.VALIDATION_ERROR
      ) {
        setNameError(err.message)
      } else {
        showToast({ message: err.message, tone: 'danger' })
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.form}>
      <label className={styles.field}>
        <span className={styles.label}>이름</span>
        <input
          className={styles.input}
          value={name}
          onChange={(e) => {
            setName(e.target.value)
            setNameError(null)
          }}
          placeholder="예: 핀 머신 사이드 레터럴 레이즈"
          maxLength={50}
          aria-invalid={nameError !== null}
        />
        {nameError && <span className={styles.error}>{nameError}</span>}
      </label>

      <div className={styles.field}>
        <span className={styles.label}>
          운동 부위<span className={styles.labelNote}>약점 판정에 쓰여요</span>
        </span>
        <div className={styles.choices3}>
          {categories.map((item) => (
            <button
              key={item.category}
              type="button"
              className={`${styles.choice} ${category === item.category ? styles.choiceOn : ''}`}
              aria-pressed={category === item.category}
              onClick={() => {
                // 하체와 목의 앞·뒤는 다른 뜻이라 부위를 바꾸면 다시 고른다
                if (item.category !== category) setSide(null)
                setCategory(item.category)
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        {sideChoice && (
          <>
            <span className={styles.subLabel}>{sideChoice.hint}</span>
            <div className={styles.choices2}>
              {sideChoice.choices.map((choice) => (
                <button
                  key={choice.value}
                  type="button"
                  className={`${styles.choice} ${side === choice.value ? styles.choiceOn : ''}`}
                  aria-pressed={side === choice.value}
                  onClick={() => setSide(choice.value)}
                >
                  {choice.label}
                </button>
              ))}
            </div>
          </>
        )}
      </div>

      <div className={styles.field}>
        <span className={styles.label}>기구</span>
        <div className={styles.choices3}>
          {EQUIPMENT_CHOICES.map((choice) => (
            <button
              key={choice.value}
              type="button"
              className={`${styles.choice} ${equipment === choice.value ? styles.choiceOn : ''}`}
              aria-pressed={equipment === choice.value}
              onClick={() => pickEquipment(choice.value)}
            >
              {choice.label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.label}>
          기록 방식
          <span className={styles.labelNote}>
            {allowedMeasureTypes ? '가져온 기록에 맞는 방식만 고를 수 있어요' : '기구를 고르면 정해져요'}
          </span>
        </span>
        <div className={styles.choices2}>
          {MEASURE_CHOICES.map((choice) => (
            <button
              key={choice.value}
              type="button"
              className={`${styles.choice} ${measureType === choice.value ? styles.choiceOn : ''}`}
              aria-pressed={measureType === choice.value}
              disabled={!allows(choice.value)}
              onClick={() => setMeasureType(choice.value)}
            >
              {choice.label}
            </button>
          ))}
        </div>
      </div>

      <button type="button" className={styles.save} disabled={!ready || saving} onClick={() => void submit()}>
        {submitLabel}
      </button>
      <p className={styles.caption}>
        {editing
          ? movedTarget
            ? '부위를 바꾸면 지난 기록도 새 부위로 계산돼요'
            : '내 계정에서만 보여요'
          : `${landing ? `${landing}에 들어가요 · ` : ''}내 계정에서만 보여요`}
      </p>
    </div>
  )
}
