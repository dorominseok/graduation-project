import { useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { isApiError } from '../../api'
import { useToast } from '../../components'
import { paths } from '../../app/paths'

/**
 * 그룹 화면들의 오류 처리. 토스트로 알리고, 404면 그룹 목록으로 보낸다.
 *
 * <p>그룹에서 내보내졌거나 그룹이 지워지면 서버가 404를 준다(멤버가 아니면 그룹이 있는지조차
 * 드러내지 않는다). 한 화면이 요청을 둘 보내면 둘 다 404가 오므로, 404는 <b>한 번만</b> 알리고
 * 한 번만 옮긴다. 상세로 보내지 않고 목록으로 보내는 것은 상세도 같은 404를 내기 때문이다.
 */
export function useGroupFail(): (err: unknown) => void {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const leaving = useRef(false)

  return useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      if (err.status === 404) {
        if (leaving.current) return
        leaving.current = true
        showToast({ message: err.message, tone: 'danger' })
        navigate(paths.group, { replace: true })
        return
      }
      showToast({ message: err.message, tone: 'danger' })
    },
    [navigate, showToast],
  )
}

/** 주소의 id가 숫자인가. "/group/abc"처럼 손으로 고친 주소는 요청 전에 걸러 목록으로 보낸다 */
export function isId(value: number): boolean {
  return Number.isInteger(value) && value > 0
}
