import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { analysisApi, isApiError } from '../../api'
import type { Balance, MuscleVolume } from '../../api'
import { ScreenHeader, SegmentedTabs, useToast } from '../../components'
import { RecoveryView } from '../analysis/RecoveryView'
import { WeaknessView } from '../analysis/WeaknessView'
import styles from '../analysis/analysis.module.css'

type View = 'weakness' | 'recovery'

const VIEWS = [
  { value: 'weakness', label: '약점' },
  { value: 'recovery', label: '회복' },
] as const

/**
 * 분석 탭.
 *
 * <p>서브탭을 주소에 둔다. 다른 화면에 갔다 돌아왔을 때 보던 서브탭이 그대로 열린다.
 * 회복 자리에는 예전에 통계(종목별 추정 1RM)가 있었다 — 같은 패널이 종목 상세에 있어서 뺐고,
 * 그때 주소({@code ?view=stats})로 들어오면 회복을 연다.
 *
 * <p>AI 해설 카드는 두지 않는다. 판정은 부족하다는 데까지만 알리고 처방하지 않는다.
 */
export function AnalysisScreen() {
  const { showToast } = useToast()
  const [params, setParams] = useSearchParams()
  const viewParam = params.get('view')
  const view: View = viewParam === 'recovery' || viewParam === 'stats' ? 'recovery' : 'weakness'

  const [data, setData] = useState<{ volume: MuscleVolume; balance: Balance } | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        // 두 판정은 같은 기간·같은 재료라 같이 읽는다. 하나만 새로우면 숫자가 서로 안 맞는다
        const [volume, balance] = await Promise.all([
          analysisApi.getMuscleVolume(),
          analysisApi.getBalance(),
        ])
        if (alive) setData({ volume, balance })
      } catch (err) {
        if (!isApiError(err)) throw err
        showToast({ message: err.message, tone: 'danger' })
        if (alive) setFailed(true)
      }
    }
    void load()
    return () => {
      alive = false
    }
  }, [showToast])

  const changeView = (next: View) => {
    const nextParams = new URLSearchParams(params)
    if (next === 'weakness') nextParams.delete('view')
    else nextParams.set('view', next)
    setParams(nextParams, { replace: true })
  }

  return (
    <>
      <ScreenHeader title="분석" hideBack />

      <div className={styles.page}>
        <SegmentedTabs items={VIEWS} value={view} onChange={changeView} aria-label="분석 보기" />

        {view === 'weakness' ? (
          data ? (
            <WeaknessView volume={data.volume} balance={data.balance} />
          ) : (
            <div className={styles.status}>{failed ? '분석을 불러오지 못했어요' : '불러오는 중…'}</div>
          )
        ) : (
          <RecoveryView />
        )}
      </div>
    </>
  )
}
