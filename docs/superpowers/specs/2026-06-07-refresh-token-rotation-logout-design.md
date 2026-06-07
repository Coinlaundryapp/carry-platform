# Refresh 토큰 회전/폐기 + Logout 설계 (A-3)

> 작성일: 2026-06-07 · 브랜치 `feature/refresh-token-rotation-logout` (base `origin/develop` `579975e`)
> 백로그 출처: `2026-06-07-carry-remaining-backlog.md` P1 #1. 직전 인증 작업(#77 Kakao 로그인/refresh wiring)의 직접 연장.

## 1. 문제

현재 refresh 토큰은 **무상태 JWT**(`sub=userId`, `purpose=REFRESH`, `iat`, `exp`만; jti 없음)다.

- `AuthService.refresh()`는 access 토큰만 재발급하고 refresh를 **회전하지 않는다**.
- 발급된 refresh 토큰을 **폐기할 수단이 없다**(7일간 무조건 유효 → 탈취 시 회수 불가).
- **`POST /auth/logout`이 없다**.

토큰 라이프사이클 보안(회전 + 폐기 + 재사용 감지)은 senior 백엔드 시그널이며, #77에서 의도적 후속으로 남긴 항목이다.

## 2. 결정 (사용자 확정 2026-06-07)

| # | 결정 | 선택 | 근거 |
|---|------|------|------|
| D1 | 세션 모델 | **디바이스별 다중 세션** | 모바일 O2O 현실(폰/태블릿 동시). Redis 키=세션 단위. |
| D2 | 회전 토큰 재사용 시 | **세션 전체 무효화** (RFC 6819 재사용 감지) | 탈취 신호로 간주 → 공격자+정상사용자 둘 다 재로그인 강제. |
| D3 | logout 시 access 토큰 | **무상태 유지** (refresh만 폐기) | 매 요청 Redis 조회 회피, JWT 무상태 보존. access TTL 1h가 잔존창 상한. |

YAGNI 제외: "전 디바이스 일괄 로그아웃"(단일 세션 logout만), access 블랙리스트.

## 3. 모델 — 세션 단위 allowlist + 회전

refresh 토큰에 claim 2개 추가:
- `sid` (session id, UUID): **한 디바이스 로그인 동안 회전돼도 불변**. 세션의 정체성.
- `jti` (token id, UUID): **토큰마다 고유**. 회전마다 갱신.

Redis는 **세션별 "현재 유효한 jti"** 하나만 보관한다:

```
Key:   auth:refresh:{sid}        Value: 현재 jti (String)     TTL: refresh 수명(기본 7d), 회전 시 갱신
```

- 디바이스별 다중 세션 = sid가 디바이스마다 다른 UUID → 키 분리(D1).
- "현재 jti와 다른 jti가 제시됨" = 이미 회전된 옛 토큰의 재사용 = 탈취 신호 → 키 삭제로 세션 폐기(D2).
- logout = 키 삭제(D3, refresh만 폐기). access는 자연 만료.

## 4. 흐름

### 4.1 로그인/가입 (issueTokens)
1. `sid = UUID`, `jti = UUID` 생성
2. `redis.set(auth:refresh:{sid}, jti, ttl=7d)`
3. access + refresh(sub, sid, jti) 반환

### 4.2 refresh (회전)
1. 서명·`purpose=REFRESH` 검증 → `userId, sid, jti` 추출 (실패 → 401 `AuthTokenInvalidException`)
2. user 조회 + active 확인 (실패 → 401)
3. `stored = redis.get(auth:refresh:{sid})`
   - `stored == null` → **로그아웃됨/만료** → 401 `AuthTokenInvalidException`
   - `stored != jti` → **재사용 감지** → `redis.del(sid)` 세션 폐기 → 401 `RefreshTokenReuseException` (warn 로그)
   - `stored == jti` → **회전**:
     - `newJti = UUID`
     - `redis.set(auth:refresh:{sid}, newJti, ttl=7d)` (TTL 갱신 = 슬라이딩)
     - 새 access + 새 refresh(sub, sid, newJti) **둘 다** 반환 (`TokenPair`)

### 4.3 logout (`POST /api/v2/auth/logout`)
1. refresh 토큰 파싱 → `sid` 추출
2. `redis.del(auth:refresh:{sid})` — 멱등(없는 키 삭제=no-op)
3. **204 No Content** 반환
4. 파싱 불가 토큰(서명 불량 등)도 폐기할 세션이 없으므로 동일하게 204 (세션 존재 여부 비노출)

## 5. 헥사고날 배치 (기존 패턴 준수)

### 5.1 carry-security `JwtProvider`
- `createRefreshToken(userId: Long, sessionId: String, jti: String): String` — **순수 빌더**. sid/jti 주입받음, UUID 생성 안 함. `.withClaim("sid", sessionId).withJWTId(jti)` 추가.
- `parseRefreshToken(token: String): RefreshClaims?` — 반환 타입 `Long?` → `RefreshClaims(userId, sessionId, jti)`. purpose 검증 유지. sid/jti 누락 시 null(구 토큰 호환 거부).
- 신규 타입 `RefreshClaims(userId: Long, sessionId: String, jti: String)` (carry-security).
- `refreshTokenExpiration` 노출(getter 또는 어댑터가 `JwtProperties` 직접 주입) — TTL 진실원천.

### 5.2 carry-user `AuthTokenPort` (outbound)
- `issueRefreshToken(userId: Long): IssuedRefreshToken` — **새 세션** (sid+jti 신규 생성).
- `issueRefreshToken(userId: Long, sessionId: String): IssuedRefreshToken` — **회전** (sid 고정, jti 신규).
- `parseRefreshToken(token: String): RefreshTokenClaims?` — `Long?` → `RefreshTokenClaims(userId, sessionId, jti)`.
- 신규 타입:
  - `IssuedRefreshToken(token: String, sessionId: String, jti: String, ttl: Duration)` — **TTL을 토큰과 함께 반환** → TTL 진실원천=JwtProperties 한 곳, store는 ttl을 인자로 받아 generic 유지.
  - `RefreshTokenClaims(userId: Long, sessionId: String, jti: String)`

### 5.3 carry-user `RefreshTokenStorePort` (outbound, 신규)
```
fun save(sessionId: String, jti: String, ttl: Duration)   // 신규 세션 + 회전 공용(set overwrite)
fun currentJti(sessionId: String): String?
fun delete(sessionId: String)                              // logout + 재사용 폐기, 멱등
```

### 5.4 carry-user `RedisRefreshTokenStore` (adapter.outbound.auth, 신규)
- `RedisTemplate<String, Any>` 위임. `opsForValue().set(key, jti, ttl)` / `get` / `RedisTemplate.delete`.
- 키 prefix `auth:refresh:`.
- `@ConditionalOnBean(RedisConnectionFactory)` 정합(carry-infra-redis와 동일 게이팅) — local/dev/prod에 Redis 존재.

### 5.5 carry-user `JwtAuthTokenAdapter`
- UUID 생성(infra 책임): `issueRefreshToken`에서 `sid`/`jti`를 `UUID.randomUUID().toString()`로 생성.
- TTL: `JwtProperties.refreshTokenExpiration`(ms) → `Duration.ofMillis(...)`로 변환해 `IssuedRefreshToken`에 담음.
- `parseRefreshToken`: `JwtProvider.RefreshClaims` → 포트 `RefreshTokenClaims` 매핑(SignupClaims→SignupIdentity 선례).

### 5.6 carry-user `AuthService` (application)
- 생성자에 `RefreshTokenStorePort` 추가.
- `issueTokens(user)`: `issued = issueRefreshToken(user.id)` → `store.save(issued.sessionId, issued.jti, issued.ttl)` → `TokenPair(access, issued.token)`.
- `refresh(refreshToken): TokenPair` (반환 타입 `String`→`TokenPair`): §4.2 로직.
- `logout(refreshToken)`: §4.3 로직 (신규).

### 5.7 carry-user `AuthUseCase` (inbound)
- `refresh(refreshToken: String): String` → `: TokenPair`.
- `logout(refreshToken: String)` 추가.

### 5.8 carry-user `AuthController` + DTO
- `POST /refresh`: 응답 `AccessTokenResponse` → **`TokenResponse`**(access+refresh, 이미 존재).
- `POST /logout`: 신규, body `RefreshRequest`, `204 No Content`. 멱등.
- `AccessTokenResponse`: refresh가 유일 소비자였으므로 미사용 시 제거.

### 5.9 carry-user `AuthExceptions`
- 신규 `RefreshTokenReuseException` — `ErrorCode.AUTH_TOKEN_INVALID`(동일 **401** 봉투, #79 통일분 재사용). 별도 타입은 **warn 로깅·향후 감사로그(#2) 연결**용 식별자.

## 6. API 변경 요약

| 엔드포인트 | 변경 전 | 변경 후 |
|-----------|---------|---------|
| `POST /api/v2/auth/refresh` | `{accessToken}` 200 | `{accessToken, refreshToken}` 200 (회전) |
| `POST /api/v2/auth/logout` | (없음) | 신규, 204, 멱등 |

⚠️ refresh 응답 계약 변경 — 클라이언트는 회전된 새 refresh를 저장해야 함. **Carry 미런칭(실트래픽 0) → 마이그레이션 무관**. 기존 발급된 구 refresh(sid/jti 없음)는 parse에서 null → 401 → 재로그인(허용).

## 7. 테스트 (TDD, Red→Green)

| 레이어 | 케이스 |
|--------|--------|
| `JwtProvider` 단위 | sid/jti 라운드트립(create→parse 일치), 잘못된 purpose 거부, sid/jti 누락 토큰 거부 |
| `RedisRefreshTokenStore` 단위 | save(TTL 전달)·get·delete (mockk RedisTemplate, geo 패턴) |
| `JwtAuthTokenAdapter` 단위 | issueRefreshToken 새 세션 vs 회전(sid 고정/jti 변화), ttl=refreshTokenExpiration, parse 매핑 |
| `AuthService` 단위(store mock) | 회전 정상(save 새 jti·TokenPair 반환) / 재사용(stored≠jti → delete 호출 + throw) / null(throw) / logout(delete 호출) |
| `carry-app` 통합(Redis 컨테이너) | 로그인→refresh 회전→이전 refresh 재사용 시 세션 폐기(이후 회전된 것도 무효)→logout 후 refresh 거부 |
| 라이브 스모크 | docker 풀스택: 로그인→refresh(회전 확인)→옛 토큰 재사용→세션 폐기→logout |

## 8. 검증 흐름

전체 `compileTestKotlin`(cross-module 호출자 깨짐 검출) → 영향모듈 `:test`(carry-security/carry-user) → `:carry-app:test`(Testcontainers) → **라이브 풀스택 스모크**(`MANAGEMENT_TRACING_ENABLED=false`). 이슈 먼저 → PR base develop → **dev 머지=사용자 게이트**.

## 9. 영향 범위 (호출자 정합)

- `AuthService` 생성자 시그니처 변경 → carry-user 테스트의 AuthService 생성 지점.
- `AuthUseCase.refresh` 반환 타입 변경 → `AuthController.refresh`, 통합 테스트.
- `AuthTokenPort.parseRefreshToken`/`issueRefreshToken` 시그니처 변경 → `JwtAuthTokenAdapter`, AuthService, 관련 테스트 mock.
- `JwtProvider.parseRefreshToken`/`createRefreshToken` 시그니처 변경 → JwtProvider 테스트, 어댑터.
