# 웹서버 이미지 — 프론트를 빌드해 Caddy 안에 넣는다.
# 빌드 맥락은 저장소 루트다(compose.yml). Caddyfile이 deploy/에 있어서다.

FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
