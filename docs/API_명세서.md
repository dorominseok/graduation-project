# API 명세서
### 운동 습관 분석 기반 맞춤 루틴 추천 헬스 웹앱(PWA)

> 「운동 기록 방식 설계서」 10장 "다음 설계 과제 — API 명세"에 대응하는 산출물이다.
> 작성 시점: 2026년 8월 5주 (프론트–백엔드 연동 직전)
> 대상 범위: **9월 1~3주 구현분** — 인증·프로필 / 운동 기록 / 통계·분석
> 제외 범위: 루틴 추천(UC-03-03~06), 뱃지(UC-04), 친구 그룹(UC-05), 알림(UC-06) — 10~11월 별도 명세

---

## 0. 문서 개요

### 0.1 이 명세가 다루는 것

| 영역 | 유스케이스 | 절 |
|---|---|---|
| 인증·프로필 | UC-01-01 ~ UC-01-03 | 4장 |
| 운동 종목 조회 | UC-02-05 | 5장 |
| 운동 기록 (세션·세트) | UC-02-01, UC-02-02, UC-02-03, UC-02-06 | 6장 |
| 통계 (1RM 추이, 세트별 강도) | UC-02-04 | 7장 |
| 약점·불균형 분석 | UC-03-01, UC-03-02 | 8장 |

### 0.2 설계 결정 반영 대응표

본 명세가 반영한 상위 설계 결정과 해당 위치는 다음과 같다. 각 항목의 근거는 참조 문서에 있으며 여기서는 반복하지 않는다.

| 설계 결정 | 출처 | 반영 위치 |
|---|---|---|
| 세트는 완료 체크 시점에만 INSERT (계획 행 미저장, `is_completed` 없음) | 기록 방식 3.3, LOG-05 | 6.4 세트 저장 |
| 세션 상태 DRAFT / DONE, 분석 집계는 DONE만 | 기록 방식 5.1 | 6.3 세션 종료, 8장 집계 조건 |
| 볼륨 집계는 본세트만 (`is_warmup = false`) | 분석 1.2, LOG-02 | 8.2 |
| 사후 입력(BACKFILL)은 볼륨에 포함, 시간 집계에서만 제외 | 기록 방식 4.3 | 6.2, 8.2 |
| 운동 시간은 세트 저장 시각으로 자동 산출 (간격 15분 캡, 세션 4시간 상한) | 기록 방식 4.2 | 6.3, 8.5 |
| 추정 1RM은 `WEIGHT_REPS` 종목의 `reps ≤ 12` 세트, 일자별 최댓값 | 분석 1.3, LOG-07 | 7.1, 8.5 |
| 판정 부위 `primary_muscle` 기반 9종, 화면 표시는 상위 6종 / 하위 9종 2계층 | 분석 2.3·2.4, LOG-09·LOG-10 | 8.1, 8.2 |
| 판정 결과는 서버가 완성해서 내려줌 (화면이 합산·분류하지 않음) | LOG-10 | 8.2 |
| 최근 4주 DONE 세션 수가 임계 미만이면 신뢰도 낮음 표시 | 기록 방식 5.5 | 8.2 `confidence` |
| 균형 판정: 밀기/당기기·상하체, 큰 쪽 ÷ 작은 쪽 2배 초과 시 경고 | 분석 4.2·4.3 | 8.3 |

---

## 1. 공통 규약

### 1.1 Base URL / 버전

```
https://{host}/api/v1
```

- URL 경로에 버전을 둔다(`/api/v1`). 9월 범위는 모두 `v1`이다.
- 이 명세의 모든 상대 경로는 `/api/v1`을 생략한 표기이다. 예: `POST /auth/login` = `POST /api/v1/auth/login`

### 1.2 요청·응답 형식

| 항목 | 규칙 |
|---|---|
| 형식 | JSON (`Content-Type: application/json; charset=UTF-8`) |
| 필드 명명 | `camelCase` (예: `weightKg`, `performedOn`) |
| 문자 인코딩 | UTF-8 |
| 날짜 | `YYYY-MM-DD` (예: `2026-09-01`) |
| 날짜·시각 | ISO 8601 오프셋 포함 (예: `2026-09-01T19:32:11+09:00`). 서버 타임존 `Asia/Seoul` (`application.yaml`의 `spring.jackson.time-zone`) |
| 숫자 ID | JSON number (`BIGSERIAL`, 2^53 미만이므로 정밀도 문제 없음) |
| 중량 | `weightKg` — 소수 1자리까지 (`NUMERIC(6,2)`, 예: `62.5`) |
| null 필드 | 값이 없으면 키를 포함하고 `null`. 키 자체를 생략하지 않는다 |

### 1.3 인증

- `/auth/**`와 `GET /exercises`, `GET /exercises/{id}`, `GET /health`를 제외한 모든 엔드포인트는 인증이 필요하다.
- 요청 헤더에 액세스 토큰을 담는다.

```
Authorization: Bearer {accessToken}
```

- 토큰이 없거나 만료·변조된 경우 `401`을 반환한다(1.5의 에러 코드 참조).
- 사용자는 자신의 리소스에만 접근할 수 있다. 타 사용자의 세션·세트·통계에 접근하면 `403 ACCESS_DENIED`.

상세한 인증 방식(JWT 채택 근거, 토큰 정책)은 **2장**에 있다.

### 1.4 페이지네이션

목록 조회는 offset 기반 페이지네이션을 쓴다.

| 쿼리 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | `0` | 0-기반 페이지 번호 |
| `size` | `20` | 페이지 크기 (최대 `100`) |
| `sort` | 엔드포인트별 지정 | 예: `performedOn,desc` |

응답 공통 형태:

```json
{
  "content": [ /* 항목 배열 */ ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "first": true,
    "last": false
  }
}
```

### 1.5 에러 응답 형식 (통일안)

모든 4xx·5xx 응답은 아래 단일 구조를 따른다. 성공 응답에는 이 envelope를 쓰지 않는다(리소스를 그대로 반환).

```json
{
  "timestamp": "2026-09-01T10:00:00+09:00",
  "path": "/api/v1/workout-sessions/55/sets",
  "status": 400,
  "code": "INVALID_MEASURE_INPUT",
  "message": "이 종목은 중량과 횟수가 필요합니다.",
  "errors": [
    { "field": "reps", "reason": "필수 항목입니다." }
  ],
  "traceId": "b1e9c7a2f04b4e1d"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `timestamp` | ✔ | 오류 발생 시각 (ISO 8601) |
| `path` | ✔ | 요청 경로 |
| `status` | ✔ | HTTP 상태 코드 (숫자) |
| `code` | ✔ | 애플리케이션 오류 코드 (아래 표의 enum). 클라이언트 분기는 이 값으로 한다 |
| `message` | ✔ | 사용자 노출 가능한 한국어 메시지 |
| `errors` | — | 필드 단위 검증 오류 목록. 필드 오류가 아닌 경우 키 생략 |
| `traceId` | — | 서버 로그 상관 추적용 ID (지원 시) |

**HTTP 상태와 오류 코드**

| HTTP | `code` | 발생 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수값 누락, 형식 오류, 범위 초과 (`errors` 포함) |
| 400 | `INVALID_MEASURE_INPUT` | 종목의 `measureType`에 맞지 않는 세트 입력 (5.1·6.4 참조) |
| 400 | `INVALID_DATE_RANGE` | `from > to`, 미래 날짜 등 |
| 401 | `AUTHENTICATION_REQUIRED` | `Authorization` 헤더 없음 |
| 401 | `INVALID_CREDENTIALS` | 로그인 시 이메일·비밀번호 불일치 |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료 → 클라이언트는 `/auth/refresh` 시도 |
| 401 | `TOKEN_INVALID` | 서명 불일치·형식 오류·폐기된 토큰 |
| 403 | `ACCESS_DENIED` | 타 사용자 리소스 접근 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 리소스 없음 |
| 409 | `EMAIL_ALREADY_EXISTS` | 회원가입 이메일 중복 |
| 409 | `DRAFT_SESSION_EXISTS` | 이미 진행 중인 `DRAFT` 세션이 있는 상태에서 새 `LIVE` 세션 생성 시도 (6.2) |
| 409 | `SESSION_ALREADY_COMPLETED` | 이미 `DONE`인 세션에 종료 재요청 |
| 422 | `EMPTY_SESSION` | 세트가 하나도 없는 세션 종료 시도 (6.3) |
| 429 | `RATE_LIMITED` | 요청 한도 초과 (도입 시) |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류 |

> 구현: Spring `@RestControllerAdvice`로 `MethodArgumentNotValidException`, 커스텀 `ApiException`(→ `code` 보유), `AuthenticationException` 등을 위 구조로 직렬화하는 단일 핸들러를 둔다.

---

## 2. 인증 방식 결정 (JWT vs 세션)

### 2.1 두 선택지

| | 세션 쿠키 (서버 세션) | JWT (토큰) |
|---|---|---|
| 상태 저장 | 서버가 세션 저장소 보유 (인메모리 / DB / Redis) | 서버 무상태, 토큰 자체에 클레임 |
| 무효화(로그아웃·강제 만료) | 즉시 (세션 삭제) | 어려움 — 만료까지 유효. 폐기 목록·짧은 TTL로 보완 |
| 확장(다중 인스턴스) | 세션 공유 저장소 또는 sticky session 필요 | 서버 간 공유 불필요 |
| **재배포 영향** | 인메모리 세션이면 배포 때마다 전원 로그아웃 | 영향 없음 (서버가 서명 키만 유지) |
| CSRF | 쿠키 자동 전송 → CSRF 토큰 방어 필요 | `Authorization` 헤더 사용 시 CSRF 비해당 |
| XSS 시 토큰 탈취 | `HttpOnly` 쿠키면 JS 접근 불가 | 액세스 토큰을 JS가 다루면 탈취 위험 |
| React SPA / PWA 궁합 | 가능하나 쿠키·CORS·CSRF 설정 부담 | SPA에서 널리 쓰는 방식, 헤더로 단순 |
| 구현량 | Spring Security 기본 + 세션 저장소 | 필터·토큰 발급/검증·리프레시 로직 직접 구현 |

### 2.2 결정 — **JWT (액세스 + 리프레시)**

이 프로젝트 조건에서 JWT를 채택한다. 결정적 근거는 **배포 방식**이다.

- **GitHub Actions로 EC2에 잦은 재배포**를 한다. 인메모리 세션이면 배포할 때마다 실사용자 검증에 참여하는 지인 약 10명이 전부 로그아웃된다. JWT는 서버가 서명 키만 유지하면 되므로 재배포와 무관하다.
- **React SPA + PWA**에서 `Authorization: Bearer` 헤더 방식이 표준적이고, 쿠키·CSRF·CORS 조합보다 설정이 단순하다.
- 스택에 **Redis가 없고** 세션 공유 저장소를 새로 도입하기엔 범위 대비 과하다. 향후 EC2를 오토스케일로 늘려도 JWT는 변경이 없다.

무효화가 어렵다는 JWT의 약점은 아래 정책으로 보완한다.

### 2.3 토큰 정책

| 토큰 | 형식 | 만료 | 저장 위치 (클라이언트) | 용도 |
|---|---|---|---|---|
| 액세스 토큰 | JWT (HS256 서명) | **30분** | 메모리 (JS 변수 / 상태). `localStorage` 지양 | API 호출 시 `Authorization` 헤더 |
| 리프레시 토큰 | 불투명 문자열(랜덤 256bit) | **14일** | `HttpOnly; Secure; SameSite=Strict` 쿠키 | 액세스 토큰 재발급 |

- **액세스 토큰 클레임**: `sub`(userId), `email`, `iat`, `exp`. 최소한만 담는다. 서버는 매 요청에서 서명·만료만 검증하고 DB 조회를 하지 않는다.
- **리프레시 토큰은 DB에 저장**한다(2.4의 `refresh_tokens` 테이블). 재발급 시 **회전(rotation)**: 기존 토큰을 폐기하고 새 토큰을 발급한다. 재사용이 감지되면 해당 사용자의 모든 리프레시 토큰을 폐기한다.
- **로그아웃**: 서버가 리프레시 토큰을 DB에서 삭제하고 쿠키를 만료시킨다. 액세스 토큰은 남은 만료 시간(최대 30분) 동안 유효하다 — 이 시간 창은 감수한다.
- **서명 키**는 환경변수(`JWT_SECRET`)로 주입한다. `.env.example`에 항목을 추가한다.

### 2.4 필요한 스키마 추가

9월 인증 구현 전에 마이그레이션이 필요하다. `V1__init.sql`에 없는 항목이다.

**(1) `refresh_tokens` — 신규 테이블**

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT FK → users ON DELETE CASCADE | |
| `token_hash` | VARCHAR(64) NOT NULL UNIQUE | 원문 대신 SHA-256 해시 저장 |
| `expires_at` | TIMESTAMPTZ NOT NULL | |
| `revoked_at` | TIMESTAMPTZ NULL | 회전·로그아웃 시 기록 |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |

**(2) `users` 프로필 컬럼 — 기존 테이블 확장**

`users`에는 현재 `email / password_hash / nickname`만 있다. UC-01-03(프로필: 키/체중/경력/목표)을 위해 컬럼을 추가한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `height_cm` | NUMERIC(4,1) NULL | 키 |
| `weight_kg` | NUMERIC(5,2) NULL | 체중 |
| `experience_level` | VARCHAR(15) NULL | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` |
| `goal` | VARCHAR(20) NULL | `STRENGTH` / `HYPERTROPHY` / `ENDURANCE` / `GENERAL_FITNESS` |

> 별도 `user_profiles` 테이블로 분리하는 방안도 있으나, 1:1이고 필드가 4개뿐이라 `users`에 인라인한다. 값은 회원가입 후 선택 입력이므로 전부 NULL 허용.

이 두 마이그레이션을 `V3__auth_and_profile.sql`로 만든다. (`V2`는 이미 `equipment` 제약에 사용됨.)

---

## 3. 엔드포인트 목록

### 3.1 인증·프로필

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/auth/signup` | 회원가입 (가입 후 토큰 발급) | — |
| POST | `/auth/login` | 로그인 | — |
| POST | `/auth/refresh` | 액세스 토큰 재발급 (리프레시 쿠키 사용) | 쿠키 |
| POST | `/auth/logout` | 로그아웃 (리프레시 토큰 폐기) | ✔ |
| GET | `/users/me` | 내 프로필 조회 | ✔ |
| PATCH | `/users/me` | 닉네임·프로필 수정 | ✔ |

### 3.2 운동 종목

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/exercises` | 종목 목록·검색 (필터·페이지네이션) | — |
| GET | `/exercises/{id}` | 종목 단건 조회 | — |

### 3.3 운동 기록 (세션·세트)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/workout-sessions` | 세션 생성 (`LIVE` 또는 `BACKFILL`) | ✔ |
| GET | `/workout-sessions` | 히스토리 목록 (기간·상태 필터) | ✔ |
| GET | `/workout-sessions/current` | 진행 중(`DRAFT`) 세션 조회 — "이어서 기록" | ✔ |
| GET | `/workout-sessions/calendar` | 월별 운동일 요약 (캘린더 렌더링용) | ✔ |
| GET | `/workout-sessions/{id}` | 세션 상세 (세트 포함) | ✔ |
| PATCH | `/workout-sessions/{id}` | 메모·수행일자·시간 수동 보정 수정 | ✔ |
| POST | `/workout-sessions/{id}/complete` | 세션 종료 (`DRAFT` → `DONE`, 시간 산출) | ✔ |
| DELETE | `/workout-sessions/{id}` | 세션 삭제 (세트 포함 CASCADE) | ✔ |
| POST | `/workout-sessions/{id}/sets` | 세트 저장 (완료 체크 시점) | ✔ |
| PATCH | `/workout-sessions/{id}/sets/{setId}` | 세트 수정 | ✔ |
| DELETE | `/workout-sessions/{id}/sets/{setId}` | 세트 삭제 | ✔ |

### 3.4 통계

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/stats/one-rm-trend` | 종목별 추정 1RM(kg) 날짜별 추이 | ✔ |
| GET | `/stats/session-intensity/{sessionId}` | 특정 세션의 세트별 강도(%) 스냅샷 | ✔ |

### 3.5 분석

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/analysis/muscle-volume` | 부위별 볼륨·부족 판정 (2계층) + 신뢰도 | ✔ |
| GET | `/analysis/balance` | 밀기/당기기·상하체 균형 판정 | ✔ |

---

## 4. 인증·프로필 API

### 4.1 POST /auth/signup

이메일 기반 회원가입. 가입 성공 시 곧바로 로그인 상태가 되도록 토큰을 함께 발급한다(별도 로그인 왕복 불필요).

**요청**

```json
{
  "email": "user@example.com",
  "password": "hunter2hunter2",
  "nickname": "민석"
}
```

| 필드 | 규칙 |
|---|---|
| `email` | 필수, 이메일 형식, ≤ 255자, 중복 불가 |
| `password` | 필수, 8~72자 (BCrypt 입력 상한). 서버가 BCrypt 해시 후 `password_hash`에 저장 |
| `nickname` | 필수, 1~50자 |

**응답 `201 Created`**

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "expiresIn": 1800,
  "user": {
    "userId": 42,
    "email": "user@example.com",
    "nickname": "민석"
  }
}
```

- 리프레시 토큰은 응답 본문이 아니라 `Set-Cookie` 헤더로 내려간다:
  `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600`

**오류**

| 상황 | 응답 |
|---|---|
| 이메일 중복 | `409 EMAIL_ALREADY_EXISTS` |
| 형식 오류 | `400 VALIDATION_ERROR` (`errors`에 필드별 사유) |

### 4.2 POST /auth/login

**요청**

```json
{ "email": "user@example.com", "password": "hunter2hunter2" }
```

**응답 `200 OK`** — 4.1의 응답과 동일 구조 (`accessToken`, `expiresIn`, `user`) + 리프레시 쿠키.

**오류**

| 상황 | 응답 |
|---|---|
| 이메일 없음 / 비밀번호 불일치 | `401 INVALID_CREDENTIALS` (둘을 구분하지 않는다 — 계정 존재 여부 노출 방지) |

### 4.3 POST /auth/refresh

리프레시 쿠키로 새 액세스 토큰을 받는다. 요청 본문 없음.

**요청**: 본문 없음. `Cookie: refreshToken=...` 자동 전송.

**응답 `200 OK`**

```json
{ "tokenType": "Bearer", "accessToken": "eyJ...", "expiresIn": 1800 }
```

- 리프레시 토큰 회전: 응답에 새 `Set-Cookie: refreshToken=...`가 포함된다.

**오류**

| 상황 | 응답 |
|---|---|
| 쿠키 없음 / 만료 / DB에 없음 / 이미 폐기됨 | `401 TOKEN_INVALID` — 클라이언트는 로그인 화면으로 |
| 폐기된 토큰 재사용 감지 | `401 TOKEN_INVALID` + 해당 사용자 전체 리프레시 토큰 폐기 |

### 4.4 POST /auth/logout

**요청**: 본문 없음. `Authorization` 헤더 + 리프레시 쿠키.

**응답 `204 No Content`** — 리프레시 토큰을 DB에서 삭제하고 쿠키를 만료시킨다(`Set-Cookie: refreshToken=; Max-Age=0`).

### 4.5 GET /users/me

**응답 `200 OK`**

```json
{
  "userId": 42,
  "email": "user@example.com",
  "nickname": "민석",
  "profile": {
    "heightCm": 175.0,
    "weightKg": 72.5,
    "experienceLevel": "INTERMEDIATE",
    "goal": "HYPERTROPHY"
  },
  "createdAt": "2026-09-01T09:12:00+09:00"
}
```

- 프로필 미입력 시 `profile`의 각 필드는 `null`.

### 4.6 PATCH /users/me

부분 수정. 포함된 필드만 갱신한다.

**요청 (예)**

```json
{
  "nickname": "민석2",
  "profile": {
    "heightCm": 175.0,
    "weightKg": 71.0,
    "experienceLevel": "ADVANCED",
    "goal": "STRENGTH"
  }
}
```

| 필드 | 규칙 |
|---|---|
| `nickname` | 1~50자 |
| `profile.heightCm` | 50.0 ~ 260.0, 소수 1자리 |
| `profile.weightKg` | 20.0 ~ 400.0, 소수 2자리 |
| `profile.experienceLevel` | enum (부록 A) |
| `profile.goal` | enum (부록 A) |

**응답 `200 OK`** — 4.5와 동일한 전체 프로필.

> 이메일·비밀번호 변경은 9월 범위에서 제외한다. 필요 시 별도 엔드포인트(`PATCH /users/me/password`)로 추가.

---

## 5. 운동 종목 API

### 5.1 종목 데이터 개요

`exercises` 테이블(V1 + V2). 종목 데이터 정제는 8월 2주 작업 산출물이며, 본 API는 읽기 전용이다.

| 필드 | 타입 | 값 |
|---|---|---|
| `id` | number | |
| `nameKo` | string | 한글 명칭 |
| `nameEn` | string \| null | 영문 명칭 |
| `bodyPart` | enum | `CHEST` `BACK` `LEGS` `SHOULDERS` `ARMS` `CORE` (저장·검색·차트용 6종) |
| `primaryMuscle` | string \| null | 주동근 원자값 (`chest`, `lats`, `triceps` …). 판정 부위 산출 근거 (8.1) |
| `pushPull` | enum | `PUSH` `PULL` `NONE` |
| `measureType` | enum | `WEIGHT_REPS` `BODYWEIGHT_REPS` `WEIGHTED_BODYWEIGHT` `TIME` |
| `equipment` | enum | `BARBELL` `DUMBBELL` `MACHINE` `CABLE` `BODYWEIGHT` `PULLUP_BAR` |

**`measureType`별 세트 입력 항목** (6.4의 검증 근거)

| `measureType` | 필수 입력 | 예시 종목 | 1RM 산출 |
|---|---|---|---|
| `WEIGHT_REPS` | `weightKg`, `reps` | 벤치프레스, 스쿼트 | 대상 |
| `BODYWEIGHT_REPS` | `reps` | 푸시업, 크런치 | 제외 |
| `WEIGHTED_BODYWEIGHT` | `weightKg`(추가 중량), `reps` | 중량 딥스 | 제외 |
| `TIME` | `durationSec` | 플랭크 | 제외 |

### 5.2 GET /exercises

**쿼리 파라미터**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `q` | string | `nameKo` / `nameEn` 부분 일치 검색 |
| `bodyPart` | enum | 6종 중 하나로 필터 |
| `equipment` | enum | 기구로 필터 |
| `measureType` | enum | 측정 유형으로 필터 |
| `page`, `size`, `sort` | | 1.4 참조. 기본 `sort=nameKo,asc` |

**응답 `200 OK`** — 1.4의 페이지 envelope. `content` 항목:

```json
{
  "id": 128,
  "nameKo": "바벨 벤치프레스",
  "nameEn": "Barbell Bench Press",
  "bodyPart": "CHEST",
  "primaryMuscle": "chest",
  "pushPull": "PUSH",
  "measureType": "WEIGHT_REPS",
  "equipment": "BARBELL"
}
```

> "최근 사용·즐겨찾기 종목 상단 노출"(기록 방식 3.4)은 기록 화면 프론트에서 `GET /workout-sessions` 이력으로 계산한다. 별도 엔드포인트는 9월 범위 밖.

### 5.3 GET /exercises/{id}

**응답 `200 OK`** — 5.2의 단일 객체. 없으면 `404 RESOURCE_NOT_FOUND`.

---

## 6. 운동 기록 API

### 6.1 개념 모델

```
workout_session (하루 한 번의 운동)
  └─ workout_set (완료 체크한 세트 1개 = 1행)
```

- **세션 생성 → 세트 저장(반복) → 세션 종료**가 기본 흐름(UC-02-01).
- 프론트는 사용자가 **첫 세트를 완료 체크하는 순간** `POST /workout-sessions`로 세션을 만들고, 이어서 `POST .../sets`를 호출한다. 계획 행은 서버로 보내지 않는다(LOG-05).
- `DRAFT` 세션은 사용자당 하나만 존재할 수 있다.

### 6.2 POST /workout-sessions

**요청**

```json
{
  "performedOn": "2026-09-01",
  "source": "LIVE",
  "routineId": null
}
```

| 필드 | 규칙 |
|---|---|
| `performedOn` | 필수. `LIVE`면 오늘 날짜여야 한다(자정 넘김은 시작 일자 유지). `BACKFILL`이면 과거 날짜 허용, 미래 불가 |
| `source` | `LIVE`(기본) \| `BACKFILL` |
| `routineId` | 선택. 추천 루틴에서 진입한 경우 연결(수행률 측정용). 9월엔 `routines` 테이블이 없으므로 항상 `null`. 컬럼만 채워둔다 |

**응답 `201 Created`**

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
  "status": "DRAFT",
  "source": "LIVE",
  "routineId": null,
  "startedAt": null,
  "endedAt": null,
  "durationSec": null,
  "durationOverrideSec": null,
  "effectiveDurationSec": null,
  "memo": null,
  "createdAt": "2026-09-01T19:10:02+09:00",
  "updatedAt": "2026-09-01T19:10:02+09:00",
  "exercises": []
}
```

**오류**

| 상황 | 응답 |
|---|---|
| 이미 `DRAFT` 세션 보유 (`LIVE` 생성 시) | `409 DRAFT_SESSION_EXISTS` — 본문 `message`에 안내, 클라이언트는 `GET /workout-sessions/current`로 이어쓰기 유도 |
| `LIVE`인데 `performedOn`이 오늘이 아님 | `400 VALIDATION_ERROR` |
| `performedOn`이 미래 | `400 INVALID_DATE_RANGE` |

> **BACKFILL 편의(선택 구현)**: `POST /workout-sessions`에 `"sets": [ … ], "autoComplete": true`를 함께 받아 세션 생성 + 세트 일괄 저장 + 종료를 한 번에 처리할 수 있다. 기본 흐름은 위 3단계를 유지한다.

### 6.3 POST /workout-sessions/{id}/complete

`DRAFT` → `DONE` 전환. 이 시점에 운동 시간을 산출한다(8.5의 규칙).

**요청**: 본문 없음.

**응답 `200 OK`** — 6.5의 세션 상세 형태. `status: "DONE"`, `startedAt`/`endedAt`/`durationSec` 채워짐.

- `source = LIVE`: `durationSec` = 세트 `recordedAt` 기반 자동 산출.
- `source = BACKFILL`: `durationSec`은 `null` 유지(그룹 시간 집계 제외). `durationOverrideSec`이 있으면 `effectiveDurationSec`에 반영.

**오류**

| 상황 | 응답 |
|---|---|
| 이미 `DONE` | `409 SESSION_ALREADY_COMPLETED` |
| 세트 0개 | `422 EMPTY_SESSION` — 클라이언트는 세션을 `DELETE` 하도록 안내 |

> 자정 경과 시 서버 스케줄러가 마지막 세트 시각 기준으로 `DRAFT`를 자동 `DONE` 처리한다(기록 방식 5.1). API 호출과 무관한 서버 내부 동작.

### 6.4 POST /workout-sessions/{id}/sets

완료 체크한 세트 하나를 저장한다. 서버가 `recordedAt = now()`를 기록한다.

**요청**

```json
{
  "exerciseId": 128,
  "weightKg": 70.0,
  "reps": 10,
  "durationSec": null,
  "isWarmup": false,
  "setNo": null
}
```

| 필드 | 규칙 |
|---|---|
| `exerciseId` | 필수 |
| `weightKg` | 종목 `measureType`에 따라 필수/무시 (5.1 표). 0 이상, 소수 2자리 |
| `reps` | 종목에 따라 필수/무시. 1 이상 |
| `durationSec` | `TIME` 종목만 필수. 1 이상 |
| `isWarmup` | 기본 `false`. `true`면 볼륨 집계 제외(8.2), 강도(%)에는 표시(7.2) |
| `setNo` | 선택. 생략 시 서버가 `해당 세션 내 같은 exerciseId 세트 수 + 1`로 자동 부여 |

**응답 `201 Created`**

```json
{
  "id": 901,
  "sessionId": 55,
  "exerciseId": 128,
  "exerciseName": "바벨 벤치프레스",
  "setNo": 1,
  "weightKg": 70.0,
  "reps": 10,
  "durationSec": null,
  "isWarmup": false,
  "recordedAt": "2026-09-01T19:32:11+09:00"
}
```

**오류**

| 상황 | 응답 |
|---|---|
| `measureType`에 필요한 값 누락 (예: `WEIGHT_REPS`인데 `reps` 없음) | `400 INVALID_MEASURE_INPUT` |
| `exerciseId` 없음 | `404 RESOURCE_NOT_FOUND` |
| 타인 세션 | `403 ACCESS_DENIED` |

> 세션 상태가 `DONE`이어도 세트 추가·수정·삭제를 허용한다(기록 방식 5.3, 사후 보정). `LIVE` 세션의 세트가 바뀌면 `durationSec`을 재산출한다. `DRAFT` 세션의 첫 세트가 저장되면 `startedAt`이 채워진다.

### 6.5 GET /workout-sessions/{id}

**응답 `200 OK`**

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
  "status": "DONE",
  "source": "LIVE",
  "routineId": null,
  "startedAt": "2026-09-01T19:10:02+09:00",
  "endedAt": "2026-09-01T20:05:41+09:00",
  "durationSec": 3339,
  "durationOverrideSec": null,
  "effectiveDurationSec": 3339,
  "memo": "가슴 + 삼두",
  "createdAt": "2026-09-01T19:10:02+09:00",
  "updatedAt": "2026-09-01T20:05:41+09:00",
  "exercises": [
    {
      "exerciseId": 128,
      "exerciseName": "바벨 벤치프레스",
      "measureType": "WEIGHT_REPS",
      "sets": [
        { "id": 901, "setNo": 1, "weightKg": 70.0, "reps": 10, "durationSec": null, "isWarmup": false, "recordedAt": "2026-09-01T19:32:11+09:00" },
        { "id": 902, "setNo": 2, "weightKg": 70.0, "reps": 9,  "durationSec": null, "isWarmup": false, "recordedAt": "2026-09-01T19:36:02+09:00" }
      ]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `durationSec` | 자동 산출값 (`BACKFILL`은 `null`) |
| `durationOverrideSec` | 사용자 수동 보정값 (없으면 `null`) |
| `effectiveDurationSec` | **서버가 결정한 최종 시간** = `durationOverrideSec ?? durationSec`. 화면은 이 값만 쓰면 된다 |
| `exercises[]` | 종목별로 그룹핑, 각 그룹 내 `sets`는 `setNo` 오름차순 |

### 6.6 GET /workout-sessions

히스토리 목록(UC-02-03). 세트는 포함하지 않는 요약 형태.

**쿼리 파라미터**

| 파라미터 | 설명 |
|---|---|
| `from`, `to` | `performedOn` 범위 (둘 다 `YYYY-MM-DD`). 생략 시 최근 30일 |
| `status` | `DRAFT` \| `DONE` 필터 (생략 시 전체) |
| `page`, `size`, `sort` | 기본 `sort=performedOn,desc` |

**응답 `200 OK`** — 페이지 envelope. `content` 항목:

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
  "status": "DONE",
  "source": "LIVE",
  "effectiveDurationSec": 3339,
  "setCount": 12,
  "exerciseCount": 3,
  "exerciseNames": ["바벨 벤치프레스", "인클라인 덤벨 프레스", "케이블 푸시다운"]
}
```

### 6.7 GET /workout-sessions/current

진행 중 세션 이어쓰기(UC-02-01 예외 흐름 8a).

**응답**

- `200 OK` + 6.5의 세션 상세 (현재 `DRAFT` 세션)
- `204 No Content` (진행 중 세션 없음)

### 6.8 GET /workout-sessions/calendar

캘린더 화면용 월별 요약.

**쿼리 파라미터**: `year` (필수), `month` (필수, 1~12)

**응답 `200 OK`**

```json
{
  "year": 2026,
  "month": 9,
  "days": [
    { "date": "2026-09-01", "sessionCount": 1, "hasDraft": false },
    { "date": "2026-09-03", "sessionCount": 2, "hasDraft": false }
  ]
}
```

- 운동 기록이 있는 날짜만 배열에 포함.

### 6.9 PATCH /workout-sessions/{id}

**요청 (부분 수정)**

```json
{
  "memo": "컨디션 좋았음",
  "performedOn": "2026-08-31",
  "durationOverrideSec": 4200
}
```

| 필드 | 규칙 |
|---|---|
| `memo` | ≤ 2000자, `null` 허용 |
| `performedOn` | `BACKFILL` 세션의 날짜 정정에 사용. 미래 불가. `LIVE` 세션은 변경 불가(`400`) |
| `durationOverrideSec` | 0 이상. 설정 시 `effectiveDurationSec`이 이 값이 됨. `null`로 보내면 보정 해제 |

**응답 `200 OK`** — 6.5의 세션 상세.

### 6.10 DELETE /workout-sessions/{id}

**응답 `204 No Content`** — 세트도 함께 삭제(`ON DELETE CASCADE`). 분석은 온디맨드 집계라 별도 재계산 불필요(기록 방식 5.3).

### 6.11 PATCH /workout-sessions/{id}/sets/{setId}

**요청 (부분 수정)**

```json
{ "weightKg": 72.5, "reps": 8, "isWarmup": false }
```

- `measureType` 검증은 6.4와 동일.
- `LIVE` 세션이면 시간 재산출.

**응답 `200 OK`** — 6.4의 세트 객체.

### 6.12 DELETE /workout-sessions/{id}/sets/{setId}

**응답 `204 No Content`**. `LIVE` 세션이면 시간 재산출. 세션의 마지막 세트를 지워도 세션 자체는 남는다(빈 `DONE` 세션 가능 — 히스토리에 `setCount: 0`으로 표시).

---

## 7. 통계 API

분석 로직 설계서 1.3의 표시 분리 원칙에 따른다.

| 화면 | 단위 | 시간축 | 엔드포인트 |
|---|---|---|---|
| 추정 1RM 추이 | kg | 있음 (날짜별) | 7.1 |
| 세트별 강도 | % | 없음 (그날 스냅샷) | 7.2 |

### 7.1 GET /stats/one-rm-trend

종목 하나의 추정 1RM(kg)이 날짜에 따라 어떻게 변했는지.

**쿼리 파라미터**

| 파라미터 | 규칙 |
|---|---|
| `exerciseId` | 필수. `measureType`이 `WEIGHT_REPS`가 아니면 `400 VALIDATION_ERROR` |
| `from`, `to` | 날짜 범위. 생략 시 최근 12주 |

**산출 규칙** (분석 1.3, LOG-07 — 서버가 전부 계산)

- 대상: 해당 종목의 `reps ≤ 12`인 세트 (본세트·워밍업 무관하나 워밍업은 중량이 낮아 자연 배제됨).
- 세트별 추정: Epley 공식 `1RM = weightKg × (1 + reps / 30)` (`reps = 1`이면 `weightKg`). 공식·`12` 경계는 서버 설정값.
- 그날 값 = 그날 세트 추정값 중 **최댓값**.
- `DONE` 세션만.

**응답 `200 OK`**

```json
{
  "exerciseId": 128,
  "exerciseName": "바벨 벤치프레스",
  "unit": "kg",
  "from": "2026-06-09",
  "to": "2026-09-01",
  "points": [
    {
      "date": "2026-06-10",
      "estimatedOneRm": 92.3,
      "basedOnSet": { "weightKg": 80.0, "reps": 5 }
    },
    {
      "date": "2026-06-17",
      "estimatedOneRm": 95.0,
      "basedOnSet": { "weightKg": 82.5, "reps": 5 }
    }
  ]
}
```

- `points`는 날짜 오름차순. 해당 종목 기록이 없는 날짜는 포함하지 않는다(선을 끊지 않고 이어 그림).
- `basedOnSet`: 그날 최댓값을 만든 세트(디버그·근거 표시용).

### 7.2 GET /stats/session-intensity/{sessionId}

특정 세션에서 각 세트가 **그날 그 종목 추정 1RM의 몇 %**였는지. 날짜 간 이어 그리지 않는 스냅샷.

**응답 `200 OK`**

```json
{
  "sessionId": 55,
  "performedOn": "2026-09-01",
  "exercises": [
    {
      "exerciseId": 128,
      "exerciseName": "바벨 벤치프레스",
      "measureType": "WEIGHT_REPS",
      "estimatedOneRm": 95.0,
      "sets": [
        { "setNo": 1, "weightKg": 60.0, "reps": 12, "isWarmup": true,  "intensityPct": 63 },
        { "setNo": 2, "weightKg": 70.0, "reps": 10, "isWarmup": false, "intensityPct": 74 },
        { "setNo": 3, "weightKg": 70.0, "reps": 9,  "isWarmup": false, "intensityPct": 74 }
      ]
    },
    {
      "exerciseId": 402,
      "exerciseName": "플랭크",
      "measureType": "TIME",
      "estimatedOneRm": null,
      "sets": [
        { "setNo": 1, "weightKg": null, "reps": null, "durationSec": 60, "isWarmup": false, "intensityPct": null }
      ]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `estimatedOneRm` | 그날 그 종목의 추정 1RM (7.1과 동일 규칙). `WEIGHT_REPS`가 아니거나 `reps ≤ 12` 세트가 없으면 `null` |
| `intensityPct` | `round(weightKg / estimatedOneRm × 100)`. `estimatedOneRm`이 `null`이면 `null`. 워밍업 세트도 표시함 |

---

## 8. 분석 API

### 8.1 판정 부위 매핑

판정 단위는 `exercises.primary_muscle` 기반 **9종**이다. 화면 표시는 `body_part` 기반 **상위 6종** / 판정 **하위 9종**의 2계층이다. (분석 2.3·2.4, LOG-09·LOG-10)

**하위 9종 ← `primary_muscle`**

| 판정 부위 (`key`) | 구성 `primary_muscle` |
|---|---|
| `CHEST` | `chest` |
| `BACK` | `lats`, `middle back`, `traps`, `lower back` |
| `DELT_FRONT` | `shoulders` 중 오버헤드 프레스·전면 레이즈·**측면 레이즈** 계열 |
| `DELT_REAR` | `shoulders` 중 후면 델트 계열 |
| `TRICEPS` | `triceps` |
| `BICEPS` | `biceps` |
| `QUADS` | `quadriceps`, `abductors`, `adductors` |
| `POSTERIOR` | `hamstrings`, `glutes` |
| `CORE` | `abdominals` |

**상위 6종 ↔ 하위 매핑** (상위 `key`는 `body_part`와 동일)

| 상위 `key` | `label` | 하위 |
|---|---|---|
| `CHEST` | 가슴 | `CHEST` |
| `BACK` | 등 | `BACK` |
| `SHOULDERS` | 어깨 | `DELT_FRONT`(어깨(앞)), `DELT_REAR`(어깨(뒤)) |
| `ARMS` | 팔 | `TRICEPS`(삼두), `BICEPS`(이두) |
| `LEGS` | 하체 | `QUADS`(앞허벅지), `POSTERIOR`(뒤허벅지·둔근) |
| `CORE` | 코어 | `CORE` |

**판정 제외 — 표시만** (분석 2.3): `calves`(종아리), `forearms`(전완). 부족 판정하지 않고 세트 수만 내려준다.

> **미해결 의존성**: `DELT_FRONT` / `DELT_REAR` 분리는 원본 데이터에 없다. `exercises`에 `movement_pattern`(또는 `delt_region`) 컬럼을 두거나 판정 로직에 종목 ID 매핑 상수를 두어야 한다. 10월 분석 로직 구현 시 확정(분석 4.3 미해결, LOG-09 한계 인지). **분석 API는 이 매핑이 확보돼야 정확히 동작한다.** 그 전까지 `shoulders`는 전량 `DELT_FRONT`로 임시 집계하고 응답에 `"shoulderSplitResolved": false` 플래그를 포함한다.

### 8.2 GET /analysis/muscle-volume

부위별 볼륨과 4단계 부족 판정을 **2계층 구조로 완성해서** 내려준다. 화면은 합산·구간 분류·배지 계산을 하지 않는다.

**쿼리 파라미터**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `weeks` | `4` | 집계 기간(주). 설정값, 보통 고정 |
| `referenceDate` | 오늘 | 이 날짜로부터 `weeks × 7`일 이전까지 집계 (테스트·과거 시점 조회용) |

**집계 조건** (전부 서버에서 적용)

- `workout_sessions.status = 'DONE'` (`DRAFT` 제외)
- `workout_sets.is_warmup = false` (본세트만)
- 기간: `performed_on ∈ [referenceDate - weeks×7일, referenceDate]` (기록 저장 시각 아님)
- `source = 'BACKFILL'` 세션 **포함** (실제 수행한 운동)
- 세트 → 판정 부위: 종목의 `primary_muscle` 기준, 주동근에만 카운트(간접 자극 미반영)
- 주당 평균 = `기간 내 세트 합 ÷ weeks`

**4단계 판정** (`verdict`, 분석 2.2)

| 주당 평균 세트 | `verdict` | `verdictLabel` |
|---|---|---|
| `< 4` | `INSUFFICIENT` | 부족 |
| `4 ~ 10` | `BELOW_RECOMMENDED` | 권장 이하 |
| `10 ~ 20` | `OPTIMAL` | 최적 |
| `> 20` | `EXCESSIVE` | 과다 |

**응답 `200 OK`**

```json
{
  "referenceDate": "2026-09-01",
  "periodWeeks": 4,
  "periodFrom": "2026-08-04",
  "periodTo": "2026-09-01",
  "shoulderSplitResolved": false,
  "confidence": {
    "level": "LOW",
    "doneSessionCount": 4,
    "threshold": 6,
    "message": "최근 4주 완료된 운동이 4회로 적어 판정 신뢰도가 낮습니다."
  },
  "tiers": [
    {
      "key": "CHEST",
      "label": "가슴",
      "weeklySets": 12.0,
      "totalSets": 48,
      "hasChildren": false,
      "verdict": "OPTIMAL",
      "verdictLabel": "최적",
      "summaryBadge": null,
      "summaryBadgeLabel": null,
      "children": [
        { "key": "CHEST", "label": "가슴", "weeklySets": 12.0, "totalSets": 48, "verdict": "OPTIMAL", "verdictLabel": "최적" }
      ]
    },
    {
      "key": "SHOULDERS",
      "label": "어깨",
      "weeklySets": 9.0,
      "totalSets": 36,
      "hasChildren": true,
      "verdict": null,
      "verdictLabel": null,
      "summaryBadge": "PARTIAL_INSUFFICIENT",
      "summaryBadgeLabel": "일부 부족",
      "children": [
        { "key": "DELT_FRONT", "label": "어깨(앞)", "weeklySets": 8.0, "totalSets": 32, "verdict": "BELOW_RECOMMENDED", "verdictLabel": "권장 이하" },
        { "key": "DELT_REAR",  "label": "어깨(뒤)", "weeklySets": 1.0, "totalSets": 4,  "verdict": "INSUFFICIENT",     "verdictLabel": "부족" }
      ]
    },
    {
      "key": "ARMS",
      "label": "팔",
      "weeklySets": 12.0,
      "totalSets": 48,
      "hasChildren": true,
      "verdict": null,
      "verdictLabel": null,
      "summaryBadge": "PARTIAL_INSUFFICIENT",
      "summaryBadgeLabel": "일부 부족",
      "children": [
        { "key": "TRICEPS", "label": "삼두", "weeklySets": 0.0,  "totalSets": 0,  "verdict": "INSUFFICIENT", "verdictLabel": "부족" },
        { "key": "BICEPS",  "label": "이두", "weeklySets": 12.0, "totalSets": 48, "verdict": "OPTIMAL",      "verdictLabel": "최적" }
      ]
    }
  ],
  "displayOnly": [
    { "key": "CALVES",   "label": "종아리", "weeklySets": 2.0, "totalSets": 8 },
    { "key": "FOREARMS", "label": "전완",   "weeklySets": 0.0, "totalSets": 0 }
  ]
}
```

**필드 정의**

| 필드 | 설명 |
|---|---|
| `periodFrom` / `periodTo` | 실제 집계 구간 (화면 표기용, 서버가 계산) |
| `shoulderSplitResolved` | `DELT_FRONT`/`DELT_REAR` 분리 매핑 확보 여부 (8.1의 미해결 의존성). `false`면 어깨 하위 판정을 참고치로만 표시하도록 화면에 신호 |
| `confidence.level` | `LOW` \| `NORMAL`. `doneSessionCount < threshold`이면 `LOW` |
| `confidence.threshold` | 최근 4주 DONE 세션 임계값. 서버 설정값(기본 6, 기록 방식 5.5) |
| `tiers[]` | **상위 6종**. 항상 6개, 고정 순서(`CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE`) |
| `tiers[].weeklySets` | 하위 합의 주당 평균 (서버가 합산). 소수 1자리 |
| `tiers[].totalSets` | 기간 내 총 세트 수 |
| `tiers[].hasChildren` | 하위가 2개면 `true`(어깨·팔·하체), 1개면 `false`(가슴·등·코어) |
| `tiers[].verdict` | **하위가 1개일 때만** 값. 하위가 2개면 `null` (상위엔 판정 라벨 안 붙임 — 분석 2.4) |
| `tiers[].summaryBadge` | **하위가 2개일 때만** 값. 하위 중 가장 나쁜 상태 요약 (아래 표) |
| `tiers[].children[]` | **하위 9종**. 각 항목에 `verdict` 항상 존재. 이것이 실제 판정 단위 |
| `displayOnly[]` | 종아리·전완. `verdict` 없음 |

**`summaryBadge` enum** (서버가 하위 상태로 결정)

| `summaryBadge` | `summaryBadgeLabel` | 조건 |
|---|---|---|
| `ALL_OPTIMAL` | 모두 최적 | 하위 전부 `OPTIMAL` |
| `PARTIAL_BELOW` | 일부 권장 이하 | 하위 중 최악이 `BELOW_RECOMMENDED` |
| `PARTIAL_INSUFFICIENT` | 일부 부족 | 하위 중 하나 이상 `INSUFFICIENT` |
| `PARTIAL_EXCESSIVE` | 일부 과다 | 하위 중 최악이 `EXCESSIVE`, 부족·권장이하 없음 |
| `MIXED` | 확인 필요 | 하위가 서로 반대 방향(한쪽 `INSUFFICIENT`, 한쪽 `EXCESSIVE`) |

> 우선순위: `INSUFFICIENT` > `EXCESSIVE`(+`INSUFFICIENT` 동반 시 `MIXED`) > `BELOW_RECOMMENDED` > `ALL_OPTIMAL`.
> **미확정**: 요약 배지의 정확한 문안, `MIXED` 표기는 화면 구현 시 확정(분석 2.4). enum `key`는 유지하고 `label`만 조정될 수 있다.

**화면이 하지 않아도 되는 것** (서버가 완성)
- 하위 → 상위 세트 합산
- 주당 평균 환산, 4구간 분류
- 요약 배지 결정
- 신뢰도 판정
- 집계 기간 문자열 산출

### 8.3 GET /analysis/balance

밀기/당기기, 상체/하체 균형(분석 4장). 계산 재료는 8.2와 동일한 최근 4주 주당 평균 세트.

**쿼리 파라미터**: `weeks`(기본 4), `referenceDate`(기본 오늘) — 8.2와 동일.

**구성** (분석 4.3, LOG-10)

```
밀기   = CHEST + DELT_FRONT + TRICEPS
당기기 = BACK  + DELT_REAR  + BICEPS
상체   = 밀기 + 당기기
하체   = QUADS + POSTERIOR + 종아리(calves)   ← 종아리 포함
비율   = 큰 쪽 ÷ 작은 쪽,  2.0 초과 시 불균형
```

**응답 `200 OK`**

```json
{
  "referenceDate": "2026-09-01",
  "periodWeeks": 4,
  "ratioThreshold": 2.0,
  "shoulderSplitResolved": false,
  "pairs": [
    {
      "key": "PUSH_PULL",
      "label": "밀기 / 당기기",
      "left":  { "key": "PUSH", "label": "밀기",   "weeklySets": 20.0, "components": ["CHEST", "DELT_FRONT", "TRICEPS"] },
      "right": { "key": "PULL", "label": "당기기", "weeklySets": 8.0,  "components": ["BACK", "DELT_REAR", "BICEPS"] },
      "biggerSide": "PUSH",
      "ratio": 2.5,
      "smallerSideZero": false,
      "verdict": "IMBALANCED",
      "verdictLabel": "불균형"
    },
    {
      "key": "UPPER_LOWER",
      "label": "상체 / 하체",
      "left":  { "key": "UPPER", "label": "상체", "weeklySets": 28.0, "components": ["CHEST", "BACK", "DELT_FRONT", "DELT_REAR", "TRICEPS", "BICEPS"] },
      "right": { "key": "LOWER", "label": "하체", "weeklySets": 18.0, "components": ["QUADS", "POSTERIOR", "CALVES"] },
      "biggerSide": "UPPER",
      "ratio": 1.56,
      "smallerSideZero": false,
      "verdict": "BALANCED",
      "verdictLabel": "정상"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `ratio` | `round(큰 쪽 / 작은 쪽, 2)`. 작은 쪽이 0이면 `null` |
| `smallerSideZero` | 작은 쪽 세트가 0일 때 `true` → `verdict`는 `IMBALANCED`(큰 쪽 > 0인 경우), 화면은 "비율 계산 불가"로 표기 |
| `verdict` | `BALANCED`(≤ 2.0) \| `IMBALANCED`(> 2.0). 양쪽 다 0이면 `INSUFFICIENT_DATA` |
| `components` | 합산에 쓰인 하위 판정 부위 `key` 목록 (근거 표시용) |

### 8.4 LLM 분석 해설 (참고 — 9월 범위 밖)

분석 로직 설계서 7.3의 "LLM 기반 분석 평가"는 위 8.2·8.3 응답을 입력으로 자연어 해설을 생성한다. 별도 엔드포인트(`GET /analysis/commentary` 등)로 10월에 명세한다. 판정 수치는 자체 로직이 확정하고 LLM은 설명만 한다는 원칙에 따라, 본 명세의 응답 구조가 그대로 LLM 입력 계약이 된다.

### 8.5 서버 계산 상수 (설정값)

| 항목 | 값 | 근거 |
|---|---|---|
| 판정 집계 기간 | 4주 (28일), `performed_on` 기준 | 분석 2.1 |
| 부족 임계 | 주 4 / 10 / 20 세트 | 분석 2.2 |
| 균형 비율 임계 | 2.0배 | 분석 4.2 |
| 신뢰도 임계 | 최근 4주 `DONE` 세션 6회 | 기록 방식 5.5 |
| 1RM 대상 | `WEIGHT_REPS`, `reps ≤ 12`, 일자별 최댓값 | 분석 1.3, LOG-07 |
| 1RM 공식 | Epley `w × (1 + reps/30)` | 분석 1.3 (예시) |
| 운동 시간: 세트 간격 캡 | 900초 (15분) | 기록 방식 4.2 |
| 운동 시간: 마지막 세트 보정 | +90초 | 기록 방식 4.2 |
| 운동 시간: 세션 상한 | 14400초 (4시간) | 기록 방식 4.2 |
| `BACKFILL` 세션 `durationSec` | `null` (시간 집계 제외, 횟수는 포함) | 기록 방식 4.3 |

> 모든 값은 `application.yaml`의 `app.analysis.*` 프로퍼티로 노출해 코드 수정 없이 조정 가능하게 한다.

---

## 9. 미해결·후속 과제

| 항목 | 내용 | 시점 |
|---|---|---|
| 어깨 전·후면 분리 데이터 | `exercises.movement_pattern` 컬럼 추가 or 종목 ID 매핑 상수. 분석 API 정확도의 전제 (8.1) | 10월 분석 로직 구현 |
| `V3__auth_and_profile.sql` | `refresh_tokens` 테이블 + `users` 프로필 4컬럼 (2.4) | 9월 1주, 인증 구현 전 |
| `routines` FK | `workout_sessions.routine_id`는 컬럼만 존재. `routines` 생성 시 FK 마이그레이션 | 10월 |
| 요약 배지 문안 | `summaryBadge` label 및 `MIXED` 표기 확정 (8.2) | 화면 구현 시 |
| QUADS 화면 명칭 | `abductors`·`adductors` 포함이라 "앞허벅지"가 부정확 (LOG-10 한계) | 종목 정제 결과 확인 후 |
| 우선순위 산출 | 여러 부위 동시 부족·불균형 시 보완 순서 (분석 8장) | 10월 |
| 이메일·비밀번호 변경 API | 9월 범위 제외 | 필요 시 |
| Rate limiting | `429 RATE_LIMITED` 코드만 예약. 실제 도입은 배포 후 판단 | 미정 |

---

## 부록 A. Enum 정의 모음

| 그룹 | 값 |
|---|---|
| `session.status` | `DRAFT`, `DONE` |
| `session.source` | `LIVE`, `BACKFILL` |
| `exercise.bodyPart` | `CHEST`, `BACK`, `LEGS`, `SHOULDERS`, `ARMS`, `CORE` |
| `exercise.pushPull` | `PUSH`, `PULL`, `NONE` |
| `exercise.measureType` | `WEIGHT_REPS`, `BODYWEIGHT_REPS`, `WEIGHTED_BODYWEIGHT`, `TIME` |
| `exercise.equipment` | `BARBELL`, `DUMBBELL`, `MACHINE`, `CABLE`, `BODYWEIGHT`, `PULLUP_BAR` |
| `user.experienceLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `user.goal` | `STRENGTH`, `HYPERTROPHY`, `ENDURANCE`, `GENERAL_FITNESS` |
| 판정 부위 상위 (`tier.key`) | `CHEST`, `BACK`, `SHOULDERS`, `ARMS`, `LEGS`, `CORE` |
| 판정 부위 하위 (`child.key`) | `CHEST`, `BACK`, `DELT_FRONT`, `DELT_REAR`, `TRICEPS`, `BICEPS`, `QUADS`, `POSTERIOR`, `CORE` |
| 표시만 (`displayOnly.key`) | `CALVES`, `FOREARMS` |
| `verdict` | `INSUFFICIENT`, `BELOW_RECOMMENDED`, `OPTIMAL`, `EXCESSIVE` |
| `summaryBadge` | `ALL_OPTIMAL`, `PARTIAL_BELOW`, `PARTIAL_INSUFFICIENT`, `PARTIAL_EXCESSIVE`, `MIXED` |
| `balance.verdict` | `BALANCED`, `IMBALANCED`, `INSUFFICIENT_DATA` |
| 에러 `code` | 1.5의 표 참조 |

---

## 부록 B. 인증이 필요 없는 엔드포인트

| 엔드포인트 |
|---|
| `POST /auth/signup` |
| `POST /auth/login` |
| `POST /auth/refresh` (리프레시 쿠키로 인증) |
| `GET /exercises`, `GET /exercises/{id}` |
| `GET /actuator/health` |

나머지 모든 엔드포인트는 유효한 액세스 토큰이 필요하며, 리소스 소유자 본인만 접근할 수 있다.

---

*본 명세는 설계 단계 산출물이며, 9월 구현 과정에서 필드·경로가 조정될 수 있다. 변경은 「설계 변경 로그」에 반영한다.*
