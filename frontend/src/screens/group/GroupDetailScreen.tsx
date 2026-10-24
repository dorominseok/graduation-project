import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { BottomSheet, Chevron, ScreenHeader, useToast } from '../../components'
import { groupApi, isApiError } from '../../api'
import type { GroupDetail, MemberActivity } from '../../api'
import { paths } from '../../app/paths'
import { formatDuration, fromDateKey, toDateKey } from '../../app/dates'
import { CheerButton } from './CheerButton'
import { GroupNameForm } from './GroupNameForm'
import { isId, useGroupFail } from './useGroupFail'
import styles from './group.module.css'
import formStyles from '../full/settings.module.css'

type Sheet = 'menu' | 'invite' | 'rename' | 'members' | 'kick' | 'regenerate' | 'leave' | 'delete' | null


/** 이번 주 월~일 날짜 일곱 개 */
function weekOf(weekStart: string): string[] {
  const start = fromDateKey(weekStart)
  return Array.from({ length: 7 }, (_, i) =>
    toDateKey(new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)),
  )
}

/**
 * 그룹 상세 — 멤버별 오늘 운동 여부와 이번 주(월~일) 횟수·시간.
 *
 * <p>"오늘 운동했나"는 손으로 체크하지 않고, 오늘 날짜로 완료한 기록이 있으면 서버가 준다.
 * 멤버를 누르면 그 사람의 기록을 읽기 전용으로 본다(메모는 빠진다). 순서는 들어온 순서다 —
 * 경쟁이 아니라 서로 보이는 것이 목적이다.
 */
export function GroupDetailScreen() {
  const groupId = Number(useParams().groupId)
  if (!isId(groupId)) return <Navigate to={paths.group} replace />
  // 다른 그룹으로 바로 옮겨 가면 같은 화면이 재사용된다. 열린 시트·이전 그룹이 남지 않게 새로 그린다
  return <GroupDetailView key={groupId} groupId={groupId} />
}

function GroupDetailView({ groupId }: { groupId: number }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { showToast } = useToast()

  const [group, setGroup] = useState<GroupDetail | null>(null)
  // 방금 만든 그룹이면 초대부터 연다
  const openInvite = (location.state as { invite?: boolean } | null)?.invite === true
  const [sheet, setSheet] = useState<Sheet>(openInvite ? 'invite' : null)

  // 표시는 한 번만 쓴다. 남겨 두면 새로고침할 때마다 초대 시트가 다시 열린다
  useEffect(() => {
    if (openInvite) navigate(location.pathname, { replace: true, state: null })
  }, [openInvite, navigate, location.pathname])
  const [kickTarget, setKickTarget] = useState<MemberActivity | null>(null)

  // 내보내졌거나 그룹이 지워졌으면(404) 목록으로 간다
  const fail = useGroupFail()

  // 내보내기처럼 멤버가 바뀌는 일 뒤에 올려서 다시 읽는다
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const run = async () => {
      try {
        setGroup(await groupApi.getGroup(groupId))
      } catch (err) {
        fail(err)
      }
    }
    void run()
  }, [groupId, fail, reloadKey])

  const close = () => setSheet(null)

  if (!group) {
    return <ScreenHeader title="그룹" onBack={() => navigate(paths.group)} />
  }

  const doneCount = group.members.filter((m) => m.today.status === 'DONE').length
  const weekDays = weekOf(group.weekStart)
  const others = group.members.filter((m) => !m.isMe)

  return (
    <>
      <ScreenHeader
        title={group.name}
        onBack={() => navigate(paths.group)}
        trailing={
          <button type="button" className={styles.iconButton} onClick={() => setSheet('menu')} aria-label="그룹 메뉴">
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="5" cy="12" r="2" fill="currentColor" />
              <circle cx="12" cy="12" r="2" fill="currentColor" />
              <circle cx="19" cy="12" r="2" fill="currentColor" />
            </svg>
          </button>
        }
      />

      <div className={styles.page}>
        <div className={styles.card}>
          <div className={styles.cardLabel}>오늘</div>
          <div className={styles.bigLine}>
            {group.members.length}명 중 {doneCount}명이 운동했어요
          </div>
        </div>

        {others.length === 0 && (
          <div className={styles.empty}>
            <div className={styles.emptyTitle}>아직 혼자예요</div>
            <div className={styles.emptyText}>초대 링크를 보내 친구를 불러 보세요.</div>
            <button type="button" className={styles.primary} onClick={() => setSheet('invite')}>
              친구 초대하기
            </button>
          </div>
        )}

        <div className={styles.memberList}>
          {group.members.map((member) => (
            <MemberRow key={member.userId} groupId={group.id} member={member} today={group.today} weekDays={weekDays} />
          ))}
        </div>

        {others.length > 0 && (
          <button type="button" className={styles.secondary} onClick={() => setSheet('invite')}>
            친구 초대하기
          </button>
        )}
      </div>

      {/* ── 메뉴 */}
      <BottomSheet open={sheet === 'menu'} onClose={close} title="그룹">
        <div className={formStyles.card}>
          <button type="button" className={formStyles.row} onClick={() => setSheet('invite')}>
            <span className={formStyles.rowLabel}>친구 초대하기</span>
            <Chevron />
          </button>
          {group.isOwner && (
            <>
              <button type="button" className={formStyles.row} onClick={() => setSheet('rename')}>
                <span className={formStyles.rowLabel}>그룹 이름 바꾸기</span>
                <Chevron />
              </button>
              {others.length > 0 && (
                <button type="button" className={formStyles.row} onClick={() => setSheet('members')}>
                  <span className={formStyles.rowLabel}>멤버 관리</span>
                  <Chevron />
                </button>
              )}
              <button type="button" className={formStyles.row} onClick={() => setSheet('regenerate')}>
                <span className={formStyles.rowLabel}>초대코드 새로 만들기</span>
                <Chevron />
              </button>
            </>
          )}
        </div>
        <div className={`${formStyles.card} ${styles.menuGap}`}>
          <button type="button" className={`${formStyles.row} ${formStyles.rowDanger}`} onClick={() => setSheet('leave')}>
            <span className={formStyles.rowLabel}>그룹 나가기</span>
          </button>
          {group.isOwner && (
            <button type="button" className={`${formStyles.row} ${formStyles.rowDanger}`} onClick={() => setSheet('delete')}>
              <span className={formStyles.rowLabel}>그룹 삭제</span>
            </button>
          )}
        </div>
      </BottomSheet>

      {/* ── 초대 */}
      <BottomSheet open={sheet === 'invite'} onClose={close} title="친구 초대하기">
        <InviteSheet name={group.name} code={group.inviteCode} />
      </BottomSheet>

      {/* ── 방장 */}
      <BottomSheet open={sheet === 'rename'} onClose={close} title="그룹 이름 바꾸기">
        <GroupNameForm
          initial={group.name}
          submitLabel="저장"
          savingLabel="저장 중…"
          onSubmit={async (name) => {
            setGroup(await groupApi.renameGroup(group.id, name))
            close()
          }}
        />
      </BottomSheet>

      <BottomSheet open={sheet === 'members'} onClose={close} title="멤버 관리">
        <div className={formStyles.card}>
          {others.map((member) => (
            <div key={member.userId} className={`${formStyles.row} ${formStyles.rowStatic}`}>
              <span className={formStyles.rowLabel}>{member.nickname}</span>
              <button
                type="button"
                className={styles.cheer}
                onClick={() => {
                  setKickTarget(member)
                  setSheet('kick')
                }}
              >
                내보내기
              </button>
            </div>
          ))}
        </div>
      </BottomSheet>

      <BottomSheet open={sheet === 'kick'} onClose={close} title="멤버 내보내기">
        {kickTarget && (
          <Confirm
            message={`${kickTarget.nickname}님을 그룹에서 내보낼까요? 내보내도 그 사람의 운동 기록은 지워지지 않고, 초대코드가 있으면 다시 들어올 수 있어요.`}
            action="내보내기"
            onConfirm={async () => {
              await groupApi.removeMember(group.id, kickTarget.userId)
              close()
              showToast({ message: `${kickTarget.nickname}님을 내보냈어요` })
              setReloadKey((k) => k + 1)
            }}
          />
        )}
      </BottomSheet>

      <BottomSheet open={sheet === 'regenerate'} onClose={close} title="초대코드 새로 만들기">
        <Confirm
          message="지금 코드와 링크로는 더 들어올 수 없어요. 이미 들어온 멤버는 그대로예요. 링크가 엉뚱한 곳에 퍼졌을 때 쓰세요."
          action="새로 만들기"
          danger={false}
          onConfirm={async () => {
            const { inviteCode } = await groupApi.regenerateInviteCode(group.id)
            setGroup({ ...group, inviteCode })
            setSheet('invite')
          }}
        />
      </BottomSheet>

      {/* ── 나가기·삭제 */}
      <BottomSheet open={sheet === 'leave'} onClose={close} title="그룹 나가기">
        <Confirm
          message={leaveMessage(group)}
          action="나가기"
          onConfirm={async () => {
            await groupApi.leaveGroup(group.id)
            showToast({ message: '그룹에서 나왔어요' })
            navigate(paths.group, { replace: true })
          }}
        />
      </BottomSheet>

      <BottomSheet open={sheet === 'delete'} onClose={close} title="그룹 삭제">
        <Confirm
          message="그룹을 삭제하면 멤버 모두 이 그룹을 볼 수 없어요. 각자의 운동 기록은 그대로 남아요."
          action="삭제하기"
          onConfirm={async () => {
            await groupApi.deleteGroup(group.id)
            showToast({ message: '그룹을 삭제했어요' })
            navigate(paths.group, { replace: true })
          }}
        />
      </BottomSheet>
    </>
  )
}

/** 방장이 나가면 누가 방장이 되는지 미리 알려준다. 서버와 같은 규칙 — 가장 먼저 들어온 멤버다 */
function leaveMessage(group: GroupDetail): string {
  if (!group.isOwner) return '나가도 내 운동 기록은 그대로예요. 초대코드가 있으면 다시 들어올 수 있어요.'
  const successor = group.members.find((m) => !m.isMe)
  if (!successor) return '혼자 있는 그룹이라 나가면 그룹이 삭제돼요.'
  return `방장은 ${successor.nickname}님에게 넘어가요. 나가도 내 운동 기록은 그대로예요.`
}

function MemberRow({
  groupId,
  member,
  today,
  weekDays,
}: {
  groupId: number
  member: MemberActivity
  today: string
  weekDays: string[]
}) {
  const worked = new Set(member.week.workoutDates)
  const { week } = member

  return (
    <div className={styles.member}>
      {/* 이름과 이번 주 요약을 같이 넘겨 멤버 화면이 그룹을 다시 읽지 않게 한다 */}
      <Link to={paths.groupMember(groupId, member.userId)} state={{ member }} className={styles.memberLink}>
        <span className={`${styles.avatar} ${member.isMe ? styles.avatarMe : ''}`} aria-hidden="true">
          {member.nickname.slice(0, 1)}
        </span>
        <span className={styles.memberMain}>
          <span className={styles.nameRow}>
            <span className={styles.nickname}>{member.nickname}</span>
            {member.isMe && <span className={styles.tag}>나</span>}
            {member.isOwner && <span className={styles.tag}>방장</span>}
          </span>
          <span className={styles.weekLine}>
            <span className={styles.dots} aria-label={`이번 주 ${worked.size}일 운동`}>
              {weekDays.map((day) => (
                <span
                  key={day}
                  className={[styles.dot, worked.has(day) ? styles.dotOn : '', day === today ? styles.dotToday : ''].join(' ')}
                />
              ))}
            </span>
            <span>
              {week.dayCount}회
              {week.totalDurationSec > 0 && ` · ${formatDuration(week.totalDurationSec)}`}
            </span>
          </span>
        </span>
      </Link>

      {/* 오늘 운동한 사람만 표시와 따봉이 붙는다. 아직 안 했으면 비워 둔다 */}
      {member.today.status === 'DONE' && member.today.sessionId != null && (
        <span className={styles.side}>
          <span className={`${styles.status} ${styles.statusDone}`}>오늘 운동함</span>
          <CheerButton
            key={`${member.today.sessionId}-${member.today.cheerCount}-${member.today.cheeredByMe}`}
            groupId={groupId}
            sessionId={member.today.sessionId}
            initial={{ cheerCount: member.today.cheerCount, cheeredByMe: member.today.cheeredByMe }}
            mine={member.isMe}
          />
        </span>
      )}
    </div>
  )
}

/**
 * 링크와 코드. 링크를 받은 친구가 누르면 참여 화면이 바로 열린다. 휴대폰에서는 공유 창으로
 * 카톡 등에 바로 보내고, 공유 창이 없는 곳(PC 일부)에서는 링크를 복사한다.
 */
function InviteSheet({ name, code }: { name: string; code: string }) {
  const { showToast } = useToast()
  const link = `${window.location.origin}${paths.join(code)}`

  const copy = async (text: string, done: string) => {
    try {
      await navigator.clipboard.writeText(text)
      showToast({ message: done })
    } catch {
      showToast({ message: '복사하지 못했어요. 길게 눌러 직접 복사해주세요.', tone: 'danger' })
    }
  }

  const share = async () => {
    const text = `밸런스핏 '${name}' 그룹에 초대해요. 링크를 누르면 바로 참여할 수 있어요.`
    if (navigator.share) {
      try {
        await navigator.share({ title: '밸런스핏 그룹 초대', text, url: link })
        return
      } catch (err) {
        // 공유 창을 닫은 것이면 할 일이 없다. 그 밖의 실패(공유를 막아 둔 PC 브라우저 등)는 복사로 넘어간다
        if (err instanceof DOMException && err.name === 'AbortError') return
      }
    }
    await copy(`${text}\n${link}`, '초대 링크를 복사했어요')
  }

  return (
    <div className={formStyles.sheetBody}>
      <div className={styles.codeBox}>
        <span className={styles.code}>{code}</span>
        <span className={styles.codeHint}>초대코드</span>
      </div>
      <p className={formStyles.hint}>
        링크를 받은 친구가 누르면 바로 참여 화면이 열려요. 링크를 쓸 수 없으면 그룹 탭의 "초대코드로
        참여"에 코드를 입력하면 돼요.
      </p>
      <button type="button" className={formStyles.submit} onClick={() => void share()}>
        초대 링크 보내기
      </button>
      <div className={styles.actions}>
        <button type="button" className={styles.secondary} onClick={() => void copy(link, '링크를 복사했어요')}>
          링크 복사
        </button>
        <button type="button" className={styles.secondary} onClick={() => void copy(code, '코드를 복사했어요')}>
          코드 복사
        </button>
      </div>
    </div>
  )
}

/** 되돌리기 어려운 일 앞의 확인. 실패하면 시트를 닫지 않고 토스트로 알린다 */
function Confirm({
  message,
  action,
  danger = true,
  onConfirm,
}: {
  message: string
  action: string
  danger?: boolean
  onConfirm: () => Promise<void>
}) {
  const { showToast } = useToast()
  const [busy, setBusy] = useState(false)

  const run = async () => {
    if (busy) return
    setBusy(true)
    try {
      await onConfirm()
    } catch (err) {
      if (!isApiError(err)) throw err
      showToast({ message: err.message, tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={formStyles.sheetBody}>
      <p className={formStyles.hint}>{message}</p>
      <button
        type="button"
        className={danger ? formStyles.danger : formStyles.submit}
        onClick={() => void run()}
        disabled={busy}
      >
        {busy ? '처리 중…' : action}
      </button>
    </div>
  )
}
