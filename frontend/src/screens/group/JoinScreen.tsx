import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ScreenHeader, useToast } from '../../components'
import { groupApi, isApiError } from '../../api'
import type { InvitePreview } from '../../api'
import { paths } from '../../app/paths'
import styles from './group.module.css'

/**
 * 초대 링크(/join/코드)로 들어온 화면 (UC-05-02, LOG-38).
 *
 * <p>바로 참여시키지 않고 어떤 그룹인지와 <b>무엇이 공개되는지</b>를 먼저 보여준다 — 참여하면
 * 멤버들에게 내 운동 기록이 보이므로, 그걸 알고 누르게 한다. 그룹 탭에서 코드를 입력해도
 * 이 화면으로 온다.
 *
 * <p>로그인 전에 링크를 누르면 로그인(또는 가입) 뒤 이 화면으로 돌아온다(RequireAuth).
 */
export function JoinScreen() {
  const { code = '' } = useParams()
  // 앱 안에서 다른 초대 링크로 옮겨 가도 같은 화면이 재사용된다. 코드가 바뀌면 처음부터 다시 그린다
  return <JoinView key={code} code={code} />
}

function JoinView({ code }: { code: string }) {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [preview, setPreview] = useState<InvitePreview | null>(null)
  const [notFound, setNotFound] = useState(false)
  // 404가 아닌 실패(약한 신호·서버 오류). 빈 화면으로 두지 않고 다시 시도할 수 있게 한다
  const [loadFailed, setLoadFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [joining, setJoining] = useState(false)

  useEffect(() => {
    const run = async () => {
      try {
        setPreview(await groupApi.previewInvite(code))
        setLoadFailed(false)
      } catch (err) {
        if (!isApiError(err)) throw err
        if (err.status === 404) setNotFound(true)
        else setLoadFailed(true)
      }
    }
    void run()
  }, [code, attempt])

  const join = async () => {
    if (joining) return
    setJoining(true)
    try {
      const result = await groupApi.joinGroup(code)
      if (!result.alreadyMember) showToast({ message: `'${result.name}' 그룹에 참여했어요` })
      navigate(paths.groupDetail(result.groupId), { replace: true })
    } catch (err) {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setJoining(false)
    }
  }

  return (
    <>
      <ScreenHeader title="그룹 초대" onBack={() => navigate(paths.group)} />

      <div className={styles.page}>
        {notFound && (
          <div className={styles.empty}>
            <div className={styles.emptyTitle}>초대코드를 찾을 수 없어요</div>
            <div className={styles.emptyText}>
              코드가 새로 바뀌었거나 그룹이 삭제됐을 수 있어요. 초대한 친구에게 새 링크를 받아 주세요.
            </div>
            <button type="button" className={styles.secondary} onClick={() => navigate(paths.group)}>
              그룹 탭으로
            </button>
          </div>
        )}

        {loadFailed && !preview && (
          <div className={styles.empty}>
            <div className={styles.emptyTitle}>초대 정보를 불러오지 못했어요</div>
            <div className={styles.emptyText}>네트워크 상태를 확인하고 다시 시도해주세요.</div>
            <button type="button" className={styles.secondary} onClick={() => setAttempt((n) => n + 1)}>
              다시 시도
            </button>
          </div>
        )}

        {preview && (
          <>
            <div className={styles.card}>
              <div className={styles.cardLabel}>초대받은 그룹</div>
              <div className={styles.bigLine}>{preview.name}</div>
              <div className={styles.subLine}>
                {preview.ownerNickname ? `${preview.ownerNickname}님의 그룹 · ` : ''}멤버 {preview.memberCount}명
              </div>
            </div>

            {preview.alreadyMember ? (
              <>
                <div className={styles.subLine}>이미 참여한 그룹이에요.</div>
                <button
                  type="button"
                  className={styles.primary}
                  onClick={() => navigate(paths.groupDetail(preview.groupId), { replace: true })}
                >
                  그룹 보기
                </button>
              </>
            ) : preview.full ? (
              <div className={styles.notice}>인원이 가득 차서 참여할 수 없어요. (최대 20명)</div>
            ) : (
              <>
                <div className={styles.notice}>
                  참여하면 이 그룹 멤버에게 내 운동 기록(날짜·종목·세트·운동 시간)이 보이고, 오늘 운동했는지가
                  표시돼요. 기록에 적은 메모는 보이지 않아요.{' '}
                  <Link to={paths.privacy}>개인정보처리방침</Link>
                </div>
                <button type="button" className={styles.primary} onClick={() => void join()} disabled={joining}>
                  {joining ? '참여하는 중…' : '참여하기'}
                </button>
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}
