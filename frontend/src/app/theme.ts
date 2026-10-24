/**
 * 유리 색감. 설정 › 화면에서 고르고, 이 기기(브라우저)에만 남는다.
 *
 * <p>화면 스타일은 유리 하나다 — {@code <html data-theme="glass">}를 index.html에 박아 둔다.
 * 기본 스타일의 CSS는 지우지 않는다. 유리 규칙이 그 위에 덧씌우는 구조라 토대다.
 *
 * <p>색감은 {@code <html data-tint>}로 건다. 첫 화면이 하늘색으로 한 번 번쩍이지 않도록
 * index.html이 React보다 먼저 읽어 붙인다 — 저장 키나 색감 이름을 바꾸면 그 스크립트도 같이 바꾼다.
 */

/** 유리 배경 색감. 실제 색은 glass.css의 [data-tint] 규칙이 정한다 */
export const TINTS = ['sky', 'white', 'lavender', 'mint', 'peach', 'aurora'] as const
export type Tint = (typeof TINTS)[number]

const TINT_KEY = 'balancefit.tint'
const DEFAULT_TINT: Tint = 'sky'

/** 모바일 브라우저 상단 막대 색. 유리는 색감마다 배경 맨 위의 흰 빛과 맞춘다 */
const BAR_COLOR: Record<Tint, string> = {
  sky: '#f3f7fd',
  white: '#f9fafc',
  lavender: '#f6f3fd',
  mint: '#f1faf7',
  peach: '#fdf6f0',
  aurora: '#eef1fb',
}

export function getTint(): Tint {
  const tint = document.documentElement.dataset.tint
  return TINTS.find((t) => t === tint) ?? DEFAULT_TINT
}

export function setTint(tint: Tint): void {
  save(TINT_KEY, tint === DEFAULT_TINT ? null : tint)
  const root = document.documentElement
  if (tint === DEFAULT_TINT) delete root.dataset.tint
  else root.dataset.tint = tint
  syncBarColor()
}

/** 상단 막대 색을 지금 색감에 맞춘다. 앱이 뜰 때 한 번, 바꿀 때마다 부른다 */
export function syncBarColor(): void {
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR_COLOR[getTint()])
}

function save(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // 저장하지 못해도 지금 화면에는 적용한다. 새로고침하면 기본으로 돌아간다
  }
}
