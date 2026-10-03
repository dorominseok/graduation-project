import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { Balance, MuscleGroupKey, MuscleVolume } from '../../api'
import { paths } from '../../app/paths'
import { Chevron } from '../../components'
import {
  BADGE_TONE,
  BALANCE_TONE,
  BAR_TICKS,
  VERDICT_TONE,
  barPercent,
  basisLabel,
  formatMonthDay,
} from './format'
import type { Tone } from './format'
import styles from './analysis.module.css'

interface WeaknessViewProps {
  volume: MuscleVolume
  balance: Balance
}

type Pair = Balance['pairs'][number]

const TONE_CLASS: Record<Tone, string> = {
  danger: styles.toneDanger,
  warn: styles.toneWarn,
  good: styles.toneGood,
  neutral: styles.toneNeutral,
}

/**
 * 처음부터 펼쳐둘 상위 부위. "일부 부족"처럼 하위에 부족이 숨어 있는 것만.
 *
 * <p>이 화면이 알려줄 것은 약점이다. 삼두 0세트를 보려고 매번 "팔"을 눌러야
 * 한다면 배지만 보고 지나치기 쉽다. 반대로 전부 펼치면 12줄이 되어 부족한 곳이
 * 묻힌다.
 */
function initiallyOpen(volume: MuscleVolume): Set<string> {
  return new Set(
    volume.tiers
      .filter((t) => t.summaryBadge === 'PARTIAL_INSUFFICIENT' || t.summaryBadge === 'MIXED')
      .map((t) => t.key),
  )
}

function Bar({ weeklySets, tone, thin }: { weeklySets: number; tone: Tone; thin?: boolean }) {
  return (
    <div className={`${styles.track} ${thin ? styles.trackThin : ''}`} aria-hidden="true">
      {BAR_TICKS.map((tick) => (
        <span
          key={tick}
          className={`${styles.tick} ${tick === 20 ? styles.tickStrong : ''}`}
          style={{ left: `${barPercent(tick)}%` }}
        />
      ))}
      {weeklySets > 0 && (
        <span className={`${styles.fill} ${TONE_CLASS[tone]}`} style={{ width: `${barPercent(weeklySets)}%` }} />
      )}
    </div>
  )
}

function Pill({ tone, children }: { tone: Tone; children: string }) {
  return <span className={`${styles.pill} ${TONE_CLASS[tone]}`}>{children}</span>
}

/**
 * 균형 판정 한 줄. 판정 셋과 "한쪽이 0" 경우를 각각 다르게 적는다.
 *
 * <p>처방("당기기를 늘리세요")은 하지 않는다. 부족하다는 사실까지만 알린다 —
 * 무엇을 할지는 루틴 추천의 몫이다(분석 설계서 2.4).
 */
function balanceSentence(pair: Pair, threshold: number): string {
  const bigger = pair.biggerSide === pair.left.key ? pair.left : pair.right
  const smaller = bigger === pair.left ? pair.right : pair.left
  if (pair.verdict === 'INSUFFICIENT_DATA') return '두 쪽 모두 기록이 없어요'
  if (pair.smallerSideZero) return `${smaller.label} 기록이 없어 비율을 낼 수 없어요`
  return `${bigger.label}가 ${pair.ratio}배 · 기준 ${threshold}배${
    pair.verdict === 'IMBALANCED' ? ' 초과' : ' 이하'
  }`
}

/**
 * 약점 서브탭 (명세 8.2·8.3).
 *
 * <p>목업과 가장 다른 곳은 부위 목록이다. 목업은 상위 6종에 판정을 바로 붙였는데
 * ("팔 12세트 · 최적"), 그러면 이두 12세트가 삼두 0세트를 가린다(LOG-09). 서버도
 * 하위가 둘인 부위의 판정을 비워서 준다. 그래서 상위에는 세트 수와 요약 배지만,
 * 판정은 펼친 하위에 둔다.
 */
export function WeaknessView({ volume, balance }: WeaknessViewProps) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(() => initiallyOpen(volume))

  const toggle = (key: string) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })

  // 균형 근거에 쓸 부위 이름. 서버가 준 라벨을 그대로 모은다 — 화면에 따로 두면 어긋난다
  const muscleLabel = new Map<MuscleGroupKey, string>()
  volume.tiers.forEach((t) => t.children.forEach((c) => muscleLabel.set(c.key, c.label)))
  volume.displayOnly.forEach((d) => muscleLabel.set(d.key, d.label))
  const namesOf = (keys: MuscleGroupKey[]) => keys.map((k) => muscleLabel.get(k) ?? k).join(' + ')

  const shortHistory = volume.basisDays < volume.periodWeeks * 7
  const period = (
    <div className={styles.periodBlock}>
      <div className={styles.period}>
        {basisLabel(volume)} · {formatMonthDay(shortHistory ? volume.basisFrom : volume.periodFrom)} ~{' '}
        {formatMonthDay(volume.periodTo)}
      </div>
      <div className={styles.periodSub}>부위별 주당 평균 세트 · 워밍업 제외</div>
      {shortHistory && (
        <div className={styles.periodSub}>
          기록한 지 {volume.periodWeeks}주가 안 돼서, 기록을 시작한 뒤 지난 기간으로 나눠 계산해요 (최소 7일)
        </div>
      )}
    </div>
  )

  // 완료한 운동이 하나도 없으면 전 부위가 "부족"으로 뜬다. 틀린 말은 아니지만
  // 막 가입한 사람에게 빨간 막대 여섯 개를 보여줄 이유는 없다.
  if (volume.confidence.doneSessionCount === 0) {
    return (
      <>
        {period}
        <div className={styles.emptyCard}>
          <div className={styles.emptyTitle}>최근 {volume.periodWeeks}주 동안 완료한 운동이 없어요</div>
          <div className={styles.emptyBody}>운동을 기록하고 종료하면 부위별로 부족한 곳을 알려드려요</div>
          <button type="button" className={styles.emptyButton} onClick={() => navigate(paths.session)}>
            운동 기록하기
          </button>
        </div>
      </>
    )
  }

  return (
    <>
      {period}

      {volume.confidence.level === 'LOW' && volume.confidence.message && (
        <div className={styles.banner} role="note">
          <span className={styles.bannerIcon} aria-hidden="true">
            !
          </span>
          <span>
            {volume.confidence.message}
            <span className={styles.bannerSub}>
              {' '}
              완료한 운동이 {volume.confidence.threshold}회 이상이면 믿을 만해져요
            </span>
          </span>
        </div>
      )}

      <ul className={styles.tiers}>
        {volume.tiers.map((tier) => {
          const expanded = open.has(tier.key)
          const head = (
            <>
              <span className={styles.tierLabel}>{tier.label}</span>
              <span className={styles.tierRight}>
                <span className={styles.sets}>{tier.weeklySets}세트</span>
                {tier.verdict && tier.verdictLabel && (
                  <Pill tone={VERDICT_TONE[tier.verdict]}>{tier.verdictLabel}</Pill>
                )}
                {tier.summaryBadge && tier.summaryBadgeLabel && (
                  <Pill tone={BADGE_TONE[tier.summaryBadge]}>{tier.summaryBadgeLabel}</Pill>
                )}
                {tier.hasChildren && (
                  <span className={`${styles.chevron} ${expanded ? styles.chevronOpen : ''}`}>
                    <Chevron />
                  </span>
                )}
              </span>
            </>
          )

          return (
            <li key={tier.key} className={styles.tier}>
              {tier.hasChildren ? (
                <button
                  type="button"
                  className={styles.tierHead}
                  aria-expanded={expanded}
                  onClick={() => toggle(tier.key)}
                >
                  {head}
                </button>
              ) : (
                <div className={styles.tierHead}>{head}</div>
              )}

              {/* 하위가 둘이면 막대를 판정색으로 칠하지 않는다 — 초록이면 라벨 없이도 "최적"이라 읽힌다 */}
              <Bar
                weeklySets={tier.weeklySets}
                tone={tier.verdict ? VERDICT_TONE[tier.verdict] : 'neutral'}
              />

              {tier.key === 'SHOULDERS' && !volume.shoulderSplitResolved && (
                <div className={styles.tierNote}>어깨 앞·뒤 구분이 아직 안 된 종목이 있어 참고치예요</div>
              )}

              {tier.hasChildren && expanded && (
                <ul className={styles.children}>
                  {tier.children.map((child) => (
                    <li key={child.key} className={styles.child}>
                      <div className={styles.childHead}>
                        <span className={styles.childLabel}>{child.label}</span>
                        <span className={styles.tierRight}>
                          <span className={styles.sets}>{child.weeklySets}세트</span>
                          <Pill tone={VERDICT_TONE[child.verdict]}>{child.verdictLabel}</Pill>
                        </span>
                      </div>
                      <Bar weeklySets={child.weeklySets} tone={VERDICT_TONE[child.verdict]} thin />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ul>

      <div className={styles.scaleNote}>
        눈금은 주 4 · 10 · 20세트 — 10~20세트가 권장 구간이에요 (ACSM)
      </div>

      {volume.displayOnly.length > 0 && (
        <div className={styles.displayOnly}>
          <div className={styles.displayOnlyTitle}>판정 제외 · 참고</div>
          <div className={styles.displayOnlyRow}>
            {volume.displayOnly.map((d) => `${d.label} ${d.weeklySets}세트`).join(' · ')}
          </div>
          <div className={styles.displayOnlyHint}>
            따로 훈련하지 않는 경우가 많아 안 한 것을 약점으로 보지 않아요
          </div>
        </div>
      )}

      <section className={styles.balance}>
        <div className={styles.sectionTitle}>균형</div>
        {balance.pairs.map((pair) => {
          const max = Math.max(pair.left.weeklySets, pair.right.weeklySets, 1)
          return (
            <div key={pair.key} className={styles.pair}>
              <div className={styles.pairHead}>
                <span className={styles.pairTitle}>{pair.label}</span>
                <Pill tone={BALANCE_TONE[pair.verdict]}>{pair.verdictLabel}</Pill>
              </div>

              <div className={styles.diverge} aria-hidden="true">
                <div className={styles.half}>
                  <span
                    className={`${styles.sideBar} ${styles.sideLeft}`}
                    style={{ width: `${(pair.left.weeklySets / max) * 100}%` }}
                  />
                </div>
                <span className={styles.axis} />
                <div className={`${styles.half} ${styles.halfRight}`}>
                  <span
                    className={`${styles.sideBar} ${styles.sideRight}`}
                    style={{ width: `${(pair.right.weeklySets / max) * 100}%` }}
                  />
                </div>
              </div>

              <div className={styles.sideLabels}>
                <span>
                  <span className={`${styles.swatch} ${styles.sideLeft}`} />
                  {pair.left.label} {pair.left.weeklySets}세트
                </span>
                <span>
                  {pair.right.label} {pair.right.weeklySets}세트
                  <span className={`${styles.swatch} ${styles.sideRight}`} />
                </span>
              </div>

              <div className={styles.pairSentence}>{balanceSentence(pair, balance.ratioThreshold)}</div>
              <div className={styles.components}>
                {pair.left.label} = {namesOf(pair.left.components)}
                <br />
                {pair.right.label} = {namesOf(pair.right.components)}
              </div>
            </div>
          )
        })}
      </section>
    </>
  )
}
