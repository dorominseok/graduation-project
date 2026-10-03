/**
 * 화면 스타일과 유리 색감. 설정 › 화면에서 고르고, 이 기기(브라우저)에만 남는다.
 *
 * <p>스타일은 {@code <html data-theme>}, 색감은 {@code <html data-tint>}로 건다. 첫 화면이
 * 기본 스타일로 한 번 번쩍이지 않도록 index.html이 React보다 먼저 두 값을 읽어 붙인다 —
 * 저장 키나 색감 이름을 바꾸면 index.html의 스크립트도 같이 바꾼다.
 */
export type Theme = 'default' | 'glass'

/** 유리 배경 색감. 실제 색은 glass.css의 [data-tint] 규칙이 정한다 */
export const TINTS = ['sky', 'white', 'lavender', 'mint', 'peach', 'aurora'] as const
export type Tint = (typeof TINTS)[number]

const THEME_KEY = 'balancefit.theme'
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

/** 지금 화면에 걸린 스타일. 처음 값은 index.html이 저장소에서 읽어 붙여 둔다 */
export function getTheme(): Theme {
  return document.documentElement.dataset.theme === 'glass' ? 'glass' : 'default'
}

export function getTint(): Tint {
  const tint = document.documentElement.dataset.tint
  return TINTS.find((t) => t === tint) ?? DEFAULT_TINT
}

export function setTheme(theme: Theme): void {
  save(THEME_KEY, theme === 'default' ? null : theme)
  const root = document.documentElement
  if (theme === 'default') delete root.dataset.theme
  else root.dataset.theme = theme
  syncBarColor()
}

export function setTint(tint: Tint): void {
  save(TINT_KEY, tint === DEFAULT_TINT ? null : tint)
  const root = document.documentElement
  if (tint === DEFAULT_TINT) delete root.dataset.tint
  else root.dataset.tint = tint
  syncBarColor()
}

/** 상단 막대 색을 지금 스타일·색감에 맞춘다. 앱이 뜰 때 한 번, 바꿀 때마다 부른다 */
export function syncBarColor(): void {
  const color = getTheme() === 'glass' ? BAR_COLOR[getTint()] : '#ffffff'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', color)
}

function save(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // 저장하지 못해도 지금 화면에는 적용한다. 새로고침하면 기본으로 돌아간다
  }
}
