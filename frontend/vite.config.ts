import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // devOptions.enabled defaults to false: no service worker in `npm run dev`.
      // Only the manifest is registered at this stage.
      manifest: {
        name: '운동 습관 분석 헬스 웹앱',
        short_name: 'FitLog',
        start_url: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#ffffff',
        icons: [],
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
