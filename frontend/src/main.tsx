import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/global.css'
import './styles/glass.css'
import App from './App.tsx'
import { syncBarColor } from './app/theme'

// 상단 막대 색은 index.html이 붙인 스타일·색감을 보고 맞춘다
syncBarColor()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
