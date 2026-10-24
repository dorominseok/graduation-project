import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { analysisApi, isApiError } from '../../api'
import type { Balance, MuscleVolume } from '../../api'
import { ScreenHeader, SegmentedTabs, useToast } from '../../components'
import { StatsView } from '../analysis/StatsView'
import { WeaknessView } from '../analysis/WeaknessView'
import styles from '../analysis/analysis.module.css'

type View = 'weakness' | 'stats'

const VIEWS = [
  { value: 'weakness', label: '약점' },
  { value: 'stats', label: '통계' },
] as const

/**
 * 분석 탭 (명세 7·8장).
 *
 * <p>서브탭과 고른 종목을 주소에 둔다. 통계에서 종목 목록으로 갔다 돌아왔을 때
 * 약점 탭으로 튕겨 나가지 않게 하려는 것이다.
 *
 * <p>AI 해설 카드는 두지 않는다. 판정은 부족하다는 데까지만 알리고 처방하지 않는다
 * (분석 설계서 2.4).
 */
export function AnalysisScreen() {
  const { showToast } = useToast()
  const [params, setParams] = useSearchParams()
  const view: View = params.get('view') === 'stats' ? 'stats' : 'weakness'
  const exerciseParam = params.get('exercise')

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

  const selectExercise = (exerciseId: number) => {
    const nextParams = new URLSearchParams(params)
    nextParams.set('exercise', String(exerciseId))
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
          <StatsView
            selectedId={exerciseParam ? Number(exerciseParam) : null}
            onSelect={selectExercise}
          />
        )}
      </div>
    </>
  )
}
