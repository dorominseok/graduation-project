import { useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { paths } from '../app/paths'
import { useLensMap } from './liquidLens'
import styles from './TabBar.module.css'

/**
 * 굴절하는 테두리 띠 두께(px)와 휘는 세기. 세기의 절반이 테두리 끝에서 배경을 끌어오는
 * 거리(px)다. 이 거리가 띠의 절반을 넘으면 배경이 접혀 거울처럼 뒤집힌 줄무늬가 생긴다
 */
const LENS_BEZEL = 16
const LENS_SCALE = 12

/** 목업의 탭 아이콘. 색은 currentColor로 받아 활성 상태를 CSS가 정한다. */
const icons = {
  home: (
    <>
      <path d="M4 11.5L12 4l8 7.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 10v9a1 1 0 001 1h4v-6h2v6h4a1 1 0 001-1v-9" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
    </>
  ),
  history: (
    <>
      <rect x="1" y="9" width="4" height="6" rx="1.5" fill="currentColor" />
      <rect x="19" y="9" width="4" height="6" rx="1.5" fill="currentColor" />
      <rect x="7" y="10.5" width="10" height="3" rx="1" fill="currentColor" />
      <rect x="4" y="7.5" width="2" height="9" rx="1" fill="currentColor" />
      <rect x="18" y="7.5" width="2" height="9" rx="1" fill="currentColor" />
    </>
  ),
  analysis: (
    <>
      <rect x="3" y="12" width="4" height="9" rx="1" fill="currentColor" />
      <rect x="10" y="7" width="4" height="14" rx="1" fill="currentColor" />
      <rect x="17" y="3" width="4" height="18" rx="1" fill="currentColor" />
    </>
  ),
  group: (
    <>
      <circle cx="9" cy="8" r="4" fill="none" stroke="currentColor" strokeWidth="2" />
      <circle cx="16" cy="10" r="3.2" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M2 20c0-4 3-6.5 7-6.5s7 2.5 7 6.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </>
  ),
  profile: (
    <>
      <circle cx="12" cy="8" r="4.2" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M4 20c0-4.5 3.6-7.5 8-7.5s8 3 8 7.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </>
  ),
}

/**
 * 탭과, 그 탭에 불을 켤 경로들.
 *
 * <p>설정은 프로필에서 들어가므로 프로필에 켠다. 종목 목록·상세는 홈·분석·기록
 * 어디서든 들어와 어느 탭이라 말할 수 없어 아무 탭에도 켜지 않는다.
 */
const tabs = [
  { to: paths.home, label: '홈', icon: icons.home, matches: [] as string[], exact: true },
  { to: paths.history, label: '기록', icon: icons.history, matches: [], exact: false },
  { to: paths.analysis, label: '분석', icon: icons.analysis, matches: [], exact: false },
  { to: paths.group, label: '그룹', icon: icons.group, matches: [], exact: false },
  { to: paths.profile, label: '프로필', icon: icons.profile, matches: [paths.settings], exact: false },
]

/** `base` 자체이거나 그 아래 경로인지. '/settings'는 '/settings/account'도 덮는다 */
function under(pathname: string, base: string): boolean {
  return pathname === base || pathname.startsWith(`${base}/`)
}

function isActive(tab: (typeof tabs)[number], pathname: string): boolean {
  return tab.exact
    ? pathname === tab.to
    : under(pathname, tab.to) || tab.matches.some((base) => under(pathname, base))
}

export function TabBar() {
  const { pathname } = useLocation()
  const activeIndex = tabs.findIndex((tab) => isActive(tab, pathname))

  // 물방울이 있던 자리. 켜진 탭이 없는 화면(종목)에서는 그 자리에서 사라진다 —
  // 0으로 보내면 홈 쪽으로 미끄러지면서 사라진다
  const [dropletAt, setDropletAt] = useState(Math.max(activeIndex, 0))
  if (activeIndex >= 0 && activeIndex !== dropletAt) setDropletAt(activeIndex)

  const navRef = useRef<HTMLElement>(null)
  const lens = useLensMap(navRef, LENS_BEZEL)

  return (
    <nav ref={navRef} className={`${styles.root} ${lens ? styles.lens : ''}`}>
      {/*
        가장자리 굴절 필터(liquidLens.ts). 가운데는 서리 낀 흐림, 테두리 띠는 지도대로
        휜 배경을 얹는다. 크로뮴에서 유리 테마일 때만 CSS가 이 필터를 건다.
      */}
      {lens && (
        <svg className={styles.lensDefs} aria-hidden="true">
          <filter
            id="tabbar-lens"
            x="0"
            y="0"
            width={lens.width}
            height={lens.height}
            filterUnits="userSpaceOnUse"
            colorInterpolationFilters="sRGB"
          >
            <feImage
              href={lens.href}
              x="0"
              y="0"
              width={lens.width}
              height={lens.height}
              preserveAspectRatio="none"
              result="map"
            />
            <feGaussianBlur in="SourceGraphic" stdDeviation="10" edgeMode="duplicate" result="frost" />
            <feDisplacementMap
              in="SourceGraphic"
              in2="map"
              scale={LENS_SCALE}
              xChannelSelector="R"
              yChannelSelector="G"
              result="bent"
            />
            <feGaussianBlur in="bent" stdDeviation="1.5" result="bentSoft" />
            <feColorMatrix
              in="map"
              type="matrix"
              values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 1 0 0"
              result="rim"
            />
            <feComposite in="bentSoft" in2="rim" operator="in" result="bentRim" />
            <feMerge>
              <feMergeNode in="frost" />
              <feMergeNode in="bentRim" />
            </feMerge>
          </filter>
        </svg>
      )}
      <span
        className={`${styles.droplet} ${activeIndex < 0 ? styles.dropletOff : ''}`}
        style={{ '--tab': dropletAt } as CSSProperties}
        aria-hidden="true"
      />
      {tabs.map((tab, index) => {
        const active = index === activeIndex
        return (
          <Link
            key={tab.to}
            to={tab.to}
            className={`${styles.tab} ${active ? styles.active : ''}`}
            aria-current={active ? 'page' : undefined}
          >
            {/* 색은 .tab / .active가 정하고, 아이콘은 currentColor로 물려받는다. */}
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              {tab.icon}
            </svg>
            <span className={styles.label}>{tab.label}</span>
          </Link>
        )
      })}
    </nav>
  )
}
