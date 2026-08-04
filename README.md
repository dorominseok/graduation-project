# 운동 습관 분석 기반 맞춤 루틴 추천 헬스 웹앱

[![CI](https://github.com/dorominseok/graduation-project/actions/workflows/ci.yml/badge.svg)](https://github.com/dorominseok/graduation-project/actions/workflows/ci.yml)

컴퓨터공학과 졸업작품 (2026)

운동 기록을 분석해 약점 부위를 자동 판정하고, 맞춤 루틴을 추천하는 PWA.

## 개발 환경

| 항목 | 버전 |
|---|---|
| Java | 21 (Temurin) |
| Node.js | 24.11.1 / npm 11.6.2 |
| PostgreSQL | 16 (Docker) |
| Docker | 29.6.2 / Compose v5.3.1 |
| 빌드 | Gradle (Groovy DSL) |

## 구조

- `backend/` — Spring Boot
- `frontend/` — React (Vite, PWA)
- `docs/` — 설계 문서
- `docker-compose.yml` — PostgreSQL

## 실행

### 0. 환경변수

```bash
cp .env.example .env
```

`.env`에 `POSTGRES_PASSWORD`를 채운다. 나머지 값(`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PORT`)은 로컬 개발용 기본값을 그대로 쓰면 된다.

`.env`는 `docker-compose.yml`(PostgreSQL 컨테이너)에서만 쓰인다. 백엔드는 이 값을 자동으로 읽지 않으므로, `.env`의 값을 기본값에서 바꿨다면 백엔드 실행 전에 아래 환경변수를 `.env`와 같은 값으로 맞춰서 내보내야 한다.

| 환경변수 | 기본값 (`.env.example` 기준) |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fitness` |
| `DB_USERNAME` | `fitness` |
| `DB_PASSWORD` | (`.env`의 `POSTGRES_PASSWORD`) |

기본값을 그대로 쓴다면 이 단계는 건너뛰어도 된다.

### 1. DB 기동

```bash
docker compose up -d
docker compose ps   # postgres가 healthy인지 확인
```

PostgreSQL이 `.env`의 `POSTGRES_PORT`(기본 5432)로 뜬다.

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

IntelliJ에서 `BackendApplication`을 직접 실행해도 된다. `http://localhost:8080/actuator/health`에서 `"status": "UP"`(특히 `db.status: UP`)이 뜨면 DB 연결까지 정상이다.

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173`에서 확인한다. `/api`, `/actuator`는 Vite dev 서버가 `localhost:8080`(백엔드)으로 프록시하므로, 화면에 백엔드의 `/actuator/health` 응답이 그대로 표시되면 연동이 정상 동작하는 것이다.

**포트 정리**

| 서비스 | 포트 |
|---|---|
| PostgreSQL | `.env`의 `POSTGRES_PORT` (기본 5432) |
| 백엔드 (Spring Boot) | 8080 |
| 프론트엔드 (Vite dev) | 5173 |

## 배포

CD 미구현 — 11월 예정 (AWS EC2)
