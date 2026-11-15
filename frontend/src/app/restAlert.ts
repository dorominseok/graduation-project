/**
 * 휴식이 끝났을 때 소리·진동으로 알리고, 쉬는 동안 화면이 저절로 꺼지지 않게 잡아 둔다.
 * 설정 › 휴식 타이머에서 하나씩 끄고, 이 기기(브라우저)에만 남는다.
 *
 * <p>모두 화면이 켜져 있고 앱을 보고 있을 때만 된다. 화면이 꺼지거나 다른 앱으로 가면 브라우저가
 * 앱 코드를 멈추므로 끝나는 순간에 울릴 수 없다 — 그래서 화면 켜 두기와 묶는다.
 * 서버 푸시로 알리지 않는 이유는 「알림_설계서」 1장.
 */

export interface RestAlertSettings {
  sound: boolean
  vibrate: boolean
  /** 쉬는 동안 화면 켜 두기 */
  keepScreenOn: boolean
}

const STORAGE_KEY = 'balancefit.restAlert'
const DEFAULTS: RestAlertSettings = { sound: true, vibrate: true, keepScreenOn: true }

export function getRestAlert(): RestAlertSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULTS
    const saved = JSON.parse(raw) as Partial<RestAlertSettings>
    return {
      sound: saved.sound ?? DEFAULTS.sound,
      vibrate: saved.vibrate ?? DEFAULTS.vibrate,
      keepScreenOn: saved.keepScreenOn ?? DEFAULTS.keepScreenOn,
    }
  } catch {
    return DEFAULTS
  }
}

export function setRestAlert(settings: RestAlertSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  } catch {
    // 저장하지 못하면 다음에 기본값(모두 켬)으로 돌아갈 뿐이다
  }
}

/** 아이폰 사파리에는 진동이 없다. 없는 기기에서는 설정 줄도 숨긴다 */
export function canVibrate(): boolean {
  return typeof navigator.vibrate === 'function'
}

export function canKeepScreenOn(): boolean {
  return 'wakeLock' in navigator
}

// ---- 소리 ----

let audio: AudioContext | null = null
let sleepTimer: number | undefined
/** 쉬는 중이라 소리 장치를 깨워 둬야 한다 */
let holding = false

/**
 * 소리 낼 준비. 아이폰 사파리는 사용자가 화면을 누르는 그 순간에만 소리를 켤 수 있어서,
 * 휴식을 시작하는 세트 체크와 휴식 띠 버튼에서 먼저 부른다. 끝날 때는 누르는 순간이 아니다.
 * 깨워 놓고 휴식이 시작되지 않으면(워밍업, 저장 실패) 잠시 뒤 다시 재운다.
 */
export function primeSound(): void {
  if (!getRestAlert().sound) return
  try {
    audio ??= new AudioContext()
    if (audio.state !== 'running') void audio.resume()
  } catch {
    audio = null
    return
  }
  if (!holding) sleepSound(10_000)
}

/** 휴식이 시작됐다. 끝날 때까지 소리 장치를 재우지 않는다 */
export function holdSound(): void {
  holding = true
  window.clearTimeout(sleepTimer)
}

/**
 * 소리 장치를 쉬게 한다. 켜 둔 채면 소리를 안 내도 배터리를 쓴다.
 * 끝 소리가 다 울릴 시간을 두고 멈춘다
 */
export function sleepSound(delayMs = 2000): void {
  holding = false
  window.clearTimeout(sleepTimer)
  sleepTimer = window.setTimeout(() => {
    if (audio?.state === 'running') void audio.suspend()
  }, delayMs)
}

/** "띠링" 두 번. 음을 직접 만들어 소리 파일을 받지 않는다 */
function chime(ctx: AudioContext): void {
  const start = ctx.currentTime + 0.02
  const notes: [offset: number, hz: number][] = [
    [0, 880],
    [0.16, 1175],
    [0.55, 880],
    [0.71, 1175],
  ]
  for (const [offset, hz] of notes) {
    const t = start + offset
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'triangle'
    osc.frequency.value = hz
    gain.gain.setValueAtTime(0.0001, t)
    gain.gain.exponentialRampToValueAtTime(0.5, t + 0.012)
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.32)
    osc.connect(gain).connect(ctx.destination)
    osc.start(t)
    osc.stop(t + 0.34)
  }
}

const VIBRATION = [250, 120, 250]

/** 휴식 끝. 켜 둔 것만 낸다 */
export function alertRestEnd(): void {
  const settings = getRestAlert()
  if (settings.vibrate && canVibrate()) navigator.vibrate(VIBRATION)
  if (settings.sound && audio?.state === 'running') chime(audio)
}

/** 설정에서 켤 때 한 번 들려준다(누르는 순간이라 아이폰에서도 난다) */
export function previewSound(): void {
  try {
    audio ??= new AudioContext()
    const ctx = audio
    void ctx.resume().then(() => {
      chime(ctx)
      // 쉬는 중에 들어와 들어 본 것이면 끝 소리를 위해 깨워 둔 채로 둔다
      if (!holding) sleepSound()
    })
  } catch {
    audio = null
  }
}

export function previewVibration(): void {
  if (canVibrate()) navigator.vibrate(VIBRATION)
}

// ---- 화면 켜 두기 ----

/**
 * 화면이 저절로 꺼지지 않게 잡는다. 돌려받은 함수를 부르면 놓는다.
 *
 * <p>막는 것은 가만히 두면 꺼지는 것뿐이다 — 전원 버튼은 그대로 듣는다. 다른 앱으로 가면 브라우저가
 * 알아서 놓으므로, 앱으로 돌아왔을 때 다시 잡는다. 기기가 거절하면(배터리 절약 모드 등) 그냥 둔다.
 */
export function keepScreenOn(): () => void {
  if (!canKeepScreenOn()) return () => {}
  let lock: WakeLockSentinel | null = null
  let pending = false
  let stopped = false

  const request = () => {
    if (stopped || pending || document.visibilityState !== 'visible') return
    if (lock && !lock.released) return
    pending = true
    navigator.wakeLock
      .request('screen')
      .then((sentinel) => {
        if (stopped) void sentinel.release().catch(() => {})
        else lock = sentinel
      })
      .catch(() => {})
      .finally(() => {
        pending = false
      })
  }

  request()
  document.addEventListener('visibilitychange', request)
  return () => {
    stopped = true
    document.removeEventListener('visibilitychange', request)
    void lock?.release().catch(() => {})
  }
}
