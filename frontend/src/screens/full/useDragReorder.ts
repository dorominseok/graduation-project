import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { CSSProperties, MouseEvent as ReactMouseEvent, PointerEvent as ReactPointerEvent } from 'react'

/** 꾹 누르는 시간. 이보다 짧으면 누르기(종목 열기)다 */
const HOLD_MS = 380
/** 누르는 동안 이만큼 움직이면 스크롤·옆으로 밀기로 본다 */
const SLOP_PX = 8
/** 목록 위·아래 끝에서 이만큼 안으로 끌면 저절로 스크롤한다 */
const EDGE_PX = 64
/** 저절로 스크롤하는 한 장면(1/60초)의 최대 거리 */
const MAX_SCROLL_PX = 14
/** 놓은 자리로 들어가는 시간. 다른 줄이 비켜나는 시간과 같다 */
const SETTLE_MS = 200
/** 들어 올린 줄을 조금 키워 손가락 밑에 떠 있는 것처럼 */
const LIFT_SCALE = 1.03

/** 끌기 시작할 때 잰 줄의 자리. 목록 안쪽(스크롤 포함) 좌표 */
interface Layout {
  tops: number[]
  heights: number[]
  gap: number
}

interface View {
  from: number
  over: number
  /** 들어 올린 줄이 원래 자리에서 움직인 거리 */
  dy: number
  /** 손을 떼고 새 자리로 들어가는 중 */
  dropping: boolean
  /** 다른 줄이 비켜날 거리 — 들어 올린 줄 높이 + 줄 간격 */
  shift: number
}

interface Press {
  index: number
  pointerId: number
  x: number
  y: number
  timer: number
  el: HTMLElement
}

interface Drag {
  from: number
  over: number
  pointerId: number
  startY: number
  pointerY: number
  startScroll: number
  layout: Layout
  raf: number
}

interface Options {
  count: number
  /** 순서를 바꿨을 때. {@code from} 자리의 줄이 {@code to} 자리로 간다 */
  onReorder: (from: number, to: number) => void
  /** 줄을 들어 올렸을 때 — 옆으로 밀기(날짜 넘기기)를 멈추는 데 쓴다 */
  onLift?: () => void
  /**
   * 그 줄이 갈 수 있는 자리(처음·끝, 둘 다 포함). 없으면 목록 전체. 기록 화면은 회차 안에서만 옮긴다(결정 기록 D-22) —
   * 회차가 다른 카드는 세트의 회차를 바꿔야 해서 끌기로 하지 않는다
   */
  rangeOf?: (from: number) => readonly [number, number]
}

const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true

/**
 * 꾹 눌러 끌어서 순서 바꾸기(「운동기록_방식_설계서」 3.2).
 *
 * <p>줄을 0.38초 누르고 있으면 들어 올려지고, 끄는 동안 다른 줄이 비켜나며 자리를 만든다. 손을 떼면 그 자리로
 * 들어간 뒤 순서가 바뀐다. 누르는 동안 움직이면 스크롤·옆으로 밀기다. 목록 위·아래 끝으로 끌면 저절로 스크롤한다.
 *
 * <p>손가락·마우스 모두 포인터 이벤트로 받는다. 손가락으로 끄는 동안 화면이 스크롤되지 않게 목록에 수동적이지
 * 않은 {@code touchmove}를 달아 끄는 중에만 막는다 — 처음부터 달려 있어야 브라우저가 막을 수 있는 움직임으로 보낸다.
 */
export function useDragReorder({ count, onReorder, onLift, rangeOf }: Options) {
  const [view, setView] = useState<View | null>(null)
  const container = useRef<HTMLElement | null>(null)
  const press = useRef<Press | null>(null)
  const drag = useRef<Drag | null>(null)
  const settleTimer = useRef(0)
  // 끌기가 끝난 뒤 따라오는 누르기(click)를 한 번 삼킨다 — 놓자마자 종목이 열리면 안 된다
  const swallowClick = useRef(false)
  const callbacks = useRef({ onReorder, onLift, rangeOf })
  useLayoutEffect(() => {
    callbacks.current = { onReorder, onLift, rangeOf }
  })

  useEffect(
    () => () => {
      if (press.current) window.clearTimeout(press.current.timer)
      if (drag.current) cancelAnimationFrame(drag.current.raf)
      window.clearTimeout(settleTimer.current)
    },
    [],
  )

  const measure = (): Layout => {
    const box = container.current
    const boxTop = box ? box.getBoundingClientRect().top - box.scrollTop : 0
    // 줄은 화면 순서대로 놓여 있다. 각 줄의 data-reorder-index가 곧 자리다
    const rects = [...(box?.querySelectorAll<HTMLElement>('[data-reorder-index]') ?? [])]
      .slice(0, count)
      .map((el) => el.getBoundingClientRect())
    const tops = rects.map((rect) => rect.top - boxTop)
    const heights = rects.map((rect) => rect.height)
    // 줄 사이에 회차 머리 같은 것이 끼면 그 틈은 넓다 — 가장 좁은 틈이 줄 간격이다
    const gaps = tops.slice(1).map((top, k) => Math.max(0, top - (tops[k] + heights[k])))
    const gap = gaps.length > 0 ? Math.min(...gaps) : 0
    return { tops, heights, gap }
  }

  /**
   * 들어 올린 줄의 가운데가 지나간 다른 줄의 수 = 놓으면 들어갈 자리. 가운데가 딱 겹치면 아래쪽 줄은 지나간 것으로,
   * 위쪽 줄은 아닌 것으로 본다 — 끝 줄까지 끌면 줄이 끝에 멈춰 가운데가 겹치는데, 그때 끝 자리로 가야 한다
   */
  const overOf = (from: number, dy: number, layout: Layout) => {
    const center = layout.tops[from] + layout.heights[from] / 2 + dy
    let over = 0
    for (let k = 0; k < count; k++) {
      const other = layout.tops[k] + layout.heights[k] / 2
      if (k < from ? other < center : k > from && other <= center) over++
    }
    return over
  }

  const update = () => {
    const d = drag.current
    if (!d) return
    const { layout, from } = d
    const scrolled = (container.current?.scrollTop ?? d.startScroll) - d.startScroll
    // 갈 수 있는 첫 줄 위, 끝 줄 아래로는 나가지 않는다
    const [lo, hi] = callbacks.current.rangeOf?.(from) ?? [0, count - 1]
    const min = layout.tops[lo] - layout.tops[from]
    const max = layout.tops[hi] + layout.heights[hi] - (layout.tops[from] + layout.heights[from])
    const dy = Math.min(max, Math.max(min, d.pointerY - d.startY + scrolled))
    d.over = Math.min(hi, Math.max(lo, overOf(from, dy, layout)))
    setView({ from, over: d.over, dy, dropping: false, shift: layout.heights[from] + layout.gap })
  }

  /** 목록 위·아래 끝 근처면 그쪽으로 스크롤한다. 끄는 동안 장면마다 */
  const tick = () => {
    const d = drag.current
    const box = container.current
    if (!d || !box) return
    const rect = box.getBoundingClientRect()
    const fromTop = d.pointerY - rect.top
    const fromBottom = rect.bottom - d.pointerY
    const speed =
      fromTop < EDGE_PX ? -MAX_SCROLL_PX * (1 - Math.max(0, fromTop) / EDGE_PX)
      : fromBottom < EDGE_PX ? MAX_SCROLL_PX * (1 - Math.max(0, fromBottom) / EDGE_PX)
      : 0
    if (speed !== 0) {
      const before = box.scrollTop
      box.scrollTop += speed
      if (box.scrollTop !== before) update()
    }
    d.raf = requestAnimationFrame(tick)
  }

  const lift = () => {
    const p = press.current
    if (!p) return
    press.current = null
    const layout = measure()
    drag.current = {
      from: p.index,
      over: p.index,
      pointerId: p.pointerId,
      startY: p.y,
      pointerY: p.y,
      startScroll: container.current?.scrollTop ?? 0,
      layout,
      raf: 0,
    }
    try {
      p.el.setPointerCapture(p.pointerId)
    } catch {
      // 이미 손을 뗀 포인터면 잡지 못한다. 그래도 끌기는 이어진다
    }
    swallowClick.current = true
    callbacks.current.onLift?.()
    setView({ from: p.index, over: p.index, dy: 0, dropping: false, shift: layout.heights[p.index] + layout.gap })
    drag.current.raf = requestAnimationFrame(tick)
  }

  const cancelPress = () => {
    if (press.current) window.clearTimeout(press.current.timer)
    press.current = null
  }

  /** 손을 뗐을 때. 들어갈 자리로 미끄러져 들어간 뒤 순서를 바꾼다 */
  const drop = () => {
    const d = drag.current
    if (!d) return
    cancelAnimationFrame(d.raf)
    drag.current = null
    const { from, over, layout } = d
    let target = 0
    if (over > from) for (let k = from + 1; k <= over; k++) target += layout.heights[k] + layout.gap
    if (over < from) for (let k = over; k < from; k++) target -= layout.heights[k] + layout.gap
    const settle = reducedMotion() ? 0 : SETTLE_MS
    setView({ from, over, dy: target, dropping: true, shift: layout.heights[from] + layout.gap })
    settleTimer.current = window.setTimeout(() => {
      // 같은 차례에 바꿔 그려 움직임 없이 제자리가 된다 — 비켜난 줄은 이미 새 자리에 있다
      setView(null)
      if (over !== from) callbacks.current.onReorder(from, over)
    }, settle)
  }

  /** 목록 상자에 단다. 끄는 동안 손가락 스크롤을 막는다 */
  const containerRef = useCallback((el: HTMLElement | null) => {
    container.current = el
    if (!el) return
    const block = (e: TouchEvent) => {
      if (drag.current) e.preventDefault()
    }
    el.addEventListener('touchmove', block, { passive: false })
    return () => {
      el.removeEventListener('touchmove', block)
      if (container.current === el) container.current = null
    }
  }, [])

  /**
   * 모든 줄에 같은 것을 단다. 줄마다 {@code data-reorder-index}(자리)를 붙이고, {@code data-no-drag}가 붙은 곳
   * (빼기 버튼)에서 시작한 누르기는 끌지 않는다
   */
  const rowHandlers = {
    onPointerDown: (e: ReactPointerEvent<HTMLElement>) => {
      swallowClick.current = false
      if (count < 2 || drag.current || view?.dropping) return
      if (e.pointerType === 'mouse' && e.button !== 0) return
      if ((e.target as Element).closest('[data-no-drag]')) return
      const index = Number(e.currentTarget.dataset.reorderIndex)
      if (!Number.isInteger(index)) return
      cancelPress()
      press.current = {
        index,
        pointerId: e.pointerId,
        x: e.clientX,
        y: e.clientY,
        timer: window.setTimeout(lift, HOLD_MS),
        el: e.currentTarget,
      }
    },
    onPointerMove: (e: ReactPointerEvent<HTMLElement>) => {
      const p = press.current
      if (p && p.pointerId === e.pointerId) {
        if (Math.hypot(e.clientX - p.x, e.clientY - p.y) > SLOP_PX) cancelPress()
        return
      }
      const d = drag.current
      if (d && d.pointerId === e.pointerId) {
        d.pointerY = e.clientY
        update()
      }
    },
    onPointerUp: () => {
      cancelPress()
      drop()
    },
    // 스크롤이 시작됐거나 브라우저가 가져갔다. 끌던 중이면 지금 자리에 놓는다
    onPointerCancel: () => {
      cancelPress()
      drop()
    },
    onClickCapture: (e: ReactMouseEvent) => {
      if (!swallowClick.current) return
      swallowClick.current = false
      e.preventDefault()
      e.stopPropagation()
    },
    // 길게 누르면 뜨는 메뉴(안드로이드 크롬 등)를 막는다
    onContextMenu: (e: ReactMouseEvent) => {
      if (press.current || drag.current) e.preventDefault()
    },
  }

  /**
   * 줄의 움직임. 들어 올린 줄은 손가락을 바로 따라가고(옮기기에는 전환을 걸지 않는다), 사이의 줄은 부드럽게
   * 비켜난다. 옮기기(translate)와 키우기(scale)를 따로 써서 들어 올릴 때 키우는 것만 천천히 한다
   */
  const styleOf = (index: number): CSSProperties | undefined => {
    if (!view) return undefined
    const { from, over, dy, dropping, shift } = view
    const still = reducedMotion()
    const curve = `${SETTLE_MS}ms cubic-bezier(0.2, 0.8, 0.2, 1)`
    if (index === from) {
      return {
        position: 'relative',
        zIndex: 2,
        translate: `0 ${dy}px`,
        scale: dropping ? '1' : String(LIFT_SCALE),
        transition: still ? 'none' : dropping ? `translate ${curve}, scale ${curve}` : 'scale 120ms ease-out',
      }
    }
    let offset = 0
    if (from < over && index > from && index <= over) offset = -shift
    if (over < from && index >= over && index < from) offset = shift
    return { translate: offset === 0 ? undefined : `0 ${offset}px`, transition: still ? 'none' : `translate ${curve}` }
  }

  return {
    containerRef,
    rowHandlers,
    styleOf,
    /** 들어 올린 줄의 자리. 없으면 -1 */
    liftedIndex: view ? view.from : -1,
  }
}
