import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [status, setStatus] = useState('loading')
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then((json) => {
        setData(json)
        setStatus('done')
      })
      .catch((err) => {
        setError(err.message)
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
