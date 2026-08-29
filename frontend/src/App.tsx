import { useEffect, useState } from 'react'
import './App.css'

type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

interface HealthComponent {
  status: HealthStatus
  details?: Record<string, unknown>
  components?: Record<string, HealthComponent>
}

interface HealthResponse {
  status: HealthStatus
  components?: Record<string, HealthComponent>
  groups?: string[]
}

type FetchStatus = 'loading' | 'error' | 'done'

function App() {
  const [status, setStatus] = useState<FetchStatus>('loading')
  const [data, setData] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<HealthResponse>
      })
      .then((json) => {
        setData(json)
        setStatus('done')
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : String(err))
        setStatus('error')
      })
  }, [])

  return (
    <div className="health-check">
      <h1>백엔드 연동 확인</h1>
      <p>GET /actuator/health</p>

      {status === 'loading' && <p>확인 중...</p>}

      {status === 'error' && <p className="error">연결 실패: {error}</p>}

      {status === 'done' && (
        <pre className="result">{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  )
}

export default App
