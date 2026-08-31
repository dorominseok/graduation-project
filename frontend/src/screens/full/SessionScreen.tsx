import { Placeholder, ScreenHeader } from '../../components'

export function SessionScreen() {
  return (
    <>
      <ScreenHeader title="세션 기록" />
      <Placeholder title="세션 기록" note="세트 입력은 스테퍼·넘버패드 컴포넌트로 붙인다. 세션 생성은 첫 세트 완료 체크 시점이다." />
    </>
  )
}
