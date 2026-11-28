/**
 * 운동 일지를 이미지로(결정 기록 D-24). 화면에 그린 일지를 그대로 PNG로 굽는다 — html-to-image가 요소를 SVG에 담아 그린다.
 *
 * <p>글꼴은 직접 골라 넣는다. SVG 이미지 안에서는 문서의 글꼴을 못 쓰므로 글꼴 파일을 이미지에 넣어야 하는데, Pretendard는 글자
 * 묶음마다 파일이 나뉘어 있어(dynamic subset, 90여 개) 라이브러리에 맡기면 전부 내려받아 넣는다. 일지에 쓰인 글자가 든 묶음만
 * 고른다.
 */

/** 이미지 배율. 화면 폭 390이면 780px 너비 — 휴대폰 사진으로 선명하다 */
const PIXEL_RATIO = 2

/** "U+AC00-D7A3, U+0041, U+4??" → 범위들. 없으면 전체 */
function parseUnicodeRange(value: string): Array<[number, number]> {
  return value
    .split(',')
    .map((part) => part.trim().replace(/^U\+/i, ''))
    .filter((part) => part.length > 0)
    .map((part): [number, number] => {
      if (part.includes('?')) {
        return [parseInt(part.replace(/\?/g, '0'), 16), parseInt(part.replace(/\?/g, 'F'), 16)]
      }
      const [from, to] = part.split('-')
      return [parseInt(from, 16), parseInt(to ?? from, 16)]
    })
}

/** 스타일시트 안의 @font-face를 모은다. @import한 시트(Pretendard)도 따라간다. 다른 출처 시트는 읽을 수 없어 건너뛴다 */
function collectFontFaces(rules: CSSRuleList, into: CSSFontFaceRule[]): void {
  for (const rule of Array.from(rules)) {
    if (rule instanceof CSSFontFaceRule) {
      into.push(rule)
    } else if (rule instanceof CSSImportRule && rule.styleSheet) {
      try {
        collectFontFaces(rule.styleSheet.cssRules, into)
      } catch {
        // 다른 출처
      }
    } else if (rule instanceof CSSGroupingRule) {
      collectFontFaces(rule.cssRules, into)
    }
  }
}

async function toDataUrl(url: string): Promise<string> {
  const blob = await (await fetch(url)).blob()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error ?? new Error('글꼴을 읽지 못했어요'))
    reader.readAsDataURL(blob)
  })
}

/** 요소에 쓰인 글자를 그리는 @font-face만, 글꼴 파일을 data URL로 넣은 CSS */
async function fontCssFor(node: HTMLElement): Promise<string> {
  const points = new Set<number>()
  for (const char of node.textContent ?? '') points.add(char.codePointAt(0) ?? 0)

  const faces: CSSFontFaceRule[] = []
  for (const sheet of Array.from(document.styleSheets)) {
    try {
      collectFontFaces(sheet.cssRules, faces)
    } catch {
      // 다른 출처
    }
  }

  const needed = faces.filter((face) => {
    const range = face.style.getPropertyValue('unicode-range')
    if (!range) return true
    const ranges = parseUnicodeRange(range)
    for (const point of points) {
      if (ranges.some(([from, to]) => point >= from && point <= to)) return true
    }
    return false
  })

  const css = await Promise.all(
    needed.map(async (face) => {
      const base = face.parentStyleSheet?.href ?? document.baseURI
      let text = face.cssText
      const urls = [...text.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/g)].map((match) => match[1])
      for (const url of new Set(urls)) {
        if (url.startsWith('data:')) continue
        text = text.split(url).join(await toDataUrl(new URL(url, base).href))
      }
      return text
    }),
  )
  return css.join('\n')
}

/**
 * 요소를 PNG로. 요소는 화면 밖(왼쪽 멀리)에 그려 둔 것이라, 이미지 안에서는 제자리에 놓는다.
 */
export async function renderJournalImage(node: HTMLElement): Promise<Blob> {
  // 저장을 누를 때만 쓰는 라이브러리라 그때 불러온다 — 첫 화면 꾸러미에 넣지 않는다
  const [{ toBlob }, fontEmbedCSS] = await Promise.all([import('html-to-image'), fontCssFor(node)])
  const blob = await toBlob(node, {
    pixelRatio: PIXEL_RATIO,
    fontEmbedCSS,
    style: { position: 'static', left: '0', top: '0', margin: '0' },
  })
  if (!blob) throw new Error('이미지를 만들지 못했어요')
  return blob
}

/** 아이폰·아이패드. 사파리는 내려받기가 "파일"로 가서, 공유 시트의 "이미지 저장"으로 사진에 넣는다 */
function isAppleMobile(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.userAgent.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

/**
 * 저장. 아이폰은 공유 시트("이미지 저장" → 사진), 그 밖은 내려받기. 공유는 누른 그 순간에만 열 수 있어(사파리) 버튼의
 * 누르기에서 바로 부른다.
 *
 * @return 내려받았으면 true(알림을 띄운다), 공유 시트를 열었거나 닫았으면 false
 */
export async function saveImage(blob: Blob, fileName: string): Promise<boolean> {
  const file = new File([blob], fileName, { type: 'image/png' })
  if (isAppleMobile() && navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file] })
    } catch (err) {
      // 공유 시트를 그냥 닫은 것
      if (err instanceof DOMException && err.name === 'AbortError') return false
      throw err
    }
    return false
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
  return true
}
