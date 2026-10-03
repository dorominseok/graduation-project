import { useNavigate, useSearchParams } from 'react-router-dom'
import { ScreenHeader } from '../../components'
import type { BrowseCategory, Exercise } from '../../api'
import { paths } from '../../app/paths'
import { ExercisePicker } from './ExercisePicker'
import type { PickerStep } from './ExercisePicker'

/**
 * 종목 둘러보기 (명세 5.2).
 *
 * 고르는 동작 자체는 ExercisePicker가 하고, 여기서는 단계를 URL에 실어
 * 뒤로가기가 한 단계씩 거슬러 올라가게 한다.
 *
 * 기록 중에 종목을 고를 때는 이 화면이 아니라 기록 화면의 시트를 쓴다 —
 * 화면을 옮기면 아직 체크하지 않은 종목이 사라지기 때문이다.
 */
export function ExerciseListScreen() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const step: PickerStep = {
    category: (params.get('category') as BrowseCategory | null) ?? null,
    group: params.get('group'),
  }

  const handleStepChange = (next: PickerStep) => {
    const query: Record<string, string> = {}
    if (next.category) query.category = next.category
    if (next.group) query.group = next.group
    setParams(query)
  }

  return (
    <>
      {/*
        뒤로가기는 히스토리를 그대로 되돌린다. 단계를 옮길 때마다 URL이 바뀌므로
        한 번 누르면 한 단계씩 올라가고, 1단계에서 누르면 들어온 화면으로 나간다.
      */}
      <ScreenHeader title="운동 종목" />
      <ExercisePicker
        step={step}
        onStepChange={handleStepChange}
        onPick={(exercise: Exercise) => navigate(paths.exerciseDetail(exercise.id))}
        onInfo={(exercise: Exercise) => navigate(paths.exerciseDetail(exercise.id))}
      />
    </>
  )
}
