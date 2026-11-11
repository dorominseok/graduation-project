import { ScreenHeader } from '../../components'
import styles from './settings.module.css'

interface Notice {
  name: string
  license: string
  copyright?: string
  /** 무엇에 썼는지, 무엇을 바꿨는지 */
  body: string
  links: { label: string; href: string }[]
}

/**
 * 앱에 들어 있는 남의 저작물. 빌드하면 코드의 라이선스 주석이 지워지므로, 배포본에는 여기와
 * `/licenses/`의 원문이 고지다. 새로 가져다 쓰는 것이 생기면 여기에 더한다.
 *
 * <p>`/licenses/npm-packages.txt`는 빌드할 때 만들어진다(vite.config.ts의 `build.license`).
 * 개발 서버에는 없어서 그 링크는 배포본에서만 열린다.
 */
const SECTIONS: { title: string; notices: Notice[] }[] = [
  {
    title: '인체 그림',
    notices: [
      {
        name: 'react-muscle-highlighter 1.2.0',
        license: 'MIT License',
        copyright: 'Copyright (c) 2024 My Muscle Contributors',
        body: '분석 › 회복의 앞·뒤 인체 그림 경로를 가져와 앱에서 직접 그려요.',
        links: [{ label: '라이선스 원문', href: '/licenses/react-muscle-highlighter.txt' }],
      },
    ],
  },
  {
    title: '종목 동작 그림',
    notices: [
      {
        name: 'everkinetic',
        license: 'CC BY-SA 4.0',
        body: '폭을 줄이고 webp로 바꿨어요. 그림은 고치지 않았고, 같은 라이선스로 공개해요.',
        links: [
          { label: '출처', href: 'https://github.com/everkinetic/data' },
          { label: '라이선스', href: 'https://creativecommons.org/licenses/by-sa/4.0/' },
        ],
      },
      {
        name: 'free-exercise-db',
        license: 'Unlicense (퍼블릭 도메인)',
        body: 'everkinetic에 없는 종목의 그림이에요. 폭을 줄이고 webp로 바꿨어요.',
        links: [{ label: '출처', href: 'https://github.com/yuhonas/free-exercise-db' }],
      },
    ],
  },
  {
    title: '라이브러리 · 글꼴',
    notices: [
      {
        name: 'React 등 앱에 함께 들어간 라이브러리',
        license: 'MIT License 등',
        body: '라이브러리마다 이름·버전·라이선스 원문을 한 파일에 모았어요.',
        links: [{ label: '라이선스 원문', href: '/licenses/npm-packages.txt' }],
      },
      {
        name: 'Pretendard 1.3.9',
        license: 'SIL Open Font License 1.1',
        body: '앱의 글꼴이에요.',
        links: [{ label: '출처', href: 'https://github.com/orioncactus/pretendard' }],
      },
    ],
  },
]

/** 오픈소스 라이선스. 설정에서 들어온다. 원문과 출처는 새 창으로 연다 — 앱 화면이 아니라 문서다 */
export function LicensesScreen() {
  return (
    <>
      <ScreenHeader title="오픈소스 라이선스" />

      <div className={styles.page}>
        <p className={styles.hint}>밸런스핏에 들어 있는 오픈소스와 그 라이선스예요.</p>

        {SECTIONS.map((section) => (
          <div key={section.title} className={styles.section}>
            <div className={styles.sectionTitle}>{section.title}</div>
            {section.notices.map((notice) => (
              <div key={notice.name} className={styles.card}>
                <div className={styles.notice}>
                  <div className={styles.noticeName}>{notice.name}</div>
                  <div className={styles.noticeLicense}>{notice.license}</div>
                  {notice.copyright && <div className={styles.noticeBody}>{notice.copyright}</div>}
                  <div className={styles.noticeBody}>{notice.body}</div>
                </div>
                {notice.links.map((link) => (
                  <a key={link.href} className={styles.row} href={link.href} target="_blank" rel="noreferrer">
                    <span className={styles.rowLabel}>{link.label}</span>
                    <span className={styles.external} aria-hidden="true">
                      ↗
                    </span>
                  </a>
                ))}
              </div>
            ))}
          </div>
        ))}
      </div>
    </>
  )
}
