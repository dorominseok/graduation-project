import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // 서비스워커는 11월이다(구축 기록 11.2). 빌드가 sw.js를 만들어도 등록하지 않는다 —
      // 등록되면 배포한 새 화면이 캐시에 막혀 사용자에게 늦게 보인다.
      // manifest와 아이콘만으로 홈 화면 설치는 된다.
      injectRegister: false,
      manifest: {
        name: '밸런스핏',
        short_name: '밸런스핏',
        description: '운동 기록을 분석해 부족한 부위를 찾아주는 앱',
        lang: 'ko',
        start_url: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#ffffff',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // actuator health check (5단계 완료 조건) — CORS 없이 확인하기 위해 함께 프록시
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
