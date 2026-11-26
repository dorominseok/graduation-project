/**
 * 알림을 눌러 갈 곳 — 서비스워커(`sw.ts`)가 남기고 화면(`push.ts`)이 읽는다. 둘 다 이 파일만 가져온다.
 *
 * 열린 앱 창에 메시지를 보내는 것만으로는 모자랐다(2026-11-28 실제 휴대폰): 안드로이드에서 설치한 앱을 알림으로
 * 앞으로 가져올 때 브라우저에 따라 앱을 처음 화면(start_url)으로 새로 띄워, 보낸 메시지가 사라지고 홈이 열렸다.
 * 그래서 누르는 순간 갈 곳을 이 기기의 IndexedDB에 먼저 적어 두고, 화면은 보일 때·새로 뜰 때 읽어 간다.
 * 1분이 지난 기록은 버린다 — 나중에 앱을 열었는데 엉뚱한 화면으로 가지 않게.
 */

const DB_NAME = 'balancefit'
const STORE = 'pending-nav'
const KEY = 'target'
const FRESH_MS = 60_000

interface PendingNav {
  url: string
  at: number
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => request.result.createObjectStore(STORE)
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function run<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb()
  try {
    return await new Promise<T>((resolve, reject) => {
      const request = work(db.transaction(STORE, mode).objectStore(STORE))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  } finally {
    db.close()
  }
}

/** 알림을 누른 순간(서비스워커) */
export async function savePendingNav(url: string): Promise<void> {
  await run('readwrite', (store) => store.put({ url, at: Date.now() } satisfies PendingNav, KEY))
}

/** 화면이 보일 때·새로 뜰 때. 읽으면 지운다. 없거나 오래됐으면 null */
export async function takePendingNav(): Promise<string | null> {
  const saved = await run<PendingNav | undefined>('readonly', (store) => store.get(KEY))
  if (!saved) return null
  await clearPendingNav()
  return Date.now() - saved.at <= FRESH_MS ? saved.url : null
}

/** 메시지로 이미 옮겼을 때 — 보일 때 또 옮기지 않게 */
export async function clearPendingNav(): Promise<void> {
  await run('readwrite', (store) => store.delete(KEY))
}
