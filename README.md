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
- `docker-compose.yml` — 개발용 PostgreSQL
- `deploy/` — 운영 서버 구성 (compose, Caddy, 백업)

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

루틴 추천이 완성되기 전에 기록·분석까지 먼저 배포한다. 실사용자 검증 4주가 압축할 수 없는 기간이고, 분석이 의미를 가지려면 기록이 먼저 4주 쌓여야 하기 때문이다(「설계 변경 로그」 LOG-15).

EC2(Ubuntu 24.04) 한 대에 DB·백엔드·웹서버(Caddy)를 컨테이너로 띄운다(LOG-35). 화면과 API가 한 도메인에서 나가는 동일 오리진 구성이라 CORS가 없다. 파일은 `deploy/`에 있다.

완료 조건은 **HTTPS 도메인에서 로그인 후 30분을 넘겨 재발급까지 유지되는 것**이다. 리프레시 토큰 쿠키가 `Secure`라 http에서는 저장되지 않아, 이게 되면 HTTPS·웹서버·운영 프로필이 다 맞물렸다는 뜻이다(LOG-17). CD(자동 배포)는 서버가 뜬 뒤 따로 붙인다.

| 파일 | 역할 |
|---|---|
| `deploy/compose.yml` | 운영 구성. 바깥에는 웹서버의 80·443만 열린다 |
| `deploy/Caddyfile` | HTTPS(Let's Encrypt 자동 발급·갱신), `/api` → 백엔드, 나머지는 앱 화면 |
| `deploy/.env.example` | 운영 환경변수 템플릿 (도메인, DB 비밀번호, JWT 키) |
| `deploy/setup-server.sh` | 서버 첫 준비 (스왑 2GB, Docker) |
| `deploy/backup.sh` | DB 덤프, 7일치 보관 |

### 처음 한 번

서버에 접속해서:

```bash
git clone https://github.com/dorominseok/graduation-project.git
cd graduation-project
bash deploy/setup-server.sh        # 처음이면 끝난 뒤 exit → 다시 접속
cd deploy
cp .env.example .env               # DOMAIN, ACME_EMAIL을 채우고
openssl rand -base64 32            # 두 번 실행해 POSTGRES_PASSWORD, JWT_SECRET에 넣는다
docker compose up -d --build
```

도메인의 DNS가 서버 IP를 가리키고 80·443이 열려 있으면 Caddy가 인증서를 받아 온다. `https://<도메인>/actuator/health`에 `"status":"UP"`이 뜨면 끝이다.

백업은 crontab에 등록한다(`crontab -e`):

```
0 4 * * * /home/ubuntu/graduation-project/deploy/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
```

### 새 버전 올리기

```bash
cd graduation-project && git pull
cd deploy && docker compose up -d --build
```

DB와 인증서는 볼륨에 남으므로 다시 빌드해도 사라지지 않는다. `docker compose down -v`는 **볼륨까지 지우므로** 운영 서버에서 쓰지 않는다.
