import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // 서비스워커는 알림 전용(src/sw.ts, 「알림_설계서」 4.1). 요청을 가로채지 않고 캐시도 없다 —
      // 캐시가 배포한 새 화면을 막던 문제(구축 기록 11.2)를 다시 만들지 않는다. 그래서 workbox가 만드는
      // 서비스워커(generateSW) 대신 직접 쓴 파일을 빌드만 하고(injectManifest), 미리 받아 둘 파일 목록은
      // 넣지 않는다(injectionPoint 없음). 등록도 자동으로 하지 않고 설정 › 알림에서 켤 때만 한다(app/push.ts).
      // manifest와 아이콘만으로 홈 화면 설치는 된다.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      injectManifest: {
        injectionPoint: undefined,
        // 일반 스크립트로 등록한다(모듈 서비스워커는 브라우저마다 지원이 다르다)
        rollupFormat: 'iife',
      },
      injectRegister: false,
      manifest: {
        name: '밸런스핏',
        short_name: '밸런스핏',
        description: '운동 기록을 분석해 부족한 부위를 찾아주는 앱',
        lang: 'ko',
        start_url: '/',
        // 기본값도 '/'지만 적어 둔다 — 안드로이드는 알림을 눌러 앱을 열 때 이 값이 없으면 브라우저로 여는 사례가 있다
        scope: '/',
        display: 'standalone',
        background_color: '#f3f7fd',
        theme_color: '#f3f7fd',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  build: {
    license: { fileName: 'licenses/npm-packages.txt' },
  },
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
