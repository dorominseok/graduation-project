import type { KeyboardEvent, PointerEvent } from 'react'
import type { OneRmTrend } from '../../api'
import { formatShortDate } from './format'
import styles from './oneRm.module.css'

type Point = OneRmTrend['points'][number]

interface OneRmChartProps {
  points: Point[]
  selected: number
  onSelect: (index: number) => void
}

// 그림 좌표. viewBox를 고정 비율로 두고 가로 폭에 맞춰 통째로 늘린다 —
// preserveAspectRatio="none"으로 찌그러뜨리면 점이 타원이 되고 글자가 눌린다.
const WIDTH = 320
const HEIGHT = 168
const PAD_LEFT = 34
// 끝점 라벨 자리. "104.5"가 들어가야 한다 — 30이었을 때 잘렸다
const PAD_RIGHT = 42
const PAD_TOP = 18
const PAD_BOTTOM = 24

/**
 * 점 사이가 이보다 좁으면 중간 점을 감춘다. 촘촘한 점마다 흰 테두리가 선을
 * 끊어 점선처럼 보이고, 점선은 "예측"으로 읽힌다. 끝점과 고른 점은 늘 그린다.
 */
const MIN_DOT_GAP = 16

/** 눈금 간격 후보. 1RM은 원판 단위(2.5kg)로 움직여서 2.5를 넣었다 */
const STEPS = [1, 2, 2.5, 5, 10, 20, 25, 50, 100]

/** 눈금을 깔끔한 수로 맞춘다. 3칸 안팎이 되도록 간격을 고른다 */
function yDomain(values: number[]): { lo: number; hi: number; step: number } {
  const min = Math.min(...values)
  const max = Math.max(...values)
  const step = STEPS.find((s) => s >= (max - min) / 3) ?? 100
  let lo = Math.floor(min / step) * step
  let hi = Math.ceil(max / step) * step
  if (hi === lo) {
    // 점이 하나뿐이거나 전부 같은 값이면 위아래로 한 칸씩 띄운다
    lo -= step
    hi += step
  }
  return { lo: Math.max(0, lo), hi, step }
}

function dayNumber(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number)
  return Date.UTC(year, month - 1, day) / 86_400_000
}

/**
 * 추정 1RM 추이 (명세 7.1).
 *
 * <p>가로축은 <b>날짜 간격 그대로</b>다. 기록 순번으로 벌리면 3주 쉰 구간과
 * 이틀 간격이 같은 폭이 되어, 쉬는 동안 떨어진 1RM이 급락처럼 보인다.
 *
 * <p>점마다 숫자를 달지 않는다. 끝점 하나만 적고, 나머지는 눌러서 본다 —
 * 눌린 점의 근거 세트는 패널이 아래에 적는다.
 */
export function OneRmChart({ points, selected, onSelect }: OneRmChartProps) {
  const { lo, hi, step } = yDomain(points.map((p) => p.estimatedOneRm))
  const plotW = WIDTH - PAD_LEFT - PAD_RIGHT
  const plotH = HEIGHT - PAD_TOP - PAD_BOTTOM

  const first = dayNumber(points[0].date)
  const span = dayNumber(points[points.length - 1].date) - first
  const xOf = (p: Point) =>
    span === 0 ? PAD_LEFT + plotW / 2 : PAD_LEFT + ((dayNumber(p.date) - first) / span) * plotW
  const yOf = (v: number) => PAD_TOP + (1 - (v - lo) / (hi - lo)) * plotH

  const ticks: number[] = []
  for (let v = lo; v <= hi + 1e-9; v += step) ticks.push(v)

  const xs = points.map(xOf)
  const sparse = xs.every((x, i) => i === 0 || x - xs[i - 1] >= MIN_DOT_GAP)
  const line = points.map((p, i) => `${xs[i]},${yOf(p.estimatedOneRm)}`).join(' ')
  const last = points[points.length - 1]
  const lastIndex = points.length - 1

  /** 누른 자리에서 가로로 가장 가까운 점. 8px 점을 정확히 누르라고 하지 않는다 */
  const pickNearest = (event: PointerEvent<SVGRectElement>) => {
    const box = event.currentTarget.ownerSVGElement?.getBoundingClientRect()
    if (!box) return
    const x = ((event.clientX - box.left) / box.width) * WIDTH
    let best = 0
    xs.forEach((px, i) => {
      if (Math.abs(px - x) < Math.abs(xs[best] - x)) best = i
    })
    onSelect(best)
  }

  const onKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    if (event.key === 'ArrowLeft') onSelect(Math.max(0, selected - 1))
    else if (event.key === 'ArrowRight') onSelect(Math.min(lastIndex, selected + 1))
    else return
    event.preventDefault()
  }

  return (
    <svg
      className={styles.chart}
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      role="group"
      aria-label="추정 1RM 추이. 좌우 화살표로 날짜를 옮긴다"
      tabIndex={0}
      onKeyDown={onKeyDown}
    >
      {ticks.map((v) => (
        <g key={v}>
          <line className={styles.grid} x1={PAD_LEFT} x2={WIDTH - PAD_RIGHT} y1={yOf(v)} y2={yOf(v)} />
          <text className={styles.yLabel} x={PAD_LEFT - 6} y={yOf(v)} dy="0.35em" textAnchor="end">
            {v}
          </text>
        </g>
      ))}

      {/* 고른 날짜의 세로 보조선. 끝점이면 그리지 않는다 — 오른쪽 테두리처럼 보인다 */}
      {selected !== lastIndex && (
        <line
          className={styles.crosshair}
          x1={xs[selected]}
          x2={xs[selected]}
          y1={PAD_TOP}
          y2={HEIGHT - PAD_BOTTOM}
        />
      )}

      {points.length > 1 && <polyline className={styles.line} points={line} />}

      {points.map((p, i) =>
        sparse || i === selected || i === lastIndex ? (
          <circle
            key={p.date}
            className={i === selected ? styles.dotOn : styles.dot}
            cx={xs[i]}
            cy={yOf(p.estimatedOneRm)}
            r={i === selected ? 5.5 : 4}
          />
        ) : null,
      )}

      {/* 끝점에만 값을 단다 */}
      <text
        className={styles.endLabel}
        x={xs[lastIndex] + 8}
        y={yOf(last.estimatedOneRm)}
        dy="0.35em"
      >
        {last.estimatedOneRm}
      </text>

      <text className={styles.xLabel} x={xs[0]} y={HEIGHT - 6} textAnchor={points.length > 1 ? 'start' : 'middle'}>
        {formatShortDate(points[0].date)}
      </text>
      {points.length > 1 && (
        <text className={styles.xLabel} x={xs[lastIndex]} y={HEIGHT - 6} textAnchor="end">
          {formatShortDate(last.date)}
        </text>
      )}

      {/* 누르는 자리는 그림 전체다 */}
      <rect
        className={styles.hit}
        x={PAD_LEFT - 12}
        y={0}
        width={plotW + 24}
        height={HEIGHT}
        onPointerDown={pickNearest}
        onPointerMove={(event) => {
          if (event.buttons > 0) pickNearest(event)
        }}
      />
    </svg>
  )
}
