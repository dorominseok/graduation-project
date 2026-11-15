import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/global.css'
import './styles/glass.css'
import App from './App.tsx'
import { syncBarColor } from './app/theme'
import { cleanEscapeMark, escapeInAppOnce } from './app/inAppBrowser'

// 카톡 같은 앱 안 브라우저면 같은 주소를 휴대폰 브라우저로 다시 연다(구글 로그인이 막혀 있다)
escapeInAppOnce()
cleanEscapeMark()

// 상단 막대 색은 index.html이 붙인 스타일·색감을 보고 맞춘다
syncBarColor()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
