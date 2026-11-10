import type { KeyboardEvent } from 'react'
import type { MuscleGroupKey, RecoveryMuscle, RecoveryState } from '../../../api'
import { BODY } from './bodyPaths'
import type { BodySide } from './bodyPaths'
import styles from './bodyMap.module.css'

/**
 * 그림의 영역 → 앱의 부위(「회복지도_설계서」 5장).
 *
 * <p>여기 없는 영역(머리·목·손·발·무릎·정강이 앞)은 앱이 세지 않는 곳이라 실루엣
 * 바탕색으로 칠하고 누를 수 없다. 어깨는 같은 영역 이름이 앞에서는 전·측면, 뒤에서는 후면이다.
 */
const REGION_MUSCLE: Record<BodySide, Record<string, MuscleGroupKey>> = {
  front: {
    chest: 'CHEST',
    trapezius: 'BACK',
    deltoids: 'DELT_FRONT',
    triceps: 'TRICEPS',
    biceps: 'BICEPS',
    quadriceps: 'QUADS',
    adductors: 'QUADS',
    abs: 'CORE',
    obliques: 'CORE',
    calves: 'CALVES',
    forearm: 'FOREARMS',
  },
  back: {
    trapezius: 'BACK',
    'upper-back': 'BACK',
    'lower-back': 'BACK',
    deltoids: 'DELT_REAR',
    triceps: 'TRICEPS',
    adductors: 'QUADS',
    gluteal: 'POSTERIOR',
    hamstring: 'POSTERIOR',
    calves: 'CALVES',
    forearm: 'FOREARMS',
  },
}

/**
 * 몸이 놓인 자리만 잘라 그린다. 원본 칸(724×1448)은 위아래·양옆이 비어 있어서 그대로 쓰면
 * 같은 폭에 몸이 작게 그려진다. 앞·뒤를 같은 크기로 잘라야 두 그림의 키가 맞는다.
 */
const CROP = { x: 38, y: 86, width: 648, height: 1276 }
const BACK_OFFSET = 724

const SIDE_LABEL: Record<BodySide, string> = { front: '앞', back: '뒤' }

const STATE_CLASS: Record<RecoveryState, string> = {
  HIGH: styles.high,
  MEDIUM: styles.medium,
  LOW: styles.low,
  RECOVERED: styles.recovered,
}

/** 한 번만 나눈다. 그림은 바뀌지 않는다 */
const DRAWING = (['front', 'back'] as const).reduce(
  (acc, side) => {
    const muscles = new Map<MuscleGroupKey, string[]>()
    const rest: string[] = []
    for (const region of BODY[side].regions) {
      const key = REGION_MUSCLE[side][region.slug]
      if (!key) rest.push(region.d)
      else muscles.set(key, [...(muscles.get(key) ?? []), region.d])
    }
    acc[side] = { muscles: [...muscles], rest }
    return acc
  },
  {} as Record<BodySide, { muscles: [MuscleGroupKey, string[]][]; rest: string[] }>,
)

interface BodyMapProps {
  side: BodySide
  muscles: RecoveryMuscle[]
  selected?: MuscleGroupKey | null
  /** 없으면 칠하기만 하고 누를 수 없다 */
  onSelect?: (key: MuscleGroupKey) => void
}

/**
 * 인체 그림 한 장(앞 또는 뒤). 부위를 회복 상태 색으로 칠하고, 누르면 그 부위를 고른다.
 * 화면에 보이는 "앞"·"뒤" 글자는 부르는 쪽이 붙인다.
 *
 * <p>같은 부위가 여러 조각이면(등 = 승모·등 위·허리) 한 묶음으로 칠하고 한 번에 눌린다.
 */
export function BodyMap({ side, muscles, selected = null, onSelect }: BodyMapProps) {
  const drawing = DRAWING[side]
  const byKey = new Map(muscles.map((m) => [m.key, m]))
  const x = CROP.x + (side === 'back' ? BACK_OFFSET : 0)

  const onKeyDown = (event: KeyboardEvent, key: MuscleGroupKey) => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    onSelect?.(key)
  }

  return (
    <svg
      className={styles.figure}
      viewBox={`${x} ${CROP.y} ${CROP.width} ${CROP.height}`}
      role="group"
      aria-label={`${SIDE_LABEL[side]}모습`}
    >
      <path className={styles.silhouette} d={BODY[side].outline} />
      <g className={styles.rest} aria-hidden="true">
        {drawing.rest.map((d, i) => (
          <path key={i} d={d} />
        ))}
      </g>

      {drawing.muscles.map(([key, paths]) => {
        const muscle = byKey.get(key)
        if (!muscle) return null
        if (!onSelect) {
          return (
            <g key={key} className={STATE_CLASS[muscle.state]}>
              {paths.map((d, i) => (
                <path key={i} d={d} />
              ))}
            </g>
          )
        }
        return (
          <g
            key={key}
            className={`${styles.muscle} ${STATE_CLASS[muscle.state]}`}
            role="button"
            tabIndex={0}
            aria-pressed={selected === key}
            aria-label={`${muscle.label}, ${muscle.stateLabel}`}
            onClick={() => onSelect(key)}
            onKeyDown={(event) => onKeyDown(event, key)}
          >
            {paths.map((d, i) => (
              <path key={i} d={d} />
            ))}
          </g>
        )
      })}

      {/* 고른 부위의 테두리. 맨 위에 따로 그려야 옆 부위에 가리지 않는다 */}
      {drawing.muscles
        .filter(([key]) => key === selected)
        .map(([key, paths]) => (
          <g key={key} className={styles.selection} aria-hidden="true">
            {paths.map((d, i) => (
              <path key={i} d={d} />
            ))}
          </g>
        ))}
    </svg>
  )
}
