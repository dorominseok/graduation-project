import { useEffect, useState } from 'react'
import type { RefObject } from 'react'

/**
 * 유리 가장자리의 굴절. 둥근 유리판의 테두리가 볼록렌즈처럼 밑의 화면을 안쪽에서 끌어와
 * 휘어 보이게 한다.
 *
 * <p>요소 크기에 맞춘 변위 지도(displacement map)를 캔버스로 그리고, 그 지도로 뒤 배경을
 * 미는 SVG 필터를 backdrop-filter에 건다. 지도는 채널 셋을 쓴다.
 * <ul>
 *   <li>R·G — 그 점에서 배경을 어느 쪽에서 가져올지. 128이 제자리, 테두리에 가까울수록
 *       안쪽으로 멀리 가져온다
 *   <li>B — 테두리 띠. 이 안쪽은 굴절을, 바깥(가운데)은 서리 낀 흐림을 보여준다
 * </ul>
 *
 * <p>backdrop-filter에 SVG 필터를 먹는 건 크로뮴뿐이다. 사파리는 문법은 받고 그림은 빼먹어
 * 흐림까지 사라지므로 크로뮴에서만 켠다 — 나머지는 glass.css의 흐림 그대로다.
 */

/** 크로뮴 계열만 가진 API로 가른다. CSS.supports는 사파리에서도 참이 나온다 */
function lensSupported(): boolean {
  if (typeof navigator === 'undefined' || !('userAgentData' in navigator)) return false
  // "투명도 줄이기"를 켠 기기에는 굴절도 걸지 않는다
  return !window.matchMedia('(prefers-reduced-transparency: reduce)').matches
}

export interface LensMap {
  href: string
  width: number
  height: number
}

/**
 * 가로 w, 세로 h, 모서리 r인 둥근 사각형의 지도. bezel은 굴절하는 테두리 띠의 두께(px).
 * 픽셀마다 테두리까지의 거리를 재고, 띠 안에서는 바깥 법선의 반대(안쪽)로 민다.
 */
function drawLensMap(w: number, h: number, r: number, bezel: number): string {
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  const image = ctx.createImageData(w, h)
  const data = image.data
  const hw = w / 2
  const hh = h / 2

  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const px = x + 0.5 - hw
      const py = y + 0.5 - hh
      // 둥근 사각형까지의 부호 있는 거리(안쪽이 음수)
      const qx = Math.abs(px) - (hw - r)
      const qy = Math.abs(py) - (hh - r)
      const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
      const inside = Math.min(Math.max(qx, qy), 0)
      const depth = r - outside - inside // 테두리에서 안쪽으로 얼마나 들어왔나

      let dx = 0
      let dy = 0
      let rim = 0
      if (depth < bezel) {
        // 바깥 법선. 모서리에서는 둥근 쪽 중심에서 뻗고, 곧은 변에서는 변에 수직이다
        let nx: number
        let ny: number
        if (qx > 0 && qy > 0) {
          const len = Math.hypot(qx, qy)
          nx = qx / len
          ny = qy / len
        } else if (qx > qy) {
          nx = 1
          ny = 0
        } else {
          nx = 0
          ny = 1
        }
        nx *= Math.sign(px) || 1
        ny *= Math.sign(py) || 1
        // 테두리 끝으로 갈수록 가파르게 휜다(볼록면의 기울기)
        const t = 1 - Math.max(depth, 0) / bezel
        const bend = t * t
        dx = -nx * bend
        dy = -ny * bend
        rim = Math.min(1, t * 1.6)
      }

      const i = (y * w + x) * 4
      data[i] = Math.round(128 + dx * 127)
      data[i + 1] = Math.round(128 + dy * 127)
      data[i + 2] = Math.round(rim * 255)
      data[i + 3] = 255
    }
  }
  ctx.putImageData(image, 0, 0)
  return canvas.toDataURL()
}

/** 요소 크기가 바뀔 때마다 지도를 다시 그린다. 지원하지 않는 브라우저에서는 늘 null이다 */
export function useLensMap(ref: RefObject<HTMLElement | null>, bezel: number): LensMap | null {
  const [map, setMap] = useState<LensMap | null>(null)

  useEffect(() => {
    const el = ref.current
    if (!el || !lensSupported()) return
    let last = ''
    const observer = new ResizeObserver(() => {
      const width = Math.round(el.offsetWidth)
      const height = Math.round(el.offsetHeight)
      const radius = Math.min(parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0, width / 2, height / 2)
      const key = `${width}x${height}r${radius}`
      // 기본 테마의 탭바(모서리 0)에도 지도는 그려 두지만 CSS가 유리 테마에서만 쓴다
      if (width === 0 || height === 0 || key === last) return
      last = key
      setMap({ href: drawLensMap(width, height, radius, bezel), width, height })
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref, bezel])

  return map
}
