# API 명세서
### 운동 습관 분석 기반 맞춤 루틴 추천 헬스 웹앱(PWA)

> 「운동 기록 방식 설계서」 10장 "다음 설계 과제 — API 명세"에 대응하는 산출물이다.
> 작성 시점: 2026년 8월 5주 (프론트–백엔드 연동 직전)
> 개정: 2026-09-02 (LOG-13·14·16 반영) / 2026-09-04 (정합성 정정 — 커밋 `c824be6`)
> 대상 범위: **9월 1~3주 구현분** — 인증·프로필 / 운동 기록 / 통계·분석
> 제외 범위: 뱃지(UC-04), 알림(UC-06) — 별도 명세
> 추가: 친구 그룹(UC-05)은 10장 (2026-10-11, LOG-38)
> 개정: 2026-10-11 하루 기록(LOG-39) — 시작·종료를 없애고 하루에 기록 하나. 6장 대부분, 1.5·3.3·5.5·8.2·8.5·10장. 엔드포인트 46→44개
> 추가: 오늘의 루틴(UC-03-03~06)은 11장 (2026-10-24, LOG-40). 1단계 규칙 루틴 — 엔드포인트 44→52개

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
| 친구 그룹 | UC-05-01 ~ UC-05-07 | 10장 |
| 오늘의 루틴 (추천·후보정·만족도) | UC-03-03 ~ UC-03-06 | 11장 |

### 0.2 설계 결정 반영 대응표

본 명세가 반영한 상위 설계 결정과 해당 위치는 다음과 같다. 각 항목의 근거는 참조 문서에 있으며 여기서는 반복하지 않는다.

| 설계 결정 | 출처 | 반영 위치 |
|---|---|---|
| 세트는 완료 체크 시점에만 INSERT (계획 행 미저장, `is_completed` 없음) | 기록 방식 3.3, LOG-05 | 6.4 세트 저장 |
| 기록은 사용자의 하루에 하나, 시작·종료 없음. 세트가 있는 날이 운동한 날 (~~DRAFT / DONE~~) | 기록 방식 2.1·5.1, LOG-39 | 6.1·6.2, 8장 집계 조건 |
| 볼륨 집계는 본세트만 (`is_warmup = false`) | 분석 1.2, LOG-02 | 8.2 |
| 사후 입력은 볼륨에 포함, 시간 집계에서만 제외 (그 날짜에 시작하지 않은 구간은 시간에서 빠진다) | 기록 방식 4.3, LOG-39 | 6.5, 8.2 |
| 운동 시간은 세트 저장 시각으로 읽을 때 계산 (30분 넘는 공백에서 구간 분할, 하루 4시간 상한) | 기록 방식 4.2, LOG-39 | 6.5, 8.5 |
| 추정 1RM은 `WEIGHT_REPS` 종목의 `reps ≤ 12` 세트, 일자별 최댓값 | 분석 1.3, LOG-07 | 7.1, 8.5 |
| 판정 부위 `primary_muscle` 기반 9종, 화면 표시는 상위 6종 / 하위 9종 2계층 | 분석 2.3·2.4, LOG-09·LOG-10 | 8.1, 8.2 |
| 판정 결과는 서버가 완성해서 내려줌 (화면이 합산·분류하지 않음) | LOG-10 | 8.2 |
| 최근 4주 운동한 날 수가 임계 미만이면 신뢰도 낮음 표시 | 기록 방식 5.5 | 8.2 `confidence` |
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
| 주 시작 요일 | **월요일** (ISO 8601). 홈 주간 집계·기록 캘린더·"이번 주" 표기가 모두 이 기준을 쓴다. 분석 API의 4주 집계는 요일과 무관한 28일 롤링이므로 이 규정의 영향을 받지 않는다 |
| 날짜·시각 | ISO 8601 오프셋 포함 (예: `2026-09-01T19:32:11+09:00`). 서버 타임존 `Asia/Seoul` (`application.yaml`의 `spring.jackson.time-zone`) |
| 숫자 ID | JSON number (`BIGSERIAL`, 2^53 미만이므로 정밀도 문제 없음) |
| 중량 | `weightKg` — 소수 2자리까지 (`NUMERIC(6,2)`, 예: `62.5`, `62.25`) |
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
| 401 | `GOOGLE_LOGIN_FAILED` | 구글 ID 토큰 검증 실패, 또는 구글이 확인하지 않은 이메일 (4.9, LOG-37) |
| 403 | `ACCESS_DENIED` | 타 사용자 리소스 접근 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 리소스 없음 |
| 409 | `EMAIL_ALREADY_EXISTS` | 회원가입 이메일 중복. 구글 로그인에서 그 이메일이 이미 다른 구글 계정에 묶여 있을 때도 (4.9) |
| 409 | `DAY_ALREADY_RECORDED` | 기록의 날짜를 옮기려는데 그 날짜에 기록이 이미 있음 (6.9, LOG-39) |
| 409 | `GROUP_LIMIT_EXCEEDED` | 그룹 인원 20명 또는 한 사람의 그룹 10개 초과 (10장) |
| 409 | `ROUTINE_FEEDBACK_EXISTS` | 이미 만족도를 답했거나 건너뛴 루틴 (11.8) |
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
- **리프레시 토큰은 DB에 저장**한다(2.4의 `refresh_tokens` 테이블). 재발급 시 **회전(rotation)**: 기존 토큰을 폐기하고 새 토큰을 발급한다. 재사용이 감지되면 해당 사용자의 모든 리프레시 토큰을 폐기한다 — 다만 폐기 직후 유예 창(기본 30초) 안의 재사용은 동시 재발급으로 보고 그 요청만 거부한다(4.3, LOG-16).
- **로그아웃**: 서버가 리프레시 토큰에 **폐기 표시**(`revoked_at`)를 남기고 쿠키를 만료시킨다. 행을 지우지 않는 이유는 재사용 감지 때문이다 — 삭제하면 되돌아온 토큰이 "DB에 없음"과 구별되지 않아 탈취를 판별할 수 없다. 액세스 토큰은 남은 만료 시간(최대 30분) 동안 유효하다 — 이 시간 창은 감수한다.
- **서명 키**는 환경변수(`JWT_SECRET`)로 주입한다. `.env.example`에 항목을 추가한다.
- 재발급 시 새 리프레시 토큰의 만료가 다시 14일로 잡힌다. **계속 쓰는 사용자는 로그인이 끊기지 않는다** — 회전은 로그인 유지 기간을 줄이지 않는다.

### 2.4 필요한 스키마 추가

9월 1주에 아래 네 항목을 담은 마이그레이션 `V3__auth_profile_exercise_meta.sql`을 만든다. `V1__init.sql`에 없는 항목이며, `V2`는 이미 `equipment` 제약에 사용됨. (2·3번은 인증과 무관하지만, 종목 데이터·9월 화면이 의존하므로 같은 마이그레이션에서 함께 처리한다.)

> **확인 완료**: `workout_sessions.memo`(TEXT)와 `duration_override_sec`(INT)는 `V1__init.sql`에 이미 존재한다. 6.9의 메모·시간 수동 보정은 추가 스키마 없이 동작한다.

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

`users`에는 현재 `email / password_hash / nickname`만 있다. 프로필 항목 **훈련 목표 1종**을 위해 컬럼을 추가한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `goal` | VARCHAR(20) NULL | `STRENGTH` / `HYPERTROPHY` / `ENDURANCE` / `GENERAL_FITNESS` |

> 컬럼 하나이므로 별도 `user_profiles` 테이블을 두지 않고 `users`에 인라인한다. 회원가입 후 선택 입력이므로 NULL 허용.

> **프로필은 훈련 목표 1종이다 — 키·체중·경력을 모두 제외한다.**
>
> 판단 기준은 LOG-08이 `equipment`를 미리 확보할 때 쓴 **비용 비대칭**이다. 종목 데이터는 나중에 컬럼을 추가하면 800여 종목을 재검토해야 하므로 미리 넣는 것이 옳았다. 반면 **프로필 컬럼에는 그 비대칭이 없다** — 필요해지면 NULL 허용 컬럼 하나와 폼 필드 하나를 추가하면 되고, 소급해서 채워야 할 과거 데이터도 없다. 미리 확보할 이유가 성립하지 않는다.
>
> - `experience_level`(경력): 분석 판정이 ACSM 절대 기준을 따라 경력을 참조하지 않고, 예상되는 사용처가 없다.
> - `height_cm`(키): **어떤 로직도 참조하지 않는다.** BMI 등 체중 관리 지표는 이 앱의 범위 밖이다(목표축을 체중이 아닌 훈련 목표로 교체했다).
> - `weight_kg`(체중): 예상 사용처는 `WEIGHTED_BODYWEIGHT` 종목의 실제 부하 산출뿐인데 해당 종목이 **109종 중 2종**(중량 풀업·중량 벤치 딥스)이고, 단일 스냅샷이라 과거 기록에 소급 적용하면 부정확하다. 실제로 쓰려면 체중 이력 테이블이 필요하다.
> - `goal`(훈련 목표): **존치.** 루틴 추천의 출력 형식이 "운동명·세트·**반복 횟수**·순서"(기능명세 2.1)인데, 반복 횟수와 강도 범위는 ACSM이 목표별로 다르게 제시한다. 목표를 모르면 정할 근거가 없다. 셋 중 유일하게 출력이 그 값을 요구한다.
>
> 지인 10명에게 배포하면서 **쓰지 않을 신체 정보를 수집하지 않는다**는 점도 함께 고려했다.
>
> **UC-01-03·계획서가 명시한 프로필 항목("키/체중/경력/목표")과 다르며**, 경력 제외는 LOG-11에, 키·체중 제외는 LOG-14에 기록한다.

**(3) `exercises.delt_region` — 기존 테이블 확장**

`primaryMuscle = shoulders`인 종목을 `DELT_FRONT` / `DELT_REAR`로 나누는 근거 컬럼(8.1). 이 분류는 8월 8일 종목 정제 때 이미 판단이 끝났고(LOG-09: `DELT_FRONT` 10종목 / `DELT_REAR` 3종목), 저장할 자리만 없었다. 폴백(전량 `DELT_FRONT` 집계)을 쓰면 전 사용자에게 "어깨(뒤) 부족"과 당기기 불균형 경고가 상시 뜨므로 — LOG-09가 전완을 판정에서 뺀 이유와 같은 상황 — 10월로 미루지 않고 여기서 확보한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `delt_region` | VARCHAR(10) NULL | `FRONT` / `REAR`. `primary_muscle = 'shoulders'`인 종목에만 값. 그 외 종목은 NULL |

`primary_muscle = 'shoulders'`인 13개 종목에만 값을 채운다. 배분 기준은 LOG-09의 "어깨 전·측면·후면 분리 방식" 표(오버헤드 프레스·전면 레이즈·측면 레이즈 → `FRONT`, 후면 델트 → `REAR`)를 따른다.

**(4) `user_favorite_exercises` — 신규 테이블**

종목 즐겨찾기 별표(기록 방식 3.4). 이력 기반 "최근 사용"과 별개로, 사용자가 직접 토글하는 값이므로 저장이 필요하다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT FK → users ON DELETE CASCADE | |
| `exercise_id` | BIGINT FK → exercises ON DELETE CASCADE | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | 즐겨찾기 탭 정렬 기준 |

- `UNIQUE (user_id, exercise_id)` — 중복 방지
- 인덱스: `(user_id, created_at DESC)` — 즐겨찾기 탭 조회

---

## 3. 엔드포인트 목록

### 3.1 인증·프로필

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/auth/signup` | 회원가입 (가입 후 토큰 발급) | — |
| POST | `/auth/login` | 로그인 | — |
| POST | `/auth/google` | **구글 로그인** (처음이면 가입까지, LOG-37) | — |
| POST | `/auth/refresh` | 액세스 토큰 재발급 (리프레시 쿠키 사용) | 쿠키 |
| POST | `/auth/logout` | 로그아웃 (리프레시 토큰 폐기) | ✔ |
| GET | `/users/me` | 내 프로필 조회 | ✔ |
| PATCH | `/users/me` | 닉네임·프로필 수정 | ✔ |
| PATCH | `/users/me/password` | 비밀번호 변경 (현재 비밀번호 확인) | ✔ |
| DELETE | `/users/me` | **회원 탈퇴** (계정·기록 전부 삭제) | ✔ |

### 3.2 운동 종목

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/exercises` | 종목 목록·검색 (필터·페이지네이션, `favorite` 필터) | — |
| GET | `/exercises/{id}` | 종목 단건 조회 | — |
| GET | `/exercises/{id}/last-performance` | 이 종목의 **직전 수행 기록** (종목 프리필용) | ✔ |
| PUT | `/users/me/favorite-exercises/{exerciseId}` | 즐겨찾기 추가 (멱등) | ✔ |
| DELETE | `/users/me/favorite-exercises/{exerciseId}` | 즐겨찾기 해제 (멱등) | ✔ |

### 3.3 운동 기록 (하루 기록·세트)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/workout-sessions` | **그날 기록 열기** — 없으면 만들고 있으면 그 기록 (LOG-39) | ✔ |
| GET | `/workout-sessions` | 히스토리 목록 (운동한 날, 기간·종목 필터) | ✔ |
| GET | `/workout-sessions/calendar` | 월별 운동일 요약 (캘린더 렌더링용) | ✔ |
| GET | `/workout-sessions/{id}` | 하루 기록 상세 (세트 포함) | ✔ |
| PATCH | `/workout-sessions/{id}` | 메모·날짜·시간 수동 보정 수정 | ✔ |
| DELETE | `/workout-sessions/{id}` | 하루 기록 삭제 (세트 포함 CASCADE) | ✔ |
| POST | `/workout-sessions/{id}/sets` | 세트 저장 (완료 체크 시점) | ✔ |
| PATCH | `/workout-sessions/{id}/sets/{setId}` | 세트 수정 | ✔ |
| DELETE | `/workout-sessions/{id}/sets/{setId}` | 세트 삭제 | ✔ |
| DELETE | `/workout-sessions/{id}/exercises/{exerciseId}` | 종목 단위 삭제 (LOG-22) | ✔ |

> ~~`POST /workout-sessions/{id}/complete`~~(세션 종료), ~~`GET /workout-sessions/current`~~(진행 중 세션)는 **삭제했다**(LOG-39). 종료가 없어 둘 다 할 일이 없다.

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

### 3.6 친구 그룹 (10장, LOG-38)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/groups` | 그룹 만들기 (만든 사람이 방장) | ✔ |
| GET | `/groups` | 내 그룹 목록 | ✔ |
| GET | `/groups/{groupId}` | 그룹 상세 — 멤버별 오늘·이번 주 | ✔ 멤버 |
| PATCH | `/groups/{groupId}` | 이름 바꾸기 | ✔ 방장 |
| DELETE | `/groups/{groupId}` | 그룹 삭제 | ✔ 방장 |
| POST | `/groups/{groupId}/invite-code` | 초대코드 다시 만들기 | ✔ 방장 |
| GET | `/groups/invite/{code}` | 초대 미리보기 (참여 전) | ✔ |
| POST | `/groups/join` | 코드로 참여 | ✔ |
| DELETE | `/groups/{groupId}/members/me` | 그룹 나가기 | ✔ 멤버 |
| DELETE | `/groups/{groupId}/members/{memberId}` | 멤버 내보내기 | ✔ 방장 |
| GET | `/groups/{groupId}/members/{memberId}/calendar` | 멤버의 월별 기록 요약 | ✔ 멤버 |
| GET | `/groups/{groupId}/members/{memberId}/days/{date}` | 멤버의 하루 기록 (메모 제외) | ✔ 멤버 |
| PUT | `/groups/{groupId}/sessions/{sessionId}/cheer` | 응원하기 | ✔ 멤버 |
| DELETE | `/groups/{groupId}/sessions/{sessionId}/cheer` | 응원 취소 | ✔ 멤버 |

### 3.7 오늘의 루틴 (11장, LOG-40)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/routines/today` | 오늘 루틴 만들기 (이미 있으면 그 루틴) | ✔ |
| GET | `/routines/today` | 오늘 루틴 | ✔ |
| GET | `/routines/feedback-pending` | 만족도를 물을 지난 루틴 | ✔ |
| GET | `/routines/{routineId}` | 루틴 상세 | ✔ |
| PATCH | `/routines/{routineId}/items/{itemId}` | 후보정 — 종목 교체·세트 조정 | ✔ |
| DELETE | `/routines/{routineId}/items/{itemId}` | 후보정 — 종목 빼기 | ✔ |
| GET | `/routines/{routineId}/items/{itemId}/alternatives` | 교체 후보 | ✔ |
| PUT | `/routines/{routineId}/feedback` | 만족도 답하기·건너뛰기 | ✔ |

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
| 폐기된 토큰 재사용 감지 | `401 TOKEN_INVALID` + 해당 사용자 전체 리프레시 토큰 폐기 (유예 창 밖일 때만 — 아래) |

> **재사용 감지의 유예 창** (2026-09-02 확정, LOG-16). 폐기된 토큰이 다시 온 경우를 무조건 탈취로 보지 않는다. **폐기 시각으로부터 `app.jwt.reuse-grace`(기본 30초) 안이면 동시 재발급으로 보고 그 요청만 거부**하며, 전체 폐기는 하지 않는다.
>
> 액세스 토큰이 만료되는 순간 여러 요청이 함께 `401`을 받아 재발급이 두 번 발사될 수 있고, 이 앱은 운동 중 세트마다 요청을 보내므로 그 상황이 잦다. 동시 요청은 밀리초 단위로 붙어 오지만 탈취는 그렇지 않으므로 시간으로 가른다.
>
> **클라이언트 요구사항**: `/auth/refresh`는 **한 번에 하나만** 보낸다. API 클라이언트가 재발급을 하나로 묶고(single-flight), 그 사이 `401`을 받은 다른 요청들은 결과를 기다렸다 재시도한다. 이 장치가 없으면 유예 창만으로는 부족하다.
>
> **어떤 401에 재발급을 시도하는가** (2026-09-14 확정, c1 구현 중). 401이면 무조건 재발급하는 것이 아니라 `code`로 가른다.
>
> | `code` | 재발급 | 이유 |
> |---|---|---|
> | `TOKEN_EXPIRED` | ✔ | 만료. 재발급이 정확히 이 경우를 위한 것이다 |
> | `AUTHENTICATION_REQUIRED` | ✔ | 헤더를 붙이지 못했다. 액세스를 메모리에만 두므로(2.3) 새로고침 직후가 이에 해당한다 |
> | `TOKEN_INVALID` | ✘ | 서명이 맞지 않는 토큰을 들고 있었다는 뜻이라 재발급으로 수습할 상황이 아니다. 반복하면 위의 재사용 감지를 자극한다 — 로그인 화면으로 보낸다 |
>
> 재시도는 **요청당 한 번**이다. 재발급 후 보낸 요청이 또 `401`이면 그대로 실패시킨다.
>
> 실패 응답에는 `Set-Cookie: refreshToken=; Max-Age=0`을 함께 보낸다. 쿠키를 남겨두면 클라이언트가 죽은 토큰으로 재시도를 반복하고, 그 재시도가 감지에 걸려 상황을 악화시킨다.

### 4.4 POST /auth/logout

**요청**: 본문 없음. `Authorization` 헤더 + 리프레시 쿠키.

**응답 `204 No Content`** — 리프레시 토큰에 폐기 표시(`revoked_at`)를 남기고 쿠키를 만료시킨다(`Set-Cookie: refreshToken=; Max-Age=0`). 행을 삭제하지 않는 이유는 2.3에 있다.

### 4.5 GET /users/me

**응답 `200 OK`**

```json
{
  "userId": 42,
  "email": "user@example.com",
  "nickname": "민석",
  "profile": {
    "goal": "HYPERTROPHY"
  },
  "loginMethods": {
    "password": true,
    "google": false
  },
  "createdAt": "2026-09-01T09:12:00+09:00"
}
```

- 프로필 미입력 시 `profile.goal`은 `null`.
- `loginMethods`는 이 계정으로 로그인하는 방법이다(LOG-37). 구글로만 가입하면 `password: false`이고, 화면은 비밀번호 변경을 숨기고 탈퇴 확인을 구글로 받는다(4.7). 이메일로 가입한 뒤 같은 이메일의 구글로 들어오면 둘 다 `true`다.
- `profile`을 객체로 감싸 둔 것은 향후 항목이 늘어날 때 최상위 필드가 흩어지지 않게 하기 위함이다. 현재 항목은 `goal` 하나다.

### 4.6 PATCH /users/me

부분 수정. 포함된 필드만 갱신한다.

**요청 (예)**

```json
{
  "nickname": "민석2",
  "profile": {
    "goal": "STRENGTH"
  }
}
```

| 필드 | 규칙 |
|---|---|
| `nickname` | 1~50자 |
| `profile.goal` | enum (부록 A). `null`로 보내면 미설정으로 되돌린다 |

**응답 `200 OK`** — 4.5와 동일한 전체 프로필.

> 프로필 항목은 **훈련 목표 1종**이다. UC-01-03이 명시한 "경력"(LOG-11)에 이어 "키·체중"도 제외했다. 근거는 2.4-(2) — 어떤 로직도 참조하지 않고, 프로필 컬럼은 나중에 추가해도 비용이 같다.

> **정정 (2026-09-14, LOG-21)**: 비밀번호 변경은 `PATCH /users/me/password`로 **9월 범위에 추가**한다(4.8). 이메일 변경은 계속 제외한다 — 이메일은 로그인 식별자라 바꾸려면 중복 검사와 기존 세션 처리가 따라붙는데, 지인 10명 검증에서 이메일을 바꿀 이유가 없다.

### 4.7 DELETE /users/me

회원 탈퇴. 계정과 그에 딸린 모든 기록을 **되돌릴 수 없게** 삭제한다.

**요청**

```json
{ "password": "hunter2hunter2" }
```

또는 구글로만 가입한 계정:

```json
{ "googleCredential": "<구글 ID 토큰>" }
```

| 필드 | 규칙 |
|---|---|
| `password` | 현재 비밀번호를 재확인한다 |
| `googleCredential` | 구글 로그인을 한 번 더 해서 받은 ID 토큰. 그 토큰의 구글 회원번호가 이 계정에 묶인 것과 같아야 한다 (LOG-37) |

둘 중 하나는 있어야 한다. 비밀번호와 구글을 다 가진 계정은 어느 쪽으로든 확인할 수 있다 — 구글을 묶은 뒤 비밀번호를 잊은 사람도 탈퇴할 수 있어야 한다.

> 본인 확인을 다시 받는 것은 액세스 토큰만으로 실행되는 되돌릴 수 없는 동작이기 때문이다. 화면에서도 확인 다이얼로그를 한 번 더 둔다.

**응답 `204 No Content`**

- `users` 행을 삭제하면 FK `ON DELETE CASCADE`로 아래가 함께 지워진다. 별도 삭제 로직이 필요 없다.

| 테이블 | 경로 |
|---|---|
| `refresh_tokens` | `user_id` → `users` (V3) |
| `user_favorite_exercises` | `user_id` → `users` (V3) |
| `workout_sessions` | `user_id` → `users` (V1) |
| `workout_sets` | `session_id` → `workout_sessions` (V1) |

- 리프레시 쿠키를 만료시킨다(`Set-Cookie: refreshToken=; Max-Age=0`). 액세스 토큰은 남은 만료 시간 동안 형식상 유효하지만 `sub`에 해당하는 사용자가 없으므로 모든 요청이 `401`이 된다.
- `exercises`는 영향받지 않는다. 종목은 사용자 소유 데이터가 아니다.
- **소프트 삭제를 쓰지 않는다.** 탈퇴의 목적이 개인정보를 남기지 않는 것이므로 행을 실제로 지운다. 같은 이메일로 다시 가입할 수 있다.

**오류**

| 상황 | 응답 |
|---|---|
| 비밀번호 불일치, 또는 다른 구글 계정으로 확인 | `401 INVALID_CREDENTIALS` |
| 구글 ID 토큰 검증 실패 | `401 GOOGLE_LOGIN_FAILED` |
| `password`·`googleCredential` 둘 다 없음 | `400 VALIDATION_ERROR` |

> 목업 계정 화면에 삭제 버튼이 이미 있으나 동작이 연결돼 있지 않다(대조 평가 C-23). 이 엔드포인트를 붙이면 해소된다.

### 4.8 PATCH /users/me/password

비밀번호 변경. **현재 비밀번호를 아는 상태**에서만 쓴다(로그인 상태). 잊어버린 경우의 재설정은 9.1의 후속 과제다.

**요청**

```json
{
  "currentPassword": "hunter2hunter2",
  "newPassword": "hunter3hunter3"
}
```

| 필드 | 규칙 |
|---|---|
| `currentPassword` | 필수. 현재 비밀번호 재확인 |
| `newPassword` | 필수, 8~72자(4.1과 동일). 현재 비밀번호와 같으면 `400` |

**응답 `204 No Content`**

- 서버는 새 비밀번호를 BCrypt로 해시해 `password_hash`를 갱신한다. 스키마 변경은 없다.
- **해당 사용자의 리프레시 토큰을 전부 폐기하고 쿠키를 만료시킨다.** 비밀번호를 바꾸는 이유 중 하나가 "내 계정이 털린 것 같다"이므로, 바꿔도 기존 세션이 살아 있으면 목적을 달성하지 못한다. 변경한 본인도 재로그인한다.
- 액세스 토큰은 남은 만료 시간(최대 30분) 동안 유효하다. 2.3의 로그아웃과 같은 한계이며 같은 이유로 감수한다.

**오류**

| 상황 | 응답 |
|---|---|
| 현재 비밀번호 불일치 | `401 INVALID_CREDENTIALS` |
| 새 비밀번호 형식 오류 / 현재와 동일 | `400 VALIDATION_ERROR` |
| 구글로만 가입해 비밀번호가 없는 계정 | `400 VALIDATION_ERROR` — 화면은 이 메뉴를 숨긴다 (LOG-37) |

### 4.9 POST /auth/google

구글 로그인(LOG-37). **처음이면 그 자리에서 가입하고, 이후로는 로그인한다** — 버튼 하나다.

화면은 Google Identity Services 버튼으로 구글 창을 띄우고, 사용자가 계정을 고르면 구글이 서명한 **ID 토큰**(JWT)을 받아 그대로 넘긴다.

**요청**

```json
{ "credential": "<구글 ID 토큰>" }
```

**응답 `200 OK`** — 4.2 로그인과 같다(액세스 토큰 + 리프레시 쿠키 + `user`). 가입이었는지 로그인이었는지는 구분하지 않는다.

**서버의 검증** — 구글 토큰은 확인만 하고 저장하지 않는다. 로그인 뒤에는 2.3의 우리 토큰을 쓴다.

| 확인 | 기준 |
|---|---|
| 서명 | 구글 공개 키(`https://www.googleapis.com/oauth2/v3/certs`)로 검증 |
| 발급자 `iss` | `https://accounts.google.com` 또는 `accounts.google.com` |
| 받는 쪽 `aud` | 우리 클라이언트 ID(`app.google.client-id`). 확인하지 않으면 다른 사이트가 받은 토큰으로 우리 앱에 로그인할 수 있다 |
| 만료 `exp` | 지나지 않았을 것 |

**계정 찾는 순서**

1. 구글 회원번호(`sub`)가 `users.google_id`인 계정 → 로그인
2. 없으면 같은 이메일로 가입한 계정에 `google_id`를 묶고 로그인. **구글이 이메일 소유를 확인한 경우(`email_verified`)만** — 확인되지 않은 이메일로 묶으면 남의 이메일을 적은 구글 계정이 그 사람의 기록을 가져갈 수 있다
3. 그것도 없으면 가입. 비밀번호 없이, 닉네임은 구글 이름(없으면 이메일 앞부분, 50자로 자름)

계정은 이메일이 아니라 **구글 회원번호로** 찾는다. 구글 계정의 이메일은 바뀔 수 있지만 번호는 바뀌지 않는다.

**스키마 (V7)**: `users.google_id VARCHAR(255) UNIQUE` 추가, `password_hash`의 `NOT NULL` 해제, CHECK `password_hash IS NOT NULL OR google_id IS NOT NULL` — 로그인할 방법이 없는 계정은 만들 수 없다.

**오류**

| 상황 | 응답 |
|---|---|
| 토큰 검증 실패(서명·발급자·대상·만료) | `401 GOOGLE_LOGIN_FAILED` |
| 새 계정이 필요한데 구글이 이메일을 확인하지 않음 | `401 GOOGLE_LOGIN_FAILED` |
| 같은 이메일 계정에 이미 다른 구글 계정이 묶여 있음 | `409 EMAIL_ALREADY_EXISTS` |
| `credential` 누락 | `400 VALIDATION_ERROR` |

> 구글로만 가입한 계정으로 `POST /auth/login`을 하면 일반 실패와 같은 `401 INVALID_CREDENTIALS`다(4.2). 다르게 알려주면 가입 방법이 드러난다.

---

## 5. 운동 종목 API

### 5.1 종목 데이터 개요

`exercises` 테이블(`V1` + `V2` + `V3`). 종목 데이터 정제는 8월 2주 작업 산출물이며, **종목 데이터 자체는 읽기 전용**이다(즐겨찾기 5.4는 별도 테이블에 쓴다).

| 필드 | 타입 | 값 |
|---|---|---|
| `id` | number | |
| `nameKo` | string | 한글 명칭. 종목의 자연키이며 UNIQUE (`V3`) |
| `nameEn` | string \| null | 영문 명칭 |
| `bodyPart` | enum | `CHEST` `BACK` `LEGS` `SHOULDERS` `ARMS` `CORE` (저장·검색·차트용 6종) |
| `primaryMuscle` | string \| null | 주동근 원자값 (`chest`, `lats`, `triceps` …). 판정 부위 산출 근거 (8.1) |
| `pushPull` | enum | `PUSH` `PULL` `NONE` |
| `measureType` | enum | `WEIGHT_REPS` `BODYWEIGHT_REPS` `WEIGHTED_BODYWEIGHT` `TIME` |
| `equipment` | enum | `BARBELL` `DUMBBELL` `MACHINE` `CABLE` `BODYWEIGHT` `PULLUP_BAR` |
| `deltRegion` | enum \| null | `FRONT` \| `REAR`. `primaryMuscle = shoulders`인 13종목만 값을 갖고 그 외는 `null` (`V3`, 8.1). 어깨 앞·뒤 판정 분리의 근거 컬럼 |

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
| `category` | enum | **탐색 분류 13종** 중 하나로 필터 (5.6, LOG-24·LOG-33) |
| `group` | string | **계열**로 필터. `category`와 함께 쓴다 (5.7, LOG-24) |
| `muscleGroup` | enum | **판정 부위 9종**(8.1 하위 `key`) 중 하나로 필터. 어깨 앞·뒤는 `delt_region`까지 가른다. `category`와 같이 주면 두 조건을 모두 만족하는 종목만 (LOG-29) |
| `equipment` | enum | 기구로 필터 |
| `measureType` | enum | 측정 유형으로 필터 |
| `favorite` | boolean | `true`면 내 즐겨찾기 종목만. 인증 없이 호출하면 무시 |
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
  "equipment": "BARBELL",
  "deltRegion": null,
  "groupName": "벤치프레스",
  "isFavorite": true
}
```

| 필드 | 설명 |
|---|---|
| `deltRegion` | `FRONT` \| `REAR` \| `null`. `primaryMuscle`가 `shoulders`인 종목만 값(8.1). 화면 종목 분류엔 쓰지 않고, 분석 판정 참고용 |
| `groupName` | 같은 동작의 변형 묶음 (`V6`, LOG-24). 109종 전부 값을 갖는다 |
| `isFavorite` | 내 즐겨찾기 여부. **인증된 요청에만** 포함하고, 비인증 요청에선 키 생략 |

**종목 목록의 세 탭 (목업)**

| 탭 | 산출 |
|---|---|
| 최근 사용 | 프론트가 `GET /workout-sessions` 이력에서 최근 등장 `exerciseId` 순으로 계산. 별도 엔드포인트 없음. 근거 필드는 6.6 응답의 `exercises[].id`이며, 배열이 수행 순서로 정렬돼 있으므로 세션을 `performedOn` 내림차순으로 훑으며 처음 만나는 순서가 곧 최근 사용 순이다 |
| 부위별 | **3단 드릴다운** (LOG-24). `GET /exercises/categories`로 분류 13종과 개수를 한 번에 받고 → `GET /exercises/groups?category=`로 계열을 받고 → `GET /exercises?category=&group=`으로 변형을 받는다. 부위마다 1회씩 6번 세던 방식은 폐기 |
| 즐겨찾기 | `GET /exercises?favorite=true&sort=` — 서버가 `user_favorite_exercises` 기준. 정렬은 `createdAt,desc` 고정(별표 누른 순) |

### 5.3 GET /exercises/{id}

**응답 `200 OK`** — 5.2의 단일 객체(`deltRegion`, 인증 시 `isFavorite` 포함). 없으면 `404 RESOURCE_NOT_FOUND`.

### 5.4 PUT / DELETE /users/me/favorite-exercises/{exerciseId}

별표 토글. 둘 다 멱등이다 — 이미 등록/해제된 상태로 다시 호출해도 성공 처리한다.

| 메서드 | 동작 | 응답 |
|---|---|---|
| `PUT` | 즐겨찾기 추가 (`user_favorite_exercises`에 UPSERT) | `204 No Content` |
| `DELETE` | 즐겨찾기 해제 | `204 No Content` |

- `exerciseId`가 없는 종목이면 `404 RESOURCE_NOT_FOUND`.
- 개수 상한은 두지 않는다(9월 범위). 필요 시 이후 `409`로 추가.

### 5.5 GET /exercises/{id}/last-performance

종목 프리필(기록 방식 3.4 ①)의 데이터 원천. 종목을 고르는 순간 호출해 그 종목의 직전 수행 세트를 그대로 받아 행을 채운다. 세트 행의 "이전기록" 열(기록 방식 3.2)도 이 값을 쓴다.

**대상 선정 규칙**

| 조건 | 내용 |
|---|---|
| 소유자 | 요청자 본인의 기록만 |
| 날짜 | **오늘 이전**의 기록만 (LOG-39) |
| 기간 | 제한 없음. 아무리 오래전이어도 마지막 수행분을 찾는다 |
| 정렬 | `performed_on` 내림차순. 하루에 기록은 하나라 그중 첫 기록이다 |
| 세트 범위 | 그날 기록에서 해당 종목의 전 세트. 워밍업 세트도 포함하고 `isWarmup`으로 구분한다 |

- 오늘을 빼는 것은 프리필 ①과 세트 복사 ②의 담당을 나누기 위함이다. 하루 기록에는 오늘 앞서 한 세트도 들어 있어서, 빼지 않으면 오늘 기록이 "지난번"으로 올라온다. 오늘 안의 값을 이어받는 것은 ②가 화면 상태로 처리한다.
- 사후 입력도 대상이다. 정렬 기준이 `performed_on`이라 오늘 입력한 과거 기록이 최신으로 올라오지 않는다.

**응답 `200 OK`**

```json
{
  "exerciseId": 128,
  "exerciseName": "바벨 벤치프레스",
  "measureType": "WEIGHT_REPS",
  "sessionId": 41,
  "performedOn": "2026-08-28",
  "sets": [
    { "setNo": 1, "weightKg": 60.0, "reps": 12, "durationSec": null, "isWarmup": true },
    { "setNo": 2, "weightKg": 70.0, "reps": 10, "durationSec": null, "isWarmup": false },
    { "setNo": 3, "weightKg": 70.0, "reps": 9,  "durationSec": null, "isWarmup": false }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `measureType` | 프리필 대상 필드를 정하는 데 쓴다(5.1 표). 종목 조회를 한 번 더 하지 않도록 함께 내려준다 |
| `sessionId` / `performedOn` | 어느 기록에서 가져왔는지. "8/28 기록에서 채움" 같은 근거 표기에 쓸 수 있다 |
| `sets[]` | `setNo` 오름차순. 저장 시 `기존 수 + 1`로 부여되므로 수행 순서와 일치한다 |
| `sets[].setNo` | 참고용 표시값이다. 삭제로 구멍이 있을 수 있으며(6.12), 새 기록의 `setNo`는 저장 시점에 서버가 새로 부여한다 |

> 세트에 `id`를 담지 않는다. 새 기록에 채워 넣을 참조값이지 편집 대상 행이 아니기 때문이다. 프리필된 행은 완료 체크 시점에 `POST /workout-sessions/{id}/sets`로 새로 저장된다(LOG-05).

**응답 `204 No Content`** — 오늘 이전에 해당 종목 기록이 없을 때. 오류가 아니라 "처음 하는 종목"이라는 정상 상태이며, 클라이언트는 빈 세트 행 하나로 시작한다.

**오류**

| 상황 | 응답 |
|---|---|
| `id`가 없는 종목 | `404 RESOURCE_NOT_FOUND` |

> 개인 기록이므로 `GET /exercises`와 달리 인증이 필요하다. 부록 B의 비인증 목록에 넣지 않는다.

### 5.6 GET /exercises/categories

종목 선택 1단계 (LOG-24). 5.2보다 앞선 단계지만 나중에 신설해 번호가 뒤에 붙었다.

**응답 `200 OK`** — 분류 13종 배열. 페이지 envelope을 쓰지 않는다(항상 13개, 고정).

```json
[
  { "category": "CHEST", "label": "가슴", "count": 18 },
  { "category": "BACK",  "label": "등",   "count": 8 }
]
```

| 필드 | 설명 |
|---|---|
| `category` | `GET /exercises?category=`에 그대로 넣는 값 |
| `label` | 화면에 적을 한글 이름. 서버가 준다 — 클라이언트마다 다르게 부르면 안 된다 |
| `count` | 그 분류의 종목 수. `GROUP BY primary_muscle` 한 번으로 12개를 모두 센다 |

**분류 13종과 `primary_muscle` 대응**

| 분류 | `label` | `primary_muscle` | 종목 수 |
|---|---|---|---|
| `CHEST` | 가슴 | `chest` | 18 |
| `ABS` | 복부 | `abdominals` | 16 |
| `LEGS` | 하체 | `quadriceps`, `hamstrings` | 15 |
| `TRICEPS` | 삼두 | `triceps` | 14 |
| `SHOULDERS_FRONT` | 어깨 전·측면 | `shoulders` + `delt_region = FRONT` | 10 |
| `SHOULDERS_REAR` | 어깨 후면 | `shoulders` + `delt_region = REAR` | 3 |
| `BACK` | 등 | `lats`, `middle back` | 8 |
| `TRAPS` | 승모근 | `traps` | 7 |
| `BICEPS` | 이두 | `biceps` | 7 |
| `FOREARMS` | 전완 | `forearms` | 3 |
| `GLUTES` | 엉덩이 | `glutes`, `abductors` | 3 |
| `CALVES` | 종아리 | `calves` | 3 |
| `LOWER_BACK` | 허리 | `lower back` | 2 |

> **컬럼을 추가하지 않았다.** 이 표는 `BrowseCategory` enum 안에 있고 기존 `primary_muscle` 값에서 파생한다. 분류를 고치려면 enum 한 곳만 고친다. 분석의 판정 부위 9종(8.1)과는 다른 축이므로 서로 영향을 주지 않는다.

### 5.7 GET /exercises/groups

종목 선택 2단계 (LOG-24). 한 분류 안의 계열 목록이다.

**쿼리 파라미터**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `category` | enum | **필수.** 5.6의 `category` 값 |

**응답 `200 OK`** — 계열 배열. 종목 수 내림차순.

```json
[
  { "groupName": "체스트프레스", "count": 6, "representativeId": 3,
    "representativeNameEn": "Decline Smith Press" },
  { "groupName": "벤치프레스",   "count": 5, "representativeId": 1,
    "representativeNameEn": "Decline Barbell Bench Press" }
]
```

| 필드 | 설명 |
|---|---|
| `groupName` | `exercises.group_name` (`V6`). `GET /exercises?category=&group=`에 그대로 넣는다 |
| `count` | 그 계열의 변형 수 |
| `representativeId` | 계열 카드에 쓸 대표 종목 id |
| `representativeNameEn` | 그 대표 종목의 영문명. 계열 카드 **동작 그림의 파일명**을 만드는 데 쓴다 — 그림이 `{영문명 슬러그}-{1,2}.webp`라서 id만으로는 어느 그림인지 알 수 없다 |

> 동작 그림은 백엔드를 거치지 않는다. `exercises`에 `image_url` 컬럼을 두지 않고 프론트의 정적 파일로 두었다 — 사용자가 올리는 것이 아니라 제품에 딸려오는 고정 자산이라 코드와 같이 버전이 매겨지는 편이 맞고, 컬럼을 두면 그림이 없는 종목까지 채워야 한다. 출처와 라이선스는 `frontend/public/exercises/CREDITS.md`에 있다.

> 계열은 109종 전부가 값을 가지며 40개다. `primary_muscle`이 "어느 근육을 쓰는가"(판정용)인 반면 `group_name`은 "어떤 동작인가"(탐색용)다 — 벤치프레스와 체스트프레스는 같은 근육을 쓰지만 고를 때는 다른 동작이다(LOG-24).

---

## 6. 운동 기록 API

### 6.1 개념 모델

```
workout_session (사용자의 하루 기록. (user_id, performed_on) 유니크)
  └─ workout_set (완료 체크한 세트 1개 = 1행)
```

> **하루에 기록은 하나다** (2026-10-11 개정, LOG-39). 처음에는 운동 한 번을 세션으로 두고 하루 여러 세션을 허용했으나(LOG-12), 같은 날 두 번째 운동을 시작하면 아침 기록이 그 화면에서 보이지 않았다. 지금은 아침·저녁에 두 번 운동해도 같은 기록에 쌓인다. 오전·오후 사이 공백이 운동 시간을 왜곡하던 문제(LOG-12의 근거)는 시간 규칙이 공백에서 구간을 나누는 것으로 해결한다(6.5, 8.5).
>
> **시작·종료가 없다.** 체크한 세트는 그 자리에서 저장되고(6.4), 마지막으로 체크한 세트가 곧 끝이다. 진행 중/완료 상태(`DRAFT`/`DONE`)와 사후 입력 표식(`LIVE`/`BACKFILL`)은 없앴다. **세트가 하나라도 있는 날**이 운동한 날이다 — 목록·캘린더·분석·그룹 모두 이 기준이다.
>
> 테이블 이름 `workout_sessions`와 경로 `/workout-sessions`는 운동 한 번을 뜻하던 시절의 이름이다. 세트와 응원이 `session_id`로 가리키고 있어 이름은 두고 뜻만 바꿨다.

- **그날 기록 열기 → 세트 저장(반복)**이 기본 흐름(UC-02-01).
- 프론트는 기록 화면에 들어올 때 `POST /workout-sessions`로 그날 기록을 열고, 체크한 세트를 `POST .../sets`로 보낸다. 계획 행은 서버로 보내지 않는다(LOG-05). 세트 없이 나가면 빈 기록이 남지만 운동한 날로 세지 않는다.

### 6.2 POST /workout-sessions

그날 기록을 연다. **없으면 만들고(`201`), 있으면 그 기록을 준다(`200`).** 두 번 불러도 기록은 하나다.

**요청**

```json
{ "performedOn": "2026-09-01" }
```

| 필드 | 규칙 |
|---|---|
| `performedOn` | 선택. 미래 불가. **생략하면 오늘이다.** 단 어제 기록의 마지막 세트가 30분(8.5의 구간 간격) 안이면 어젯밤 운동이 자정을 넘겨 이어지는 중이므로 **어제 기록**을 준다 |
| ~~`source`~~ | 없앴다(LOG-39). 보내도 무시한다. 사후 입력인지는 운동 시간의 구간 규칙이 세트 단위로 가린다(6.5) |
| ~~`routineId`~~ | **요청에서 받지 않는다**(LOG-26). 루틴과 기록은 서버가 잇는다 — 오늘 루틴을 만들 때 그날 기록을 열어 `routineId`를 채운다(11.2). 응답에는 그 값이, 루틴이 없는 날은 `null`이 담긴다 |

**응답 `201 Created`** (그날 첫 기록) / **`200 OK`** (그날 기록이 이미 있음) — 6.5의 기록 상세.

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
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
| `performedOn`이 미래 | `400 INVALID_DATE_RANGE` |

> **미래 날짜 기록은 허용하지 않는다** (대조표 A-4.2 미결정 항목의 확정). 미리 짜두는 운동은 "계획"이고, `workout_sessions`는 "기록"만 담는다 — LOG-05가 이 둘을 다른 테이블로 분리한 것이 핵심 결정이다. 미래 운동을 미리 구성하는 화면은 10월 `routines` 테이블의 일이다.

> **동시에 두 번 열어도 하나다.** "있는지 보고 넣기"를 하면 두 요청이 모두 "없음"을 보고 넣어 유니크 제약에 걸린다. 서버는 `INSERT … ON CONFLICT (user_id, performed_on) DO NOTHING` 뒤에 그 기록을 읽는다(응원과 같은 방식, 10.1). 이전에 미결로 두었던 세션 생성 멱등 키(`clientSessionId`)는 하루 하나 제약이 대신해 필요 없어졌다.

### 6.3 ~~POST /workout-sessions/{id}/complete~~ — 삭제 (LOG-39)

세션 종료(`DRAFT` → `DONE`, 운동 시간 산출)였다. 종료가 없어져 지웠다. 운동 시간은 읽을 때 세트 저장 시각으로 계산하고(6.5), 오류 코드 `SESSION_ALREADY_COMPLETED`·`EMPTY_SESSION`도 함께 없앴다.

### 6.4 POST /workout-sessions/{id}/sets

완료 체크한 세트 하나를 저장한다. 서버가 `recordedAt = now()`를 기록한다.

**요청**

```json
{
  "clientSetId": "9f1c2a44-7b30-4d6e-9a11-8e2f5c0b7d31",
  "exerciseId": 128,
  "weightKg": 70.0,
  "reps": 10,
  "durationSec": null,
  "isWarmup": false
}
```

| 필드 | 규칙 |
|---|---|
| `clientSetId` | **필수. 멱등 키** — 클라이언트가 완료 체크 시점에 생성한 UUID(`crypto.randomUUID()`). 같은 세션에서 같은 값이 다시 오면 새로 저장하지 않는다 (아래 참조) |
| `exerciseId` | 필수 |
| `weightKg` | 종목 `measureType`에 따라 필수/무시 (5.1 표). 0 이상, 소수 2자리 |
| `reps` | 종목에 따라 필수/무시. 1 이상 |
| `durationSec` | `TIME` 종목만 필수. 1 이상 |
| `isWarmup` | 기본 `false`. `true`면 볼륨 집계 제외(8.2), 강도(%)에는 표시(7.2) |
| ~~`setNo`~~ | **요청에서 받지 않는다**(LOG-26). 서버가 `해당 세션 내 같은 exerciseId 세트 수 + 1`로 부여한다. 클라이언트가 정하게 두면 같은 번호를 두 번 보내 1세트가 두 줄이 되고, 화면이 세트를 셀 근거가 없어진다 |

**멱등 처리**

- 서버는 `(session_id, client_set_id)`에 UNIQUE 제약을 두고, 이미 있는 키로 들어온 요청은 **저장하지 않고 기존 세트를 그대로 돌려준다.**
- 이때 상태 코드는 `201`이 아니라 **`200 OK`** 다. 클라이언트가 "새로 저장됨 / 이미 있던 것"을 구분할 수 있게 한다.
- 본문이 기존 세트와 달라도 **기존 값을 유지한다.** 이 키는 "같은 완료 체크 한 번"을 뜻하므로, 값 수정은 재전송이 아니라 `PATCH`(6.11)로 한다.
- 멱등 키는 `POST`에만 둔다. `PATCH`는 같은 값을 다시 보내도 결과가 같고, `DELETE`는 두 번째 호출이 `404`로 끝나므로 별도 장치가 필요 없다.

> **왜 필수인가.** 요청이 서버에 도달해 저장까지 끝난 뒤 응답만 유실되는 경우(타임아웃·전파 지연)가 있다. 클라이언트는 실패로 판단해 재전송하고, 멱등 키가 없으면 **같은 세트가 2행**이 된다. 이는 오프라인 큐를 붙이지 않아도 발생한다 — UC-02-01 예외 흐름 6a가 정한 "재시도를 안내한다"에서 사용자가 재시도를 누르면 그대로 일어난다.
>
> 중복 세트는 곧 **볼륨 과대 집계**이고, 부족한 부위를 충분한 것으로 올려 **약점을 놓치는 방향**으로 판정을 틀리게 한다. LOG-02(워밍업 제외)·LOG-05(계획 행 미저장)가 같은 이유로 막아온 오판 방향이며, 여기서만 열어둘 이유가 없다.
>
> 선택 항목으로 두지 않은 것은, 빠뜨려도 아무 오류가 나지 않고 **중복이 조용히 쌓이기** 때문이다. 클라이언트가 하나뿐인 현 단계에서 필수로 두는 비용은 `crypto.randomUUID()` 한 줄이다.
>
> **후속**: `workout_sets.client_set_id` 컬럼과 UNIQUE 제약이 필요하다. `V1`에 없으므로 `V4` 마이그레이션으로 추가한다(9.1).

**응답 `201 Created`** (새로 저장) / **`200 OK`** (같은 `clientSetId`로 이미 저장돼 있음)

```json
{
  "id": 901,
  "sessionId": 55,
  "clientSetId": "9f1c2a44-7b30-4d6e-9a11-8e2f5c0b7d31",
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
| `clientSetId` 누락 또는 UUID 형식 아님 | `400 VALIDATION_ERROR` |
| `measureType`에 필요한 값 누락 (예: `WEIGHT_REPS`인데 `reps` 없음) | `400 INVALID_MEASURE_INPUT` |
| `exerciseId` 없음 | `404 RESOURCE_NOT_FOUND` |
| 타인 기록 | `404 RESOURCE_NOT_FOUND` |

> 지난 날의 기록에도 세트 추가·수정·삭제를 허용한다(기록 방식 5.3, 사후 보정). 시각과 운동 시간은 저장해 두지 않고 읽을 때 계산하므로 세트가 바뀌면 다음 조회에 그대로 반영된다.

### 6.5 GET /workout-sessions/{id}

**응답 `200 OK`**

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
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
        { "id": 901, "clientSetId": "9f1c2a44-7b30-4d6e-9a11-8e2f5c0b7d31", "setNo": 1, "weightKg": 70.0, "reps": 10, "durationSec": null, "isWarmup": false, "recordedAt": "2026-09-01T19:32:11+09:00" },
        { "id": 902, "clientSetId": "3d7e6b18-0c52-4f9a-b6d4-1a8c93e5f207", "setNo": 2, "weightKg": 70.0, "reps": 9,  "durationSec": null, "isWarmup": false, "recordedAt": "2026-09-01T19:36:02+09:00" }
      ]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `startedAt` / `endedAt` | 첫·마지막 세트의 저장 시각. 세트가 없으면 `null`. 저장하지 않고 읽을 때 계산한다 |
| `durationSec` | 세트 저장 시각으로 계산한 운동 시간(8.5) — **30분 넘는 공백에서 구간을 나눠, 첫 세트가 그 날짜에 저장된 구간들만 합한다.** 아침·저녁 두 번 운동하면 둘을 더하고 사이 공백은 넣지 않는다. 지난 날짜에 나중에 채운 세트의 구간은 빠진다. 그런 구간만 있으면 `null` |
| `durationOverrideSec` | 사용자 수동 보정값 (없으면 `null`) |
| `effectiveDurationSec` | **서버가 결정한 최종 시간** = `durationOverrideSec ?? durationSec`. 화면은 이 값만 쓰면 된다 |
| `exercises[]` | 종목별로 그룹핑. **정렬 = 각 종목의 가장 이른 `recordedAt` 오름차순**(= 사용자가 그 종목을 처음 수행한 순서). 별도 순서 컬럼은 두지 않는다 |
| `exercises[].sets` | `setNo` 오름차순 |
| `exercises[].sets[].clientSetId` | 저장 시 클라이언트가 보낸 멱등 키(6.4). 재전송 도중 앱이 종료된 경우, 클라이언트가 세션을 다시 받아 자기 큐와 대조해 무엇이 이미 저장됐는지 판별하는 데 쓴다 |

> 세션 내 종목 순서는 "수행한 순서"로 고정한다. 종목을 위아래로 재배치하는 기능은 두지 않으므로 목업에서 제거해야 한다(9장). 자유 기록에서 `recordedAt` 순서 = 수행 순서이고, 이는 히스토리에서 보고 싶은 순서와 일치한다.

### 6.6 GET /workout-sessions

히스토리 목록(UC-02-03). **운동한 날**(세트가 있는 기록)만 준다. 세트는 포함하지 않는 요약 형태.

**쿼리 파라미터**

| 파라미터 | 설명 |
|---|---|
| `from`, `to` | `performedOn` 범위 (둘 다 `YYYY-MM-DD`). 생략 시 최근 30일 |
| ~~`status`~~ | 없앴다(LOG-39) |
| `exerciseId` | **종목별 필터.** 해당 종목의 세트가 하나라도 있는 날만. 없는 종목 ID를 넣으면 오류가 아니라 빈 페이지다 |
| `page`, `size`, `sort` | 기본 `sort=performedOn,desc` |

> `exerciseId`는 「기능명세서」 1의 기본 기능 *"운동 히스토리 — 날짜별/**종목별** 필터"* 에 대응한다. 세트 단위가 아니라 **날 단위 필터**다 — 응답은 그대로 하루 요약이며, 필터에 걸린 종목의 세트만 골라 담지 않는다. 종목 하나의 수치 추이는 통계 API(7.1)가, 직전 1회는 5.5가 담당한다.

**응답 `200 OK`** — 페이지 envelope. `content` 항목:

```json
{
  "id": 55,
  "performedOn": "2026-09-01",
  "effectiveDurationSec": 3339,
  "setCount": 12,
  "exerciseCount": 3,
  "exercises": [
    { "id": 128, "nameKo": "바벨 벤치프레스" },
    { "id": 131, "nameKo": "인클라인 덤벨 프레스" },
    { "id": 302, "nameKo": "케이블 푸시다운" }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `exercises[]` | 그날 등장한 종목. **정렬은 6.5와 동일**(각 종목의 가장 이른 `recordedAt` 오름차순 = 수행 순서). `exerciseCount`와 길이가 같다 |
| `exercises[].id` | 종목 ID. 5.2의 "최근 사용" 탭과 종목 상세 이동이 이 값을 쓴다 |

> **이름 배열(`exerciseNames`)이 아니라 `{ id, nameKo }` 객체 배열이다.** 5.2가 "최근 사용" 탭을 *"이력에서 최근 등장 `exerciseId` 순으로 계산"* 하도록 정했는데, 이름만 내려주면 그 계산을 할 수 없고 목록에서 종목 상세·프리필로 이어갈 수도 없다. 이름으로 역조회하는 경로(`GET /exercises?q=`)는 부분 일치라 동명 종목을 구분하지 못한다.

### 6.7 ~~GET /workout-sessions/current~~ — 삭제 (LOG-39)

진행 중 세션 이어쓰기였다. 진행 중이라는 상태가 없어져 지웠다. 오늘 기록을 이어 쓰려면 날짜 없이 `POST /workout-sessions`(6.2)를 부른다.

### 6.8 GET /workout-sessions/calendar

캘린더 화면용 월별 요약.

**쿼리 파라미터**: `year` (필수), `month` (필수, 1~12)

**응답 `200 OK`**

```json
{
  "year": 2026,
  "month": 9,
  "days": [
    { "date": "2026-09-01" },
    { "date": "2026-09-03" }
  ]
}
```

- **운동한 날**(세트가 있는 기록)만 배열에 포함. 하루에 기록이 하나라 날짜만 준다 — `sessionCount`·`hasDraft`는 없앴다(LOG-39).

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
| `memo` | ≤ 2000자. **빈 문자열**을 보내면 지운다 |
| `performedOn` | 날짜 정정. 미래 불가(`400`). **그 날짜에 기록이 이미 있으면 `409 DAY_ALREADY_RECORDED`** — 하루에 기록은 하나이고, 두 날의 메모·시간 보정을 어떻게 합칠지 서버가 정할 수 없어 합치지 않고 막는다 |
| `durationOverrideSec` | 0 이상. 설정 시 `effectiveDurationSec`이 이 값이 됨. **`0`**을 보내면 보정 해제 |

> **값을 비우는 신호는 `null`이 아니라 빈 문자열과 `0`이다** (2026-09-25 확정, LOG-23).
> JSON에서 "필드를 보내지 않음"과 "`null`로 보냄"은 역직렬화 후 똑같이 `null`이라
> 서버가 구분할 수 없다. `null`을 "지우기"로 삼으면 **날짜만 고치려는 요청에 메모가
> 날아간다** — 부분 수정의 전제가 깨진다. 두 필드 모두 빈 문자열·`0`이 실제 값으로
> 쓰일 일이 없어(0초짜리 운동은 없다) 비움 신호로 쓴다.

**응답 `200 OK`** — 6.5의 기록 상세.

### 6.10 DELETE /workout-sessions/{id}

**응답 `204 No Content`** — 세트도 함께 삭제(`ON DELETE CASCADE`). 분석은 온디맨드 집계라 별도 재계산 불필요(기록 방식 5.3).

### 6.11 PATCH /workout-sessions/{id}/sets/{setId}

**요청 (부분 수정)**

```json
{ "weightKg": 72.5, "reps": 8, "isWarmup": false }
```

- `measureType` 검증은 6.4와 동일.

**응답 `200 OK`** — 6.4의 세트 객체.

### 6.12 DELETE /workout-sessions/{id}/sets/{setId}

**응답 `204 No Content`**. 마지막 세트를 지워도 기록 자체는 남는다(메모가 있을 수 있다). 세트가 없는 날은 히스토리·캘린더·분석·그룹에 나오지 않는다.

> **`setNo`는 재부여하지 않는다** (2026-08-31 확정). 저장 시점에 부여된 값을 그대로 두므로 삭제 후 `setNo`에 구멍이 생길 수 있다(예: 1, 2, 4). 재부여를 하려면 남은 세트마다 PATCH를 돌리거나 삭제 응답에 갱신된 목록을 실어야 하는데, 얻는 것에 비해 계약이 무거워진다.
>
> **화면은 서버 `setNo`를 그대로 찍지 않고 배열 순번(1..N)으로 그린다.** 사용자에게는 항상 1, 2, 3으로 보이고, 서버 값은 식별·정렬에만 쓴다. 정렬 기준은 `recordedAt` 오름차순이다.

### 6.13 DELETE /workout-sessions/{id}/exercises/{exerciseId}

기록에서 그 종목의 세트를 한 번에 지운다(LOG-22).

**응답 `204 No Content`**.

**오류**

| 상황 | 응답 |
|---|---|
| 그 기록에 해당 종목의 세트가 없음 | `404 RESOURCE_NOT_FOUND` |
| 타인 기록 | `404 RESOURCE_NOT_FOUND` |

> 6.12의 세트 삭제를 반복해도 결과는 같지만, 5세트짜리 종목을 빼려면 요청이 다섯 번
> 나가고 **중간에 끊기면 일부만 지워진 상태가 남는다.** 기록 화면에서 "이 종목 빼기"는
> 세트 하나를 지우는 것보다 흔한 동작이라 한 번에 끝나는 경로를 둔다.

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
| `from`, `to` | 날짜 범위. 생략 시 `to`는 오늘, `from`은 `to`를 **포함해** 과거로 84일(12주 × 7). 8.2의 집계 구간과 같은 셈법이다 |

**산출 규칙** (분석 1.3, LOG-07 — 서버가 전부 계산)

- 대상: 해당 종목의 `reps ≤ 12`인 세트 (본세트·워밍업 무관하나 워밍업은 중량이 낮아 자연 배제됨).
- 세트별 추정: Epley 공식 `1RM = weightKg × (1 + reps / 30)` (`reps = 1`이면 `weightKg`). 공식·`12` 경계는 서버 설정값.
- 그날 값 = 그날 세트 추정값 중 **최댓값**.
- 오늘 체크한 세트도 바로 들어간다 — 종료가 없다(LOG-39).

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
      "estimatedOneRm": 93.3,
      "basedOnSet": { "weightKg": 80.0, "reps": 5 }
    },
    {
      "date": "2026-06-17",
      "estimatedOneRm": 96.3,
      "basedOnSet": { "weightKg": 82.5, "reps": 5 }
    }
  ]
}
```

- `points`는 날짜 오름차순. 해당 종목 기록이 없는 날짜는 포함하지 않는다(선을 끊지 않고 이어 그림).
- `basedOnSet`: 그날 최댓값을 만든 세트(디버그·근거 표시용).
- `estimatedOneRm`은 소수 1자리(HALF_UP).

> **정정 (2026-09-02)**: 초판 예시의 `92.3` / `95.0`은 위 Epley 공식으로 계산되지 않는 값이었다. `80.0 × (1 + 5/30) = 93.3`, `82.5 × (1 + 5/30) = 96.3`이 맞다. 공식이 규범이고 예시가 틀린 것이었으며, 예시를 기준으로 테스트를 짜면 실패한다. 단위 테스트 작성 중 발견했으며 LOG-13에 기록한다.

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
      "estimatedOneRm": 93.3,
      "sets": [
        { "setNo": 1, "weightKg": 60.0, "reps": 12, "isWarmup": true,  "intensityPct": 64 },
        { "setNo": 2, "weightKg": 70.0, "reps": 10, "isWarmup": false, "intensityPct": 75 },
        { "setNo": 3, "weightKg": 70.0, "reps": 9,  "isWarmup": false, "intensityPct": 75 }
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

> 위 예시의 검산: 세트별 Epley 추정이 `60×12 → 84.0`, `70×10 → 93.3`, `70×9 → 91.0`이므로 그날 최댓값은 `93.3`이고, 강도는 `60/93.3 → 64%`, `70/93.3 → 75%`가 된다. 초판은 `95.0 / 63 / 74`로 적혀 있었다(7.1과 같은 정정).

---

## 8. 분석 API

### 8.1 판정 부위 매핑

판정 단위는 `exercises.primary_muscle` 기반 **9종**이다. 화면 표시는 `body_part` 기반 **상위 6종** / 판정 **하위 9종**의 2계층이다. (분석 2.3·2.4, LOG-09·LOG-10)

**하위 9종 ← `primary_muscle`**

| 판정 부위 (`key`) | 구성 `primary_muscle` |
|---|---|
| `CHEST` | `chest` |
| `BACK` | `lats`, `middle back`, `traps`, `lower back` |
| `DELT_FRONT` | `primary_muscle = shoulders` AND `delt_region = FRONT` (오버헤드 프레스·전면 레이즈·**측면 레이즈** 계열) |
| `DELT_REAR` | `primary_muscle = shoulders` AND `delt_region = REAR` (후면 델트 계열) |
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
| `SHOULDERS` | 어깨 | `DELT_FRONT`(어깨 전·측면), `DELT_REAR`(어깨 후면) |
| `ARMS` | 팔 | `TRICEPS`(삼두), `BICEPS`(이두) |
| `LEGS` | 하체 | `QUADS`(앞허벅지), `POSTERIOR`(뒤허벅지·둔근) |
| `CORE` | 코어 | `CORE` |

**판정 제외 — 표시만** (분석 2.3): `calves`(종아리), `forearms`(전완). 부족 판정하지 않고 세트 수만 내려준다.

> **어깨 앞·뒤 분리**: `DELT_FRONT` / `DELT_REAR`를 나누는 `exercises.delt_region` 컬럼을 9월 1주 `V3` 마이그레이션에서 확보한다(2.4-(3)). 분류 자체는 8월 8일 종목 정제 때 끝났고(`FRONT` 10 / `REAR` 3), 저장할 자리만 없었다. 폴백(전량 `DELT_FRONT` 집계)은 전 사용자에게 "어깨(뒤) 부족" + 당기기 불균형 경고를 상시 띄우므로 채택하지 않는다.
>
> 응답의 `shoulderSplitResolved` 플래그는 **유지**한다. `delt_region`이 채워진 종목이 하나라도 세션에 있으면 `true`, 정제 지연 등으로 아직 NULL만 있으면 `false`를 내려 화면이 어깨 하위 판정을 참고치로 낮춰 표시하도록 신호한다. 정상 운영에서는 항상 `true`다.

### 8.2 GET /analysis/muscle-volume

부위별 볼륨과 4단계 부족 판정을 **2계층 구조로 완성해서** 내려준다. 화면은 합산·구간 분류·배지 계산을 하지 않는다.

**쿼리 파라미터**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `weeks` | `4` | 집계 기간(주). 설정값, 보통 고정 |
| `referenceDate` | 오늘 | 집계 구간의 마지막 날. 이 날을 **포함해** 과거로 `weeks × 7`일을 집계한다 (테스트·과거 시점 조회용) |

**집계 조건** (전부 서버에서 적용)

- 체크한 세트 전부. 오늘 세트도 바로 센다 — 종료가 없다(LOG-39)
- `workout_sets.is_warmup = false` (본세트만)
- 기간: `performed_on ∈ [referenceDate - (weeks × 7 - 1)일, referenceDate]` (기록 저장 시각 아님)
- 사후 입력 **포함** (실제 수행한 운동)
- 세트 → 판정 부위: 종목의 `primary_muscle` 기준, 주동근에만 카운트(간접 자극 미반영)
- 주당 평균 = `기간 내 세트 합 × 7 ÷ basisDays` (소수 1자리, HALF_UP). `basisDays`는 아래 참조

> **`basisDays` — 기록이 4주 미만이면 분모를 줄인다 (2026-10-03, LOG-31).** 첫 운동한 날(전체 기록 중 `referenceDate` 이전 최초)부터 `referenceDate`까지의 일수(양끝 포함)를 `[min-basis-days, weeks × 7]`로 자른 값이다(기본 `[7, 28]`). 기록이 없으면 `weeks × 7`. 28일 이상 기록했으면 `÷ weeks`와 정확히 같다. 근거는 분석 설계서 2.1 「기록이 4주 미만일 때의 분모」.

> **집계 구간은 `referenceDate`를 포함해 정확히 `weeks × 7`일이다.** `weeks = 4`면 28일이며, `referenceDate = 2026-09-01`일 때 `2026-08-05 ~ 2026-09-01`이다. 양끝을 포함하는 구간이므로 하한에서 하루를 빼야 분모(`weeks`)와 분자의 기간이 맞는다. LOG-12의 "오늘 포함 28일" 확정을 계산식으로 옮긴 것이다.

**4단계 판정** (`verdict`, 분석 2.2)

| 주당 평균 세트 | `verdict` | `verdictLabel` |
|---|---|---|
| `x < 4` | `INSUFFICIENT` | 부족 |
| `4 ≤ x < 10` | `BELOW_RECOMMENDED` | 권장 이하 |
| `10 ≤ x ≤ 20` | `OPTIMAL` | 최적 |
| `x > 20` | `EXCESSIVE` | 과다 |

> **경계값은 위 부등호가 확정이다.** `weeklySets`가 소수 1자리이므로 정확히 `4.0` / `10.0` / `20.0`이 흔하게 나온다(4주 40세트 = 10.0). ACSM 권고의 "최소 유효 4세트, 최적 10~20세트, 20세트 초과 시 수익 감소"(분석 2.2)를 그대로 읽어 **`10`과 `20`은 최적에 포함**한다. 목업은 `20`을 과다로 처리하므로 수정 대상이다(대조 평가 C-29).

**응답 `200 OK`**

```json
{
  "referenceDate": "2026-09-01",
  "periodWeeks": 4,
  "periodFrom": "2026-08-05",
  "periodTo": "2026-09-01",
  "shoulderSplitResolved": true,
  "confidence": {
    "level": "LOW",
    "workoutDayCount": 4,
    "threshold": 6,
    "message": "최근 4주 운동한 날이 4일로 적어 판정 신뢰도가 낮습니다."
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
        { "key": "DELT_FRONT", "label": "어깨 전·측면", "weeklySets": 8.0, "totalSets": 32, "verdict": "BELOW_RECOMMENDED", "verdictLabel": "권장 이하" },
        { "key": "DELT_REAR",  "label": "어깨 후면", "weeklySets": 1.0, "totalSets": 4,  "verdict": "INSUFFICIENT",     "verdictLabel": "부족" }
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
| `periodFrom` / `periodTo` | 실제 집계 구간 (화면 표기용, 서버가 계산). 양끝 포함이며 `periodTo - periodFrom + 1 = weeks × 7`일. 세트는 이 구간에서 센다 |
| `basisDays` | 주당 평균을 낼 때 나눈 일수. 기록이 4주 미만이면 첫 기록일부터 센 일수(최소 7), 아니면 `weeks × 7` (LOG-31) |
| `basisFrom` | `periodTo - basisDays + 1`. `basisDays < weeks × 7`이면 화면은 "최근 4주" 대신 "최근 N일 · basisFrom ~ periodTo"로 적는다 |
| `shoulderSplitResolved` | 세션에 `delt_region`이 채워진 어깨 종목이 있으면 `true`(정상), 아직 NULL만 있으면 `false`. `false`일 때만 화면이 어깨 하위 판정을 참고치로 낮춰 표시 (8.1) |
| `confidence.workoutDayCount` | 집계 기간에 운동한 날 수. 이전 이름 `doneSessionCount`(완료한 세션 수)를 하루 기록에 맞춰 바꿨다(LOG-39) |
| `confidence.level` | `LOW` \| `NORMAL`. `workoutDayCount < threshold`이면 `LOW` |
| `confidence.threshold` | 최근 4주 운동한 날 임계값. 서버 설정값(기본 6, 기록 방식 5.5) |
| `confidence.message` | `LOW`일 때만 채운다. `NORMAL`이면 `null` — 충분하다는 사실을 화면에 띄울 이유가 없다 (2026-09-29 구현 시 확정) |
| `tiers[]` | **상위 6종**. 항상 6개, 고정 순서(`CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE`) |
| `tiers[].weeklySets` | 하위 합의 주당 평균 (서버가 합산). 소수 1자리 |
| `tiers[].totalSets` | 기간 내 총 세트 수 |
| `tiers[].hasChildren` | 하위가 2개면 `true`(어깨·팔·하체), 1개면 `false`(가슴·등·코어) |
| `tiers[].verdict` | **하위가 1개일 때만** 값. 하위가 2개면 `null` (상위엔 판정 라벨 안 붙임 — 분석 2.4) |
| `tiers[].summaryBadge` | **하위가 2개일 때만** 값. 하위 중 가장 나쁜 상태 요약 (아래 표) |
| `tiers[].children[]` | **하위 9종**. 각 항목에 `verdict` 항상 존재. 이것이 실제 판정 단위 |
| `displayOnly[]` | 종아리·전완. `verdict` 없음 |

**`summaryBadge` enum** (서버가 하위 상태로 결정)

위에서부터 순서대로 검사해 처음 걸리는 것을 쓴다. 조건이 겹치지 않도록 각 행에 배타 조건을 명시했다.

| 순서 | `summaryBadge` | `summaryBadgeLabel` | 조건 |
|---|---|---|---|
| 1 | `MIXED` | 확인 필요 | `INSUFFICIENT`와 `EXCESSIVE`가 **함께** 있다 (서로 반대 방향) |
| 2 | `PARTIAL_INSUFFICIENT` | 일부 부족 | `INSUFFICIENT`가 하나 이상 있다 |
| 3 | `PARTIAL_EXCESSIVE` | 일부 과다 | `EXCESSIVE`가 하나 이상 있다 |
| 4 | `PARTIAL_BELOW` | 일부 권장 이하 | `BELOW_RECOMMENDED`가 하나 이상 있다 |
| 5 | `ALL_OPTIMAL` | 모두 최적 | 하위 전부 `OPTIMAL` |

우선순위: `INSUFFICIENT` > `EXCESSIVE` > `BELOW_RECOMMENDED` > `ALL_OPTIMAL`. 부족과 과다가 동반하면 `MIXED`.

> **정정 (2026-09-02)**: 초판은 조건을 서술형으로 적어 `PARTIAL_EXCESSIVE` 행("하위 중 최악이 `EXCESSIVE`, 부족·권장이하 없음")과 우선순위 줄(`EXCESSIVE` > `BELOW_RECOMMENDED`)이 어긋났다. 과다와 권장 이하가 함께 있을 때(삼두 22세트 · 이두 6세트처럼 실제로 가능한 조합) 두 서술이 다른 답을 낸다. **우선순위를 따르는 쪽으로 확정**하고 표를 순서 검사 형태로 바꿨다 — 더 나쁜 상태를 감추지 않는 쪽이다. 단위 테스트 작성 중 발견했으며 LOG-13에 기록한다.

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
  "shoulderSplitResolved": true,
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
| `basisDays` | 8.2와 같은 분모. 양쪽 주당 평균이 이 값으로 계산된다 (LOG-31) |
| `ratio` | `round(큰 쪽 / 작은 쪽, 2)`. 작은 쪽이 0이면 `null` |
| `smallerSideZero` | 작은 쪽 세트가 0일 때 `true` → `verdict`는 `IMBALANCED`(큰 쪽 > 0인 경우), 화면은 "비율 계산 불가"로 표기 |
| `verdict` | `BALANCED`(≤ 2.0) \| `IMBALANCED`(> 2.0). 양쪽 다 0이면 `INSUFFICIENT_DATA` |
| `components` | 합산에 쓰인 하위 판정 부위 `key` 목록 (근거 표시용) |
| `biggerSide` | 세트가 많은 쪽. 양쪽 다 `0`이면 `null`이고, 같으면 `left`다 (2026-09-29 구현 시 확정) |

### 8.4 LLM 분석 해설 (참고 — 9월 범위 밖)

분석 로직 설계서 7.3의 "LLM 기반 분석 평가"는 위 8.2·8.3 응답을 입력으로 자연어 해설을 생성한다. 별도 엔드포인트(`GET /analysis/commentary` 등)로 10월에 명세한다. 판정 수치는 자체 로직이 확정하고 LLM은 설명만 한다는 원칙에 따라, 본 명세의 응답 구조가 그대로 LLM 입력 계약이 된다.

### 8.5 서버 계산 상수 (설정값)

| 항목 | 값 | 근거 |
|---|---|---|
| 판정 집계 기간 | 4주 (28일), `performed_on` 기준 | 분석 2.1 |
| 주당 평균 분모 최솟값 | 7일 (`min-basis-days`). 기록이 4주 미만이면 첫 기록일부터 센 일수로 나누되 이보다 짧게는 나누지 않는다 | 분석 2.1, LOG-31 |
| 부족 임계 | 주 4 / 10 / 20 세트 | 분석 2.2 |
| 균형 비율 임계 | 2.0배 | 분석 4.2 |
| 신뢰도 임계 | 최근 4주 운동한 날 6일 | 기록 방식 5.5 |
| 1RM 대상 | `WEIGHT_REPS`, `reps ≤ 12`, 일자별 최댓값 | 분석 1.3, LOG-07 |
| 1RM 공식 | Epley `w × (1 + reps/30)` | 분석 1.3 (예시) |
| 운동 시간: 구간 간격 (`bout-gap`) | 1800초 (30분). 이보다 긴 공백에서 구간을 나눈다 | 기록 방식 4.2, LOG-39 |
| 운동 시간: 마지막 세트 보정 | 구간마다 +90초 | 기록 방식 4.2 |
| 운동 시간: 하루 상한 (`day-cap`) | 14400초 (4시간) | 기록 방식 4.2 |
| 사후 입력 구간 | 첫 세트가 그 날짜에 저장되지 않은 구간은 시간에서 뺀다. 그런 구간만 있으면 `durationSec`이 `null` (횟수는 포함) | 기록 방식 4.3, LOG-39 |

> 모든 값은 `application.yaml`의 `app.analysis.*` 프로퍼티로 노출해 코드 수정 없이 조정 가능하게 한다.

---

## 9. 미해결·후속 과제

### 9.1 스키마 / 백엔드

| 항목 | 내용 | 시점 |
|---|---|---|
| `V3__auth_profile_exercise_meta.sql` | ① `refresh_tokens` 테이블 ② `users.goal` 1컬럼 ③ `exercises.delt_region` ④ `user_favorite_exercises` 테이블 (2.4) | 완료 (9월 1주) |
| `exercises.delt_region` 값 채우기 | `primary_muscle = 'shoulders'`인 13개 종목에 `FRONT`/`REAR` 입력. 배분 기준은 LOG-09 표 | 완료 (9월 1주) |
| `V4__add_client_set_id.sql` | `workout_sets.client_set_id`(UUID NOT NULL) + `UNIQUE (session_id, client_set_id)`. 세트 저장 멱등 키(6.4). `V3`가 이미 적용된 뒤이므로 별도 버전으로 올린다 | 완료 (9월 1주) |
| `V5__exercises_name_ko_collate_c.sql` | `exercises.nameKo` 정렬을 `C` 콜레이션으로. 한글 이름 정렬이 DB 로케일에 따라 달라지는 것을 고정 | 완료 (9월 3주) |
| `V6__add_exercise_group.sql` | `exercises.group_name`(VARCHAR 40) + 인덱스. 종목 선택 3단 탐색의 계열 축(5.7, LOG-24). 분류 13종은 컬럼 없이 `primary_muscle`·`delt_region`에서 파생하므로 이 한 컬럼이 전부다 | 완료 (9월 4주) |
| 계열 값 채우기 | 109종 전부에 `group_name` 입력(40개 계열). 시드 CSV와 `R__seed_exercises.sql`에서 관리 | 완료 (9월 4주) |
| `V8__friend_groups.sql` | 친구 그룹 — `friend_groups`·`friend_group_members`·`cheers` (10.11, LOG-38) | 완료 (10월 2주) |
| `V9__daily_workout_record.sql` | 하루 기록(LOG-39) — 같은 날 기록을 가장 앞 기록으로 합치고(세트·응원 이동, 세트 순번 재부여, 메모 잇기), `status`·`source`·`started_at`·`ended_at`·`duration_sec` 삭제, `(user_id, performed_on)` UNIQUE | 완료 (10월 2주) |
| `password_reset_tokens` 테이블 | 비밀번호 재설정(4.8 주석). 문서 곳곳에 `V5`로 적혀 있으나 `V5`~`V9`가 이미 쓰여(`V7` 구글 로그인, `V8` 그룹, `V9` 하루 기록) 실제로는 **다음 빈 번호**다. d1에서 떼어 검증 이후로 미뤘다(LOG-25) | 검증 이후 |
| ~~세션 생성 멱등 키~~ | 하루 하나 제약(`V9`)과 "있으면 그 기록을 준다"(6.2)가 재전송 중복을 막아 **필요 없어졌다** | 해소 (LOG-39) |
| ~~`routines` FK~~ | `V10`에서 `workout_sessions.routine_id → routines(id)` FK를 걸었다(`ON DELETE SET NULL`) | 완료 (LOG-40) |
| 요약 배지 문안 | `summaryBadge` label 및 `MIXED` 표기 확정 (8.2). enum `key`는 고정 | 화면 구현 시 |
| QUADS 화면 명칭 | `abductors`·`adductors` 포함이라 "앞허벅지"가 부정확 (LOG-10 한계) | 종목 정제 결과 확인 후 |
| 우선순위 산출 | 여러 부위 동시 부족·불균형 시 보완 순서 (분석 8장) | 10월 |
| 부위별 볼륨 **주간 추이** 엔드포인트 | 8장에 시계열 경로가 없다. 2026-10-03에 `GET /analysis/weekly-volume`(월~일 주마다 부위별 세트, 판정 없음)을 만들었으나 화면에 붙일 방식이 정해지지 않아 **엔드포인트까지 걷어냈다**(LOG-30). 보여줄 방식이 정해지면 다시 만든다 | 미정 |
| 비밀번호 **변경** API | **9월 범위에 추가** (4.8) — 스키마·의존성 추가 없이 엔드포인트 1개 | LOG-21 |
| 이메일 변경 API | 계속 제외 — 로그인 식별자라 중복 검사·세션 처리가 따라붙고, 지인 10명 검증에서 바꿀 이유가 없다 | 필요 시 |
| 비밀번호 **재설정**(찾기) | **d1(배포 준비) 이후로 유예.** 메일 발송 수단이 스택에 없고(`build.gradle`에 mail 의존성 없음), 재설정 토큰 테이블·엔드포인트 2개·화면 2개가 필요하다. 무엇보다 **재설정 링크가 가리킬 HTTPS 도메인이 10월2주에나 생겨** 지금 만들면 동작을 확인할 수 없다. 그 전까지 비밀번호를 잊은 검증 참여자는 운영으로 대응한다(연락받아 해시 직접 갱신) | LOG-21 |
| Rate limiting | `429 RATE_LIMITED` 코드만 예약. 실제 도입은 배포 후 판단 | 미정 |
| 즐겨찾기 개수 상한 | 현재 무제한. 필요 시 `409`로 추가 (5.4) | 배포 후 판단 |
| **LOG-11 작성** | 프로필에서 운동 경력 제외 결정을 「설계 변경 로그」에 신설 | 완료 |
| **LOG-14 작성** | 프로필에서 키·체중까지 제외해 **훈련 목표 1종으로 축소**한 결정. UC-01-03·계획서의 "키/체중/경력/목표"와의 차이를 기록 (2.4-(2)) | 완료 |

> `workout_sessions.memo` / `duration_override_sec`는 `V1__init.sql`에 이미 존재함을 확인했다. V3 대상 아님.

### 9.2 프론트엔드 목업 수정 필요

명세와 8월 3주 목업이 어긋나는 지점. 명세를 기준으로 목업을 고친다.

> 진행 중 세션·종료·`BACKFILL`을 전제로 한 행(재진입 다이얼로그, 빈 세션 종료, 홈 진행 중 배너 등)은 하루 기록(LOG-39)으로 전제가 없어졌다. 당시 결정의 기록으로 남겨 둔다.

| 목업 화면 | 수정 내용 | 근거 |
|---|---|---|
| 미래 날짜 선택 → "루틴 미리 짜기" 화면 | **제거**. `workout_sessions`는 미래 날짜를 받지 않는다 | 6.2. 계획/기록 분리(LOG-05), 미리 짜기는 10월 `routines` |
| 세션 내 종목 위·아래 재배치 기능 | **제거**. 종목 순서는 "수행한 순서"(첫 세트 `recordedAt`)로 고정 | 6.5 |
| 프로필 목표: 증량 / 유지 / 감량 (체중 축) | **훈련 목표로 교체** — 근력 / 근비대 / 지구력 / 일반 체력 (`STRENGTH`/`HYPERTROPHY`/`ENDURANCE`/`GENERAL_FITNESS`) | 4.6, 분석 1.3의 목표 축. 체중 목표는 분석·추천 로직에서 안 쓰임 |

**추가 확인분 (2026-08-31 전수 대조)** — 목업 전체를 문서와 대조해 새로 찾은 항목. 심각도 "높음"만 옮겼고, 중간·낮음 28건과 근거·줄번호는 「목업_문서_대조_평가」 C장에 있다.

| 목업 화면 | 수정 내용 | 근거 |
|---|---|---|
| 분석 › 약점 — 상위 6종 평면 리스트에 판정 라벨 직접 부착 | **2계층으로 재설계**. 판정은 하위 9종에만, 상위에는 세트 수 + `summaryBadge`만. `tiers[].verdict`는 하위 1개일 때만 값 | 8.2, 분석 2.4, LOG-09. 그대로 두면 "삼두 0 + 이두 12 → 팔 · 최적" 오판 |
| 홈 › 부위별 볼륨 "이번 주" 모드가 누적 원값에 4/10/20 판정색을 칠함 | **판정색 제거**. 이번 주는 누적 막대만, 판정색은 4주 평균 모드에서만 | 분석 2.1이 최근 7일 고정 방식을 명시적으로 배제 |
| 분석 집계 창이 이번 주를 제외한 직전 4주 | **오늘 포함 28일로 교체** — 서버 값만 사용 | 8.2. 화면 문구 "최근 4주 평균"과 최대 11일 어긋남 |
| 세션 시작 버튼들에 진행 중 세션 가드 없음 | **가드 추가** + `409 DRAFT_SESSION_EXISTS` → 재진입 다이얼로그 경로 신설 | 6.1, 6.2. `DRAFT`는 사용자당 하나 |
| 세트 0개 세션에서도 "종료" 노출, 세션 삭제 UI 부재 | 빈 세션은 **"기록 취소"(DELETE)로 교체** + 세션 삭제 진입점 추가 | 6.3(`422 EMPTY_SESSION`), 6.10 |
| 운동 시간을 세트당 ±2분 로컬 카운터 / 세트 수 임의 공식으로 산출 | **서버 `effectiveDurationSec`만 사용.** BACKFILL 세션은 시간 표기 제외 | 기록 방식 4.2·4.3, 8.5 |
| 종목 상세·통계 "총 볼륨(톤)" 지표 6종 + 이동평균 | **제거**. 톤수 방식은 쓰지 않기로 했고 내려줄 엔드포인트도 없다 | 분석 1.1, 7장(엔드포인트는 `one-rm-trend`·`session-intensity` 둘뿐) |
| 넘패드·스테퍼: `reps`/`durationSec` 최솟값 0, 중량 소수 자릿수 무제한 | **`reps` ≥ 1, `durationSec` ≥ 1, 중량 소수 2자리**로 제한 | 6.4, 6.11 |
| 상·하체 비율에서 **종아리 누락** (`lower = legs`만) | **하체 = QUADS + POSTERIOR + CALVES**. 서버 `components` 사용 | 분석 4.3, 8.3. 하체 과소 집계 → 불균형 과다 경고 |
| 통계 › 부위별 8주 추이 미니차트 6개 | **제거**. 9월엔 추이 엔드포인트를 만들지 않는다 | 9.1 후속 과제로 이관 |
| 기록 탭 캘린더가 하루 1세션만 전제 (`historyData[key]` 단일 객체) | **하루 여러 세션 카드**를 그릴 수 있게 수정 | 6.1 하루 복수 세션 허용 확정 |
| 홈은 월요일 시작 주, 기록 캘린더는 일요일 시작 주 | **월요일 시작으로 통일** | 1.2 주 시작 요일 규정 신설 |

**c7 구현 시 추가 결정 (2026-09-30, LOG-28)** — 위 표에 없던 것. 화면을 실제로 옮기며 정했다.

| 목업 화면 | 수정 내용 | 근거 |
|---|---|---|
| 분석 › "AI 해설" 카드 (화면이 조립한 문장 + 처방 톤) | **제거**. 해설 API가 없고, 판정은 부족하다는 데까지만 알린다 | 8.4, 분석 2.4, 대조 평가 C-11 |
| 분석 › 신뢰도 낮으면 균형 카드를 숨김 | **숨기지 않는다.** 배너로 신뢰도만 알리고 판정은 그대로. `smallerSideZero`는 "비율을 낼 수 없어요"로 표기 | 8.3은 신뢰도와 무관하게 응답, 대조 평가 C-10 |
| 통계 › "최고 중량" 타일 | **"최고 추정 1RM"으로 교체.** 7.1은 최고 중량을 주지 않는다 | 7.1 |
| 기록 › 종목 지표 "볼륨(kg)" | **본세트 수(세트)로 교체.** 이 앱의 볼륨은 세트 수다 | 분석 1.1 |
| (목업에 없음) 완료 세션 0회일 때 | 전 부위 "부족" 대신 **빈 상태 + 기록 시작 버튼** | 8.2는 0세트를 `INSUFFICIENT`로 판정 |
| (목업에 없음) 하위가 둘인 상위 부위의 막대 색 | **판정색을 칠하지 않는다**(회색). 초록이면 라벨 없이도 "최적"으로 읽힌다 | 분석 2.4, LOG-09 |
| 홈 › 부위별 볼륨 막대 6줄 + 이번 주/4주 평균 토글 | **약점 카드로 교체** — 부족·권장 이하인 **하위** 부위를 덜 한 순으로(기본 셋, 펼치면 전부) 주당 세트·막대로 보여주고, 행을 누르면 오늘 기록 화면에 그 부위 종목 시트가 열린다. **균형은 별도 카드**로 두 쌍 모두 표시. 토글은 삭제 (LOG-29) | 분석 2.1·2.4. 상위 막대에 판정색을 칠하면 LOG-09 문제가 홈에서 재발 |
| 홈 › 오늘의 루틴 추천 카드 | **루틴 기능(e2) 전까지 넣지 않는다.** "운동 시작"이 주 버튼 (LOG-29) | 루틴은 10월5주. 검증 첫 주에 "준비 중" 카드가 보이면 안 됨 |
| 홈 › "이번 주 총 N세트 · M분" | **줄 전체 제거** — 시간은 분석에 안 쓰고 BACKFILL엔 없으며, 세트 수는 약점 카드가 부위별로 이미 보여준다 (LOG-29) | 기록 방식 4.3 — BACKFILL은 시간 없음 |
| 홈 › 진행 중 배너 → 재진입 다이얼로그 | 배너에 **날짜**를 적고 이어서/종료를 바로 고른다. 세트 0개면 종료 대신 삭제 (LOG-29) | 6.1·6.3. 어제 세션이 남으면 오늘 새 운동이 막힘 |

> 즐겨찾기 별표·탭은 목업 그대로 유지된다(명세가 목업에 맞췄음).
>
> **정정(2026-08-31)**: 이 자리에 "'어깨(앞)/어깨(뒤)' 2계층 표시는 목업 그대로 유지된다"고 적혀 있었으나 **사실이 아니다.** 목업에 2계층 표시는 존재하지 않는다 — `어깨(앞)`·`어깨(뒤)`·`삼두`·`이두`·`앞허벅지`·`뒤허벅지` 문자열이 목업 전체에 0회다. 목업 분석 탭은 상위 6종을 평면 리스트로 나열하고 **각 상위 항목에 판정 라벨을 직접 붙인다.** 이는 2.4가 금지한 표시 방식이므로, 2계층 리스트·펼치기·`summaryBadge`는 **신규로 설계해야 한다**(아래 표에 추가). 근거: 「목업_문서_대조_평가」 C-1.
> 프로필 항목은 **훈련 목표 1종**으로 확정. UC-01-03의 "경력"(LOG-11)에 이어 "키·체중"도 제외했다(2.4-(2), LOG-14). 목업 개인 정보 화면의 키·체중 표시(대조 평가 C-21)는 **제거** 대상이 된다 — C-21이 지적한 "편집 불가" 문제도 함께 해소된다.

---

## 10. 친구 그룹 API

> 2026-10-11 신설 (LOG-38). 기능명세서 2.6, UC-05-01~07. 엔드포인트 14개 — 전체 32→46개. 같은 날 하루 기록(LOG-39)으로 운동 기록 API 2개를 지워 44개.

### 10.1 원칙

| 항목 | 규칙 |
|---|---|
| 성격 | 경쟁이 아니라 서로 보이는 것. 순위·점수 없음, 멤버는 들어온 순서 (`joinedAt` 오름차순) |
| 조회 권한 | **멤버만.** 멤버가 아니면 그룹이 있는지조차 드러내지 않도록 `403`이 아니라 `404`다(6.5와 같은 원칙) |
| 방장 권한 | 이름 변경·코드 재발급·내보내기·삭제. 멤버가 시도하면 `403 ACCESS_DENIED` — 이때는 그룹이 있다는 걸 이미 안다 |
| 방장 승계 | 방장이 나가거나 탈퇴하면 남은 멤버 중 가장 먼저 들어온 사람(`joinedAt`, 같으면 `id`)에게 넘어간다. 마지막 한 명이 나가면 그룹을 지운다 |
| 한도 | 한 그룹 20명, 한 사람 10개 그룹. 넘으면 `409 GROUP_LIMIT_EXCEEDED` |
| 초대코드 | 대문자·숫자 6자리, 헷갈리는 글자(0·O, 1·I·L) 제외 — 31자 6자리 ≈ 8.9억 가지. 무작위 생성. 입력은 소문자·공백·하이픈을 받아 준다 |
| 공개 범위 | 멤버에게 닉네임, 운동 기록(날짜·종목·세트·운동 시간), 오늘 운동 여부. **메모는 보내지 않는다.** 종료가 없으므로 친구가 운동하는 중이면 그때까지 체크한 세트가 보인다(LOG-39 — 처음에는 끝난 운동만 보였다) |
| 동시성 | 멤버가 바뀌는 일(참여·나가기·내보내기·삭제)은 그룹 행을 잠그고(`SELECT … FOR UPDATE`) 한다. 응원은 `INSERT … ON CONFLICT DO NOTHING` |

### 10.2 POST /groups

**요청** `{ "name": "새벽 헬스" }` — 앞뒤 공백(전각 공백 포함)을 걷어 1~20자

**응답 `201 Created`** — 10.4와 같은 그룹 상세. 만든 사람이 방장이자 첫 멤버이고 `inviteCode`가 담긴다.

| 상황 | 응답 |
|---|---|
| 이름이 비었거나 20자 초과 | `400 VALIDATION_ERROR` |
| 들어간 그룹이 이미 10개 | `409 GROUP_LIMIT_EXCEEDED` |

### 10.3 GET /groups

**응답 `200 OK`** — 내가 들어간 순서. 그룹은 보통 한두 개라, 들어가기 전에도 누가 오늘 운동했는지 보이게 멤버별 오늘 상태를 담는다.

```json
[{
  "id": 7, "name": "새벽 헬스", "memberCount": 3, "isOwner": true,
  "todayWorkedOutCount": 1,
  "members": [
    { "userId": 41, "nickname": "민석", "isMe": true,  "todayStatus": "NONE" },
    { "userId": 42, "nickname": "영희", "isMe": false, "todayStatus": "DONE" },
    { "userId": 43, "nickname": "철수", "isMe": false, "todayStatus": "NONE" }
  ]
}]
```

| 필드 | 규칙 |
|---|---|
| `todayWorkedOutCount` | 오늘 세트가 있는 멤버 수 |
| `members` | 들어온 순서. `todayStatus`는 10.4의 `today.status`와 같은 기준이다. 응원 수는 담지 않는다 |

### 10.4 GET /groups/{groupId}

**응답 `200 OK`**

```json
{
  "id": 7,
  "name": "새벽 헬스",
  "inviteCode": "K7P2QX",
  "isOwner": false,
  "today": "2026-10-11",
  "weekStart": "2026-10-05",
  "members": [
    {
      "userId": 42, "nickname": "영희", "isOwner": true, "isMe": false,
      "joinedAt": "2026-10-11T09:00:00+09:00",
      "today": { "status": "DONE", "sessionId": 1166, "cheerCount": 2, "cheeredByMe": true },
      "week": { "dayCount": 3, "totalDurationSec": 9900, "workoutDates": ["2026-10-06", "2026-10-08", "2026-10-11"] }
    }
  ]
}
```

| 필드 | 규칙 |
|---|---|
| `inviteCode` | 멤버 누구에게나 준다. 다시 만드는 것만 방장이 한다 |
| `weekStart` | 이번 주 월요일(1.2, 홈의 "이번 주"와 같은 기준). 주는 월~일 |
| `today.status` | `DONE` 오늘 기록에 세트가 있음(사후 입력 포함) / `NONE`. 기록을 열기만 하고 체크하지 않았으면 `NONE` |
| `today.sessionId` | 응원할 대상인 오늘 기록. `NONE`이면 `null` |
| `week.dayCount` | 이번 주 운동한 날 수 (사후 입력 포함). 이전 이름 `sessionCount` |
| `week.totalDurationSec` | 그날들의 `effectiveDurationSec` 합. 시간이 없는 사후 입력은 빠진다(보정값이 있으면 들어간다) |
| `week.workoutDates` | 운동한 날짜, 오름차순 |

멤버 전원의 이번 주 기록과 세트를 **한 번에** 읽어 메모리에서 나눈다. 별도 집계 테이블은 없다.

### 10.5 방장 — PATCH /groups/{groupId}, DELETE /groups/{groupId}, POST /groups/{groupId}/invite-code

| 요청 | 응답 | 비고 |
|---|---|---|
| `PATCH` `{ "name": "새 이름" }` | `200` 그룹 상세 | 10.2와 같은 이름 규칙 |
| `DELETE` | `204` | 멤버십은 FK 연쇄 삭제. 멤버들의 운동 기록은 그대로 |
| `POST …/invite-code` | `200 { "inviteCode": "M4R8TW" }` | 옛 코드·링크로는 더 들어올 수 없다 |

멤버가 시도하면 `403 ACCESS_DENIED`, 멤버가 아니면 `404`.

### 10.6 GET /groups/invite/{code}

초대 링크로 들어왔을 때 **참여 전에** 보여줄 정보. 참여하면 무엇이 공개되는지 화면이 함께 안내한다.

```json
{ "groupId": 7, "name": "새벽 헬스", "memberCount": 4, "ownerNickname": "민석", "alreadyMember": false, "full": false }
```

없는 코드는 `404`. 로그인이 필요하다 — 참여하려면 어차피 계정이 있어야 하고, 화면은 로그인 뒤 이 미리보기로 돌아온다.

### 10.7 POST /groups/join

**요청** `{ "code": "k7p-2qx" }` — 소문자·공백·하이픈은 서버가 다듬는다

| 상황 | 응답 |
|---|---|
| 참여함 | `201 { "groupId": 7, "name": "새벽 헬스", "alreadyMember": false }` |
| 이미 멤버 | `200 { …, "alreadyMember": true }` — 오류가 아니다. 링크를 두 번 누르는 일은 흔하다 |
| 없는 코드 | `404` |
| 그룹 20명 / 내 그룹 10개 | `409 GROUP_LIMIT_EXCEEDED` |

### 10.8 DELETE /groups/{groupId}/members/me · DELETE /groups/{groupId}/members/{memberId}

- **나가기**(`me`): `204`. 방장이면 10.1의 승계 규칙, 마지막 한 명이면 그룹 삭제
- **내보내기**(방장): `204`. 방장 자신은 `400`(나가기를 쓴다), 멤버가 아니면 `404`
- 나가거나 내보내져도 그 사람의 운동 기록과 보낸 응원은 남는다
- 회원 탈퇴(4.7) 때도 방장인 그룹은 같은 규칙으로 넘어간다. `friend_groups.owner_id`에는 연쇄 삭제를 걸지 않았다 — 걸면 방장 한 명의 탈퇴로 남은 멤버들의 그룹이 사라진다

### 10.9 멤버 기록 — GET …/members/{memberId}/calendar · GET …/members/{memberId}/days/{date}

보는 사람과 대상이 **같은 그룹 멤버**여야 한다. 아니면 `404`.

- **calendar** `?year=2026&month=10` — 6.8과 같은 모양. `year`는 2000~2100
- **days/{date}** — 그날 기록. 6.5 기록 상세에서 **메모·세트 id·클라이언트 키를 뺀** 모양이다. 운동하지 않은 날이면 `session`이 `null`이다

```json
{
  "userId": 42, "nickname": "영희", "date": "2026-10-11",
  "session": {
    "id": 1166,
    "startedAt": "2026-10-11T19:12:03+09:00", "endedAt": "2026-10-11T20:22:41+09:00",
    "durationSec": 4200,
    "exercises": [{ "exerciseId": 1, "exerciseName": "바벨 벤치프레스", "exerciseNameEn": "Barbell Bench Press", "measureType": "WEIGHT_REPS",
                    "sets": [{ "setNo": 1, "weightKg": 40.00, "reps": 10, "durationSec": null, "isWarmup": false }] }],
    "cheerCount": 2, "cheeredByMe": true
  }
}
```

메모는 빈 값으로 내리지 않고 **필드 자체를 두지 않는다** — `null`이면 "메모가 없다"로 읽힌다. `exerciseNameEn`은 화면이 종목 카드에 동작 그림을 붙이는 데 쓴다(그림 파일 이름이 영문명에서 나온다).

### 10.10 PUT·DELETE /groups/{groupId}/sessions/{sessionId}/cheer

| 요청 | 응답 |
|---|---|
| `PUT` | `200 { "cheerCount": 3, "cheeredByMe": true }` — 두 번 눌러도 한 번이다 |
| `DELETE` | `200 { "cheerCount": 2, "cheeredByMe": false }` — 누르지 않았어도 오류가 아니다 |

| 상황 | 응답 |
|---|---|
| 내 운동 | `400 VALIDATION_ERROR` |
| 기록 주인이 이 그룹 멤버가 아님 / 내가 멤버가 아님 | `404` |

응원은 그룹이 아니라 **하루 기록**에 붙는다. 같은 사람과 그룹 두 개를 함께해도 그 사람의 운동은 하나라, 어느 그룹에서 눌렀든 같은 응원이다. 하루에 두 번 운동해도 응원은 그 하루에 하나다(LOG-39).

### 10.11 스키마 (V8)

| 테이블 | 컬럼 | 제약·인덱스 |
|---|---|---|
| `friend_groups` | `id`, `name VARCHAR(20)`, `invite_code VARCHAR(6)`, `owner_id`, `created_at`, `updated_at` | `invite_code` UNIQUE, `owner_id` FK(연쇄 삭제 없음) + 인덱스 |
| `friend_group_members` | `id`, `group_id`, `user_id`, `joined_at` | `(group_id, user_id)` UNIQUE, 두 FK 연쇄 삭제, `user_id` 인덱스 |
| `cheers` | `id`, `session_id`, `user_id`, `created_at` | `(session_id, user_id)` UNIQUE, 두 FK 연쇄 삭제, `user_id` 인덱스 |

테이블 이름을 `groups`가 아니라 `friend_groups`로 둔 것은 `GROUP`이 SQL·JPQL 예약어이고, 종목의 "계열"(`exercises.group_name`, `V6`)과도 헷갈리기 때문이다.

---

## 11. 오늘의 루틴 API

> 2026-10-24 신설 (LOG-40). 「루틴생성_로직_설계서」, 기능명세서 2.1, UC-03-03~06. 엔드포인트 8개 — 전체 44→52개.
> **1단계(규칙 루틴)** 기준이다. LLM 연결(3단계)은 응답 모양을 바꾸지 않는다 — `generatedBy`가 `LLM`이 되고 `comment`가 LLM의 설명이 될 뿐이다.

### 11.1 원칙

| 항목 | 규칙 |
|---|---|
| 역할 분담 | **부위·세트는 규칙**이 정한다(분석 결과 + 회복 + 하루 총량). 종목·순서·설명은 LLM이, 없거나 실패하면 규칙이 정한다. 화면에 보이는 숫자(근거·세트·반복 범위)는 전부 규칙이 쓴다 |
| 하루 하나 | 사용자의 하루에 루틴은 하나다. **다시 만들기는 없다** — 마음에 안 들면 후보정(11.6·11.7)한다. 하루 하나라 만족도·수행률이 루틴과 1:1이다 |
| 기준일 | 판정은 **어제**까지의 기록으로 한다(`referenceDate`). 오늘 체크한 세트가 오늘 루틴의 근거를 흔들지 않게. 회복 제외(오늘·어제 한 부위)만 오늘 기록까지 본다 |
| 계획 저장 | 계획은 `routine_items`에만 있다. `workout_sets`에 계획 행을 넣지 않는다(LOG-05) — 기록 화면이 "계획 − 체크한 세트"로 남은 계획을 다시 만든다 |
| 원래 계획 | 후보정은 지우지 않고 원래 값을 남긴다(`original_exercise_id`·`original_sets`·`removed`). 수행률의 분모가 **후보정 전 원래 계획**이다(설계서 11.2) |
| 권한 | 남의 루틴은 있는지조차 드러내지 않도록 `404`(6.5·10.1과 같은 원칙) |

### 11.2 POST /routines/today

**요청** 본문 없음.

**응답 `201 Created`** (오늘 첫 루틴) / **`200 OK`** (오늘 루틴이 이미 있음) — 11.4의 루틴. 동시에 두 번 눌러도 하나만 생긴다(`INSERT … ON CONFLICT DO NOTHING`).

만들 때 **그날 기록을 열어(6.2) `routineId`를 채운다.** 기록 화면은 그 값으로 루틴을 찾아 남은 계획을 붙인다.

오늘 할 부위가 없으면(전부 회복 중이거나 충분) `items`가 빈 루틴이 만들어지고 `comment`가 쉬어 가라는 문장이다. 버튼을 막지 않는다 — 기록이 없는 사람도 기본값(주 3회, 큰 근육부터)으로 루틴을 받는다(설계서 2.6).

### 11.3 GET /routines/today · GET /routines/feedback-pending

| 요청 | 응답 |
|---|---|
| `GET /routines/today` | `200` 오늘 루틴 / `204` 아직 받지 않음 |
| `GET /routines/feedback-pending` | `200` 만족도를 물을 지난 루틴 / `204` 물을 루틴 없음 |

`feedback-pending`은 다음 홈 방문 때 **한 번만** 묻기 위한 것이다(설계서 11.4). 최근 7일(`feedback-lookback-days`)의 지난 루틴 중 **루틴 부위를 체크한 가장 최근 하나**만 본다. 그 루틴에 이미 답했거나 건너뛰었으면 더 오래된 루틴은 묻지 않는다 — 밀린 숙제처럼 연달아 묻지 않게. 오늘 루틴은 여기 오지 않는다(홈 카드가 `askFeedback`으로 묻는다).

### 11.4 GET /routines/{routineId}

**응답 `200 OK`**

```json
{
  "id": 31,
  "routineDate": "2026-10-24",
  "referenceDate": "2026-10-23",
  "generatedBy": "RULE",
  "comment": "큰 근육 위주로, 큰 부위부터 하는 순서예요.",
  "lowConfidence": true,
  "totalSets": 16,
  "doneSets": 4,
  "items": [
    {
      "id": 101, "position": 1,
      "muscleGroup": "CHEST", "muscleLabel": "가슴",
      "exerciseId": 5, "exerciseName": "바벨 벤치프레스", "exerciseNameEn": "Barbell Bench Press - Medium Grip",
      "measureType": "WEIGHT_REPS", "equipment": "BARBELL",
      "plannedSets": 4, "targetMin": 8, "targetMax": 12,
      "firstTime": true,
      "basisWeeklySets": 0.0, "basisVerdict": "INSUFFICIENT", "basisVerdictLabel": "부족",
      "removed": false, "adjusted": false,
      "doneSets": 4
    }
  ],
  "feedback": { "rating": null, "reason": null, "skipped": false },
  "askFeedback": true
}
```

| 필드 | 규칙 |
|---|---|
| `generatedBy` | `RULE` / `LLM`. 1단계는 모두 `RULE` |
| `comment` | 조합 설명 1~2문장. 종목·순서의 이유만 말하고 숫자는 쓰지 않는다. 규칙 문장은 그날 담긴 부위(큰 근육·작은 근육·코어)로 고른다(설계서 7.2) |
| `lowConfidence` | 기준일까지 4주 동안 운동한 날이 신뢰도 임계(8.5) 미만. 화면이 "기록이 쌓일수록…" 안내를 띄운다 |
| `totalSets` | 빼지 않은 항목의 `plannedSets` 합 |
| `doneSets` | 빼지 않은 항목마다 `min(그날 체크한 본세트, plannedSets)`의 합 — 진행 표시용. 계획보다 많이 해도 계획만큼만 센다 |
| `items` | `position` 순. 큰 근육 → 작은 근육 → 코어. 뺀 항목도 `removed: true`로 내려온다 |
| `items[].targetMin`·`targetMax` | 반복 범위 — 측정 유형으로 고정. 중량 8~12회, 맨몸 10~15회, 시간 30~60**초**(설계서 9.1). 중량은 정하지 않는다 |
| `items[].firstTime` | 루틴 날짜 전에 이 종목 세트가 하나도 없다. 화면이 [처음]과 "동작 보기"를 띄운다 |
| `items[].basisWeeklySets`·`basisVerdict` | 근거 — 만든 날 기준 최근 4주 주당 평균 세트와 판정(8.2). "가슴 · 주 0세트(부족)" |
| `items[].adjusted` | 교체·세트 조정·빼기 중 하나라도 했다 |
| `items[].doneSets` | 그날 이 종목의 체크한 본세트 수 (워밍업 제외) |
| `askFeedback` | **오늘** 루틴이고, 루틴 부위를 하나라도 체크했고, 아직 답하지 않았다 — 홈 카드 아래 "오늘 루틴 어땠어요?" |

### 11.5 GET /routines/{routineId}/items/{itemId}/alternatives

교체 후보. **같은 판정 부위**에서 루틴에 이미 있는 종목을 빼고, 최대 10개.

```json
[{ "exerciseId": 34, "exerciseName": "랫풀다운: 와이드 그립", "exerciseNameEn": "Wide-Grip Lat Pulldown",
   "equipment": "CABLE", "measureType": "WEIGHT_REPS", "firstTime": true, "sameEquipment": false }]
```

정렬은 **기구가 다른 종목 먼저**(LOG-08 — 기구가 없어서 바꾸는 경우가 많다) → 최근 8주에 자주 한 순 → 입문 대표 종목 순(설계서 4.1) → 시드 순. `sameEquipment`는 화면이 "다른 기구 / 같은 기구"로 묶는 데 쓴다.

### 11.6 PATCH /routines/{routineId}/items/{itemId}

**요청** — 보내지 않은 항목은 그대로 둔다.

```json
{ "exerciseId": 34, "plannedSets": 3 }
```

| 필드 | 규칙 |
|---|---|
| `exerciseId` | 선택. **같은 판정 부위**의 종목만. 반복 범위와 `firstTime`은 새 종목으로 다시 정한다 |
| `plannedSets` | 선택. 1~10 |

**응답 `200 OK`** — 바뀐 루틴(11.4). 처음 바꿀 때 원래 종목·세트를 `original_*`에 남긴다. 몇 번을 바꿔도 원래 값은 처음 것이다.

| 상황 | 응답 |
|---|---|
| 다른 부위의 종목 | `400 VALIDATION_ERROR` — 다른 부위로 바꾸면 루틴의 근거("이 부위가 부족하다")가 성립하지 않는다. 다른 부위는 기록 화면에서 직접 추가한다 |
| `plannedSets`가 1~10 밖 | `400 VALIDATION_ERROR` |
| 지난 루틴 | `400 VALIDATION_ERROR` — 고치면 이미 쌓인 수행률이 바뀐다 |
| 없는 종목 / 없는·남의 루틴·항목 | `404` |

### 11.7 DELETE /routines/{routineId}/items/{itemId}

**응답 `200 OK`** — 바뀐 루틴(11.4). 항목을 지우지 않고 `removed: true`로 남긴다(11.1 원래 계획). 되돌리기는 없다. 지난 루틴이면 `400`.

### 11.8 PUT /routines/{routineId}/feedback

**요청** — 답하거나 건너뛴다.

```json
{ "rating": "OKAY", "reason": "TOO_MUCH" }
{ "skipped": true }
```

| 필드 | 규칙 |
|---|---|
| `rating` | `GOOD` / `OKAY` / `BAD` |
| `reason` | 선택. `EXERCISE`(종목이 안 맞음) / `TOO_MUCH`(양이 많음) / `TOO_LITTLE`(양이 적음) / `OTHER`. `GOOD`이면 보내도 저장하지 않는다 |
| `skipped` | `true`면 건너뜀. 다시 묻지 않는다 |

**응답 `200 OK`** — 바뀐 루틴(11.4).

| 상황 | 응답 |
|---|---|
| `rating`도 `skipped`도 없음 | `400 VALIDATION_ERROR` |
| 이미 답했거나 건너뜀 | `409 ROUTINE_FEEDBACK_EXISTS` — 루틴당 한 번 |
| 없는·남의 루틴 | `404` |

지난 루틴에도 답할 수 있다(다음 방문 시트, 11.3).

### 11.9 스키마 (V10·V11)

| 테이블 | 컬럼 | 제약·인덱스 |
|---|---|---|
| `routines` | `id`, `user_id`, `routine_date`, `reference_date`, `generated_by`, `llm_attempts`, `fallback_reason`, `comment`, `comment_by`, `final_check_passed`, `final_check_failures`, `feedback_rating`, `feedback_reason`, `feedback_skipped`, `feedback_at`, `created_at` | `(user_id, routine_date)` UNIQUE, `user_id` FK 연쇄 삭제, 열거값 CHECK |
| `routine_items` | `id`, `routine_id`, `position`, `muscle_group`, `exercise_id`, `planned_sets`, `target_min`, `target_max`, `first_time`, `basis_weekly_sets`, `basis_verdict`, `original_exercise_id`, `original_sets`, `removed` | `routine_id` FK 연쇄 삭제 + 인덱스, `exercise_id` FK, `planned_sets` 1~10 |
| `workout_sessions` | `routine_id` → `routines(id)` | FK `ON DELETE SET NULL` (V10) |
| `exercises` | `starter_rank SMALLINT NULL` | 입문 대표 종목 순위(V11, 설계서 4.1). 값은 종목 CSV → 반복 마이그레이션 |

`llm_attempts`·`fallback_reason`·`comment_by`·`final_check_*`는 응답에 내리지 않는다 — 검증 지표(LLM 1차 통과율, 폴백률, 운동학적 기준 통과율)를 계산하는 데 쓴다(설계서 11장). 1단계 루틴은 `generated_by = RULE`, `fallback_reason = LLM_DISABLED`로 남아, LLM 연결 뒤 규칙 루틴과 LLM 루틴을 나눠 비교할 수 있다.

### 11.10 설정값 (`app.routine.*`)

| 설정 | 기본값 | 의미 |
|---|---|---|
| `target-weekly-sets` | 10 | 부위당 주간 목표 세트 (권장 구간의 아래 끝) |
| `per-muscle-min-sets` · `per-muscle-max-sets` | 2 · 6 | 하루 한 부위 세트 범위 |
| `fill-max-sets` | 3 | 하루 총량을 못 채울 때 최적 부위로 채우는 세트 |
| `daily-min-sets` · `daily-max-sets` | 12 · 18 | 하루 총 세트 범위 |
| `recovery-days` | 2 | 이 날수 안에 한 부위는 뺀다 (오늘·어제) |
| `default-days-per-week` | 3 | 기록이 적을 때 쓰는 주당 운동 일수 |
| `min-known-days` | 3 | 운동한 날이 이보다 적으면 주당 일수를 기본값으로 |
| `candidates-per-muscle` | 5 | 부위당 후보 종목 |
| `history-weeks` · `history-max-candidates` | 8 · 3 | 해 본 종목을 보는 기간과 후보 안의 최대 개수 |
| `feedback-lookback-days` | 7 | 만족도를 물을 지난 루틴을 찾는 기간 |

---

## 부록 A. Enum 정의 모음

| 그룹 | 값 |
|---|---|
| `exercise.bodyPart` | `CHEST`, `BACK`, `LEGS`, `SHOULDERS`, `ARMS`, `CORE` |
| `exercise.pushPull` | `PUSH`, `PULL`, `NONE` |
| `exercise.measureType` | `WEIGHT_REPS`, `BODYWEIGHT_REPS`, `WEIGHTED_BODYWEIGHT`, `TIME` |
| `exercise.equipment` | `BARBELL`, `DUMBBELL`, `MACHINE`, `CABLE`, `BODYWEIGHT`, `PULLUP_BAR` |
| `exercise.deltRegion` | `FRONT`, `REAR`, `null` (`primaryMuscle = shoulders`인 종목만 값) |
| `user.goal` | `STRENGTH`, `HYPERTROPHY`, `ENDURANCE`, `GENERAL_FITNESS` (훈련 목표 축 — 체중 목표 아님) |
| 판정 부위 상위 (`tier.key`) | `CHEST`, `BACK`, `SHOULDERS`, `ARMS`, `LEGS`, `CORE` |
| 판정 부위 하위 (`child.key`) | `CHEST`, `BACK`, `DELT_FRONT`, `DELT_REAR`, `TRICEPS`, `BICEPS`, `QUADS`, `POSTERIOR`, `CORE` |
| 표시만 (`displayOnly.key`) | `CALVES`, `FOREARMS` |
| `verdict` | `INSUFFICIENT`, `BELOW_RECOMMENDED`, `OPTIMAL`, `EXCESSIVE` |
| `summaryBadge` | `ALL_OPTIMAL`, `PARTIAL_BELOW`, `PARTIAL_INSUFFICIENT`, `PARTIAL_EXCESSIVE`, `MIXED` |
| `balance.verdict` | `BALANCED`, `IMBALANCED`, `INSUFFICIENT_DATA` |
| `routine.generatedBy` | `RULE`, `LLM` |
| `routine.feedback.rating` | `GOOD`, `OKAY`, `BAD` |
| `routine.feedback.reason` | `EXERCISE`, `TOO_MUCH`, `TOO_LITTLE`, `OTHER` |
| `routines.fallback_reason` (응답 없음) | `LLM_DISABLED`, `LLM_ERROR`, `VALIDATION_FAILED` |
| 에러 `code` | 1.5의 표 참조 |

---

## 부록 B. 인증이 필요 없는 엔드포인트

| 엔드포인트 |
|---|
| `POST /auth/signup` |
| `POST /auth/login` |
| `POST /auth/google` |
| `POST /auth/refresh` (리프레시 쿠키로 인증) |
| `GET /exercises`, `GET /exercises/{id}` |
| `GET /actuator/health` |

나머지 모든 엔드포인트는 유효한 액세스 토큰이 필요하며, 리소스 소유자 본인만 접근할 수 있다.

---

*본 명세는 설계 단계 산출물이며, 9월 구현 과정에서 필드·경로가 조정될 수 있다. 변경은 「설계 변경 로그」에 반영한다.*
