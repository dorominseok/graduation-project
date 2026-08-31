import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  BottomSheet,
  NumberPad,
  ScreenHeader,
  SegmentedTabs,
  Stepper,
  useToast,
} from '../components'

/**
 * 공용 컴포넌트 확인용 화면. 제품 화면이 아니다.
 * 9월에 실제 화면을 붙이면서 필요 없어지면 지운다.
 */
export function DevComponentsScreen() {
  const { showToast } = useToast()
  const [weight, setWeight] = useState(60)
  const [reps, setReps] = useState(10)
  const [tab, setTab] = useState<'recent' | 'byPart' | 'favorite'>('recent')
  // ?open=numpad|sheet 로 오버레이를 연 상태로 띄울 수 있다(확인용).
  const [searchParams] = useSearchParams()
  const preset = searchParams.get('open')
  const [padOpen, setPadOpen] = useState(preset === 'numpad')
  const [sheetOpen, setSheetOpen] = useState(preset === 'sheet')

  // ?open=toast 확인용. StrictMode의 이중 실행으로 두 번 뜨지 않게 막는다.
  const toastShown = useRef(false)
  useEffect(() => {
    if (preset !== 'toast' || toastShown.current) return
    toastShown.current = true
    showToast({
      message: '세트를 삭제했어요',
      action: { label: '되돌리기', onClick: () => {} },
      durationMs: 60000,
    })
  }, [preset, showToast])

  return (
    <>
      <ScreenHeader title="공용 컴포넌트" hideBack />
      <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <section>
          <Label>스테퍼 — 중량 ±2.5kg</Label>
          <Stepper value={weight} onChange={setWeight} step={2.5} precision={1} unit="kg" />
        </section>

        <section>
          <Label>스테퍼 — 횟수 ±1회</Label>
          <Stepper value={reps} onChange={setReps} step={1} unit="회" />
        </section>

        <section>
          <Label>세그먼트 탭</Label>
          <SegmentedTabs
            value={tab}
            onChange={setTab}
            items={[
              { value: 'recent', label: '최근 사용' },
              { value: 'byPart', label: '부위별' },
              { value: 'favorite', label: '즐겨찾기' },
            ]}
          />
        </section>

        <section>
          <Label>넘버패드 · 바텀시트 · 토스트</Label>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <DevButton onClick={() => setPadOpen(true)}>넘버패드 열기</DevButton>
            <DevButton onClick={() => setSheetOpen(true)}>바텀시트 열기</DevButton>
            <DevButton
              onClick={() =>
                showToast({
                  message: '세트를 삭제했어요',
                  action: { label: '되돌리기', onClick: () => showToast({ message: '되돌렸어요' }) },
                })
              }
            >
              토스트 띄우기
            </DevButton>
          </div>
        </section>
      </div>

      <NumberPad
        open={padOpen}
        onClose={() => setPadOpen(false)}
        onApply={setWeight}
        label="벤치프레스 · 중량"
        initialValue={weight}
        unit="kg"
        allowDecimal
      />

      <BottomSheet open={sheetOpen} onClose={() => setSheetOpen(false)} title="바텀시트">
        <div style={{ paddingBottom: 8, color: 'var(--text-secondary)' }}>
          스크림을 누르거나 Esc로 닫힌다.
        </div>
      </BottomSheet>
    </>
  )
}

function Label({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontSize: 'var(--font-2xs)',
        fontWeight: 700,
        color: 'var(--text-secondary)',
        marginBottom: 8,
      }}
    >
      {children}
    </div>
  )
}

function DevButton({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        height: 44,
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--card-border)',
        background: 'var(--card-bg)',
        color: 'var(--text-primary)',
        fontSize: 'var(--font-sm)',
        fontWeight: 700,
      }}
    >
      {children}
    </button>
  )
}
