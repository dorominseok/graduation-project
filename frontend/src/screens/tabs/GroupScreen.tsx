import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BottomSheet, ScreenHeader, useToast } from '../../components'
import { groupApi, isApiError } from '../../api'
import type { GroupSummary } from '../../api'
import { paths } from '../../app/paths'
import { GroupCard } from '../group/GroupCard'
import { GroupNameForm } from '../group/GroupNameForm'
import styles from '../group/group.module.css'
import formStyles from '../full/settings.module.css'

type Sheet = 'create' | 'join' | null

/**
 * 그룹 탭 — 내 그룹 목록 (명세 10장, LOG-38).
 *
 * <p>그룹이 없으면 무엇을 하는 곳인지부터 알려준다. 만들기와 코드로 참여하기는 목록이
 * 있어도 아래에 남긴다 — 그룹은 여러 개 들어갈 수 있다.
 */
export function GroupScreen() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [groups, setGroups] = useState<GroupSummary[] | null>(null)
  const [sheet, setSheet] = useState<Sheet>(null)

  const fail = useCallback(
    (err: unknown) => {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    },
    [showToast],
  )

  useEffect(() => {
    const run = async () => {
      try {
        setGroups(await groupApi.getMyGroups())
      } catch (err) {
        fail(err)
      }
    }
    void run()
  }, [fail])

  return (
    <>
      <ScreenHeader title="그룹" hideBack />

      <div className={styles.page}>
        {groups && groups.length === 0 && (
          <div className={styles.empty}>
            <div className={styles.emptyTitle}>함께 운동할 친구를 모아 보세요</div>
            <div className={styles.emptyText}>
              그룹을 만들고 친구에게 초대 링크를 보내면, 서로 오늘 운동했는지와 이번 주에 몇 번
              했는지 볼 수 있어요. 친구의 운동에 응원도 보낼 수 있어요.
            </div>
          </div>
        )}

        {groups?.map((group) => <GroupCard key={group.id} group={group} />)}

        {groups && (
          <div className={styles.actions}>
            <button type="button" className={styles.primary} onClick={() => setSheet('create')}>
              그룹 만들기
            </button>
            <button type="button" className={styles.secondary} onClick={() => setSheet('join')}>
              초대코드로 참여
            </button>
          </div>
        )}
      </div>

      <BottomSheet open={sheet === 'create'} onClose={() => setSheet(null)} title="그룹 만들기">
        <GroupNameForm
          submitLabel="만들기"
          savingLabel="만드는 중…"
          hint="만든 사람이 방장이 돼요. 이름은 나중에 바꿀 수 있어요."
          onSubmit={async (name) => {
            const group = await groupApi.createGroup(name)
            setSheet(null)
            // 만들고 나면 할 일은 친구를 부르는 것이라, 상세에서 초대 시트를 바로 연다
            navigate(paths.groupDetail(group.id), { state: { invite: true } })
          }}
        />
      </BottomSheet>

      <BottomSheet open={sheet === 'join'} onClose={() => setSheet(null)} title="초대코드로 참여">
        <JoinByCodeForm
          onSubmit={(code) => {
            setSheet(null)
            // 링크로 들어온 것과 같은 화면으로 간다 — 참여 전에 어떤 그룹인지 보여준다
            navigate(paths.join(code))
          }}
        />
      </BottomSheet>
    </>
  )
}

function JoinByCodeForm({ onSubmit }: { onSubmit: (code: string) => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string>()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    // 받은 링크를 통째로 붙여 넣어도 코드만 꺼낸다. 아니면 공백·하이픈을 걷어 낸다.
    // 서버도 다듬어 받지만, 주소(/join/코드)에 넣을 값이라 여기서 글자를 확인한다
    const fromLink = code.match(/\/join\/([A-Z0-9]{6})/i)
    const cleaned = (fromLink ? fromLink[1] : code.replace(/[\s-]/g, '')).toUpperCase()
    if (!/^[A-Z0-9]{6}$/.test(cleaned)) {
      setError('초대코드는 영문·숫자 6자리예요')
      return
    }
    onSubmit(cleaned)
  }

  return (
    <form className={formStyles.sheetBody} onSubmit={handleSubmit} noValidate>
      <div>
        <label className={formStyles.label} htmlFor="invite-code">초대코드</label>
        <input
          id="invite-code"
          className={`${formStyles.input} ${error ? formStyles.inputError : ''}`}
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
        />
        {error && <div className={formStyles.error}>{error}</div>}
      </div>
      <p className={formStyles.hint}>친구에게 받은 링크를 누르면 코드를 입력하지 않아도 돼요. 링크를 여기에 붙여 넣어도 돼요.</p>
      <button type="submit" className={formStyles.submit}>
        다음
      </button>
    </form>
  )
}
