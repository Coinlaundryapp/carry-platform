# Refresh 토큰 회전/폐기 + Logout 설계 (A-3)

> 작성일: 2026-06-07 · 브랜치 `feature/refresh-token-rotation-logout` (base `origin/develop` `579975e`)
> 백로그 출처: `2026-06-07-carry-remaining-backlog.md` P1 #1. 직전 인증 작업(#77 Kakao 로그인/refresh wiring)의 직접 연장.
> 리뷰 1회 반영(원자성·유예창·직렬화·test 컨텍스트·장애정책) 후 v2.

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

## 3. 모델 — 세션 단위 allowlist + 원자적 회전 + 유예창

refresh 토큰에 claim 2개 추가:
- `sid` (session id, UUID): **한 디바이스 로그인 동안 회전돼도 불변**. 세션의 정체성.
- `jti` (token id, UUID): **토큰마다 고유**. 회전마다 갱신.

Redis는 **세션별 상태**를 하나의 hash로 보관한다:

```
Key: auth:refresh:{sid}   (hash)   TTL: refresh 수명(기본 7d), 회전 시 갱신
  cur    = 현재 유효 jti
  prev   = 직전(직전 회전 전) jti        ← 유예창 대상
  prevAt = prev가 기록된 서버시각(ms)     ← 유예 만료 판정
```

- 디바이스별 다중 세션 = sid가 디바이스마다 다른 UUID → 키 분리(D1).
- "제시된 jti가 cur도 prev(유예 내)도 아님" = 이미 회전된 옛 토큰의 재사용 = 탈취 신호 → 키 삭제로 세션 폐기(D2).
- logout = 키 삭제(D3, refresh만 폐기). access는 자연 만료.
- TTL은 회전마다 7d로 갱신(슬라이딩) → **활성 디바이스 세션은 계속 쓰면 무기한 유지**(의도된 동작, 리뷰 v2 #5). 미사용 7d 경과 시 키 만료 → 다음 refresh가 ABSENT.

### 3.1 원자성 (리뷰 #3·#4 해소)

탐지(GET)와 회전/폐기(SET/DEL)를 **단일 Lua 스크립트**로 원자 실행한다. 비원자 GET-then-SET이 만드는 두 결함을 차단:
- **TOCTOU**: 재사용 탐지와 `DEL` 사이에 정상 회전이 끼어들어 세션이 되살아나는 창 제거.
- **정상 동시요청 self-lock**: 클라이언트의 정상 재시도(응답 유실 후 재요청, 더블탭, 네트워크 retry)로 같은 토큰이 짧은 간격에 2회 제시되면, 단순 CAS는 두 번째를 "재사용"으로 오판해 세션을 폭파한다.

### 3.2 유예창 (리뷰 #3, RFC 6819 leeway)

직전 jti(`prev`)는 **`prevAt`로부터 grace(기본 10s) 이내**라면 재사용으로 보지 않고 **정상 재시도로 간주해 다시 회전**한다.

**경계 고정 = 시계만 고정, 값은 전진**(리뷰 v2 #1 BLOCKER 해소): 유예 재시도 시 `prev`를 **나가는 `cur` 값으로 전진**시키되 `prevAt`는 **갱신하지 않는다**. `prev`를 첫 토큰에 고정하면(값+시계 둘 다 고정) 회전 산출물이 고아가 된다 — 동시 2요청에서 Req1이 `cur:A→B` 회전 후 `B`를 반환했는데 Req2(같은 A 재시도)가 `cur←C`로 덮으면 `B`가 `cur`도 `prev`도 아니게 되고, 클라이언트가 `B`를 보관했다 제시하면 거짓 REUSE로 세션이 폭파된다. **값을 전진(`prev←B`)하면** 클라가 `B`를 들든 `C`를 들든 정상 회전되고, `prevAt`를 고정해 윈도우는 슬라이딩하지 않는다.

이는 Auth0 등 성숙 구현의 `reuse interval`과 동형. **잔여 노출(리뷰 v2 #2)**: grace 창(≤10s) 동안 탈취자도 1회 회전을 얻을 수 있으나 `prevAt` 고정으로 무한 연장 불가 — 창 만료 후 첫 stale 제시에서 탐지 발화. **단일 슬롯 한계**: 직전 1개만 관용하므로 *동일 토큰 3회 동시제시* 같은 병리적 경우는 REUSE(세션 리셋)로 안전 degrade — 정상 더블탭(2회)은 커버.

## 4. 흐름

### 4.1 로그인/가입 (issueTokens)
1. `sid = UUID`, `jti = UUID`
2. `store.start(sid, jti)` → `HSET key cur=jti` + `PEXPIRE 7d`
3. access + refresh(sub, sid, jti) 반환

### 4.2 refresh (회전)
1. 서명·`purpose=REFRESH` 검증 → `userId, sid, presentedJti` 추출 (실패 → 401 `AuthTokenInvalidException`)
2. user 조회 + active 확인 (실패 → 401)
3. `newJti = UUID` 로 새 refresh 토큰 발급(아직 미확정)
4. `result = store.rotate(sid, presentedJti, newJti)` — 원자 Lua (KEYS=[key], ARGV=[presented, newJti, ttlMs, graceMs]):
   - `cur` 없음 → **`ABSENT`** (로그아웃됨/만료)
   - `cur == presentedJti` → 회전: `prev←cur, prevAt←now, cur←newJti`, `PEXPIRE ttlMs` → **`ROTATED`**
   - `prev == presentedJti` 이고 `now - prevAt ≤ grace` → 유예 재시도: `prev←cur(값 전진), cur←newJti, prevAt 미갱신(시계 고정)`, `PEXPIRE ttlMs` → **`ROTATED`** (§3.2)
   - 그 외 → 진짜 재사용: `DEL key` → **`REUSE`**

   ```lua
   local cur = redis.call('HGET', KEYS[1], 'cur')
   if not cur then return 'ABSENT' end
   local t = redis.call('TIME')                       -- {sec, usec}
   local now = t[1] * 1000 + math.floor(t[2] / 1000)  -- ms (리뷰 v2 #3)
   if cur == ARGV[1] then
     redis.call('HSET', KEYS[1], 'prev', cur, 'prevAt', now, 'cur', ARGV[2])
     redis.call('PEXPIRE', KEYS[1], ARGV[3]); return 'ROTATED'
   end
   local prev = redis.call('HGET', KEYS[1], 'prev')
   if prev and prev == ARGV[1] then
     local prevAt = tonumber(redis.call('HGET', KEYS[1], 'prevAt'))  -- StringRedisTemplate → 문자열, tonumber 필수
     if prevAt and (now - prevAt) <= tonumber(ARGV[4]) then
       redis.call('HSET', KEYS[1], 'prev', cur, 'cur', ARGV[2])      -- prev 값 전진, prevAt 고정
       redis.call('PEXPIRE', KEYS[1], ARGV[3]); return 'ROTATED'
     end
   end
   redis.call('DEL', KEYS[1]); return 'REUSE'
   ```
   `newJti`는 결과 확정 전 생성되나(step 3) ABSENT/REUSE 시 미반환·미저장 폐기 — 순수 생성(UUID+서명)이라 부작용 없음(리뷰 v2 #4).
5. 매핑:
   - `ROTATED` → 새 access + (newJti 담긴) 새 refresh 둘 다 반환 (`TokenPair`)
   - `ABSENT` → 401 `AuthTokenInvalidException`
   - `REUSE` → 401 `RefreshTokenReuseException` (warn 로그; 세션은 Lua가 이미 폐기)

### 4.3 logout (`POST /api/v2/auth/logout`)
1. `parseRefreshToken(token)` — **서명+purpose 완전검증** 후에만 `sid` 추출 (리뷰 #7: 미검증 토큰에서 sid를 뽑아 임의 세션을 evict하는 DoS 차단)
2. 검증 실패(서명불량/만료/purpose불일치) → 폐기할 세션 없음 → **204** (세션 존재 여부 비노출, Redis 미접촉)
3. 검증 성공 → `store.delete(sid)` = `DEL key` (멱등, 없는 키 삭제=no-op) → **204**

## 5. 헥사고날 배치 (기존 패턴 준수)

### 5.1 carry-security `JwtProvider`
- `createRefreshToken(userId: Long, sessionId: String, jti: String): String` — **순수 빌더**(sid/jti 주입, UUID 생성 안 함). `.withClaim("sid", sessionId).withJWTId(jti)` 추가.
- `parseRefreshToken(token): RefreshClaims?` — 반환 `Long?` → `RefreshClaims(userId, sessionId, jti)`. purpose 검증 유지. sid/jti 누락 토큰(구 무상태 토큰) → null.
- 신규 타입 `RefreshClaims(userId: Long, sessionId: String, jti: String)` (carry-security).

### 5.2 carry-security `JwtProperties`
- `refreshTokenRotationGraceMillis: Long = 10000` 추가(유예창, §3.2). `jwt.refresh-token-rotation-grace` 키.

### 5.3 carry-user `AuthTokenPort` (outbound)
- `issueRefreshToken(userId: Long): IssuedRefreshToken` — **새 세션**(sid+jti 신규).
- `issueRefreshToken(userId: Long, sessionId: String): IssuedRefreshToken` — **회전**(sid 고정, jti 신규).
- `parseRefreshToken(token): RefreshTokenClaims?` — `Long?` → `RefreshTokenClaims(userId, sessionId, jti)`.
- 신규 타입: `IssuedRefreshToken(token: String, sessionId: String, jti: String)`, `RefreshTokenClaims(userId: Long, sessionId: String, jti: String)`.
- UUID 생성은 어댑터(infra) 책임.

### 5.4 carry-user `RefreshTokenStorePort` (outbound, 신규)
```kotlin
enum class RotateResult { ROTATED, ABSENT, REUSE }

interface RefreshTokenStorePort {
    fun start(sessionId: String, jti: String)
    fun rotate(sessionId: String, presentedJti: String, newJti: String): RotateResult
    fun delete(sessionId: String)
}
```
- 원자성·유예·TTL은 **어댑터 내부**에 캡슐화 → 포트는 의도만 노출(서비스는 `RotateResult`로 분기). ttl/grace를 인자로 흘리지 않아 포트가 깔끔.

### 5.5 carry-user `RedisRefreshTokenStore` (adapter.outbound.auth, 신규)
- **`StringRedisTemplate`** 위임 (리뷰 #6: 공유 `RedisTemplate<String,Any>`는 `GenericJackson2JsonRedisSerializer`로 값이 JSON-인용(`"uuid"`)돼 Lua의 raw string 비교가 깨짐 → raw string 직렬화 강제).
- `rotate`는 `RedisTemplate.execute(RedisScript<String>, keys, args...)`로 Lua 1회 실행, 반환 문자열 → `RotateResult` 매핑. Lua는 `redis.call('TIME')`로 서버시각 사용(클럭 주입 불필요·원자).
- TTL=`JwtProperties.refreshTokenExpiration`, grace=`JwtProperties.refreshTokenRotationGraceMillis` 주입(carry-user는 carry-security 의존 → JwtProperties 빈 주입 가능; JwtProvider와 동일 source-of-truth=JwtProperties).
- 키 prefix `auth:refresh:`.

### 5.6 carry-user `InMemoryRefreshTokenStore` (adapter.outbound.auth, 신규 — fallback)
- `ConcurrentHashMap<sid, Entry(cur, prev, prevAt)>` + `synchronized` rotate로 **Lua와 동일 의미**(§4.2 분기·prev 값전진/prevAt 고정 포함) 구현. `now`는 주입된 `Clock.millis()`(테스트 결정성, Lua의 `TIME` 대응).
- 용도: ① Redis 미존재 환경(test 프로파일은 `RedisAutoConfiguration` 제외) ② 알고리즘의 결정적 단위테스트.
- ⚠️ **의미 동등성이 load-bearing**(리뷰 v2 #6): InMemory와 실 Lua가 grace 경계에서 동일 `RotateResult`를 내야 함 → §8에 동일 시나리오 매트릭스를 양쪽에 적용하는 parity 테스트.

### 5.7 carry-user `RefreshTokenStoreConfig` (adapter.outbound.auth, 신규 — 와이어링)
- **geo `GeocodingResilienceConfig` 선례 차용**(리뷰 #1): `@Bean fun refreshTokenStorePort(@Autowired(required=false) stringRedisTemplate: StringRedisTemplate?, jwtProperties)` → Redis 있으면 `RedisRefreshTokenStore`, 없으면 `InMemoryRefreshTokenStore`(warn 로그).
- 이유: `AuthService`가 `RefreshTokenStorePort`를 **필수 생성자 의존**으로 받는데, test 프로파일(application-test.yml이 `RedisAutoConfiguration` exclude)엔 `StringRedisTemplate` 빈이 없다. 어댑터에 `@ConditionalOnBean`만 달면 빈이 아예 없어 `@SpringBootTest` 컨텍스트가 "no RefreshTokenStorePort"로 깨진다. nullable 와이어링 + InMemory fallback이 정석(geo와 동형).

### 5.8 carry-user `JwtAuthTokenAdapter`
- UUID 생성(infra): `issueRefreshToken`에서 `sid`/`jti` = `UUID.randomUUID().toString()`.
- `parseRefreshToken`: `JwtProvider.RefreshClaims` → 포트 `RefreshTokenClaims` 매핑(SignupClaims→SignupIdentity 선례).

### 5.9 carry-user `AuthService` (application)
- 생성자에 `RefreshTokenStorePort` 추가.
- `issueTokens(user)`: `issued = issueRefreshToken(user.id)` → `store.start(issued.sessionId, issued.jti)` → `TokenPair(access, issued.token)`.
- `refresh(refreshToken): TokenPair` (반환 `String`→`TokenPair`): §4.2.
  - **`@Transactional(readOnly = true)` 제거**(리뷰 #2): 메서드의 주효과가 이제 Redis 상태변경(회전/폐기)이라 readOnly는 오도. 클래스 기본(read-write) 사용. ⚠️ **Redis 연산은 JPA 트랜잭션 밖**(롤백 안 됨) — DB 조회(findById) 후 Redis 회전 순서 유지.
- `logout(refreshToken)`: §4.3 (신규).

### 5.10 carry-user `AuthUseCase` (inbound)
- `refresh(refreshToken): String` → `: TokenPair`.
- `logout(refreshToken: String)` 추가.

### 5.11 carry-user `AuthController` + DTO
- `POST /refresh`: 응답 `AccessTokenResponse` → **`TokenResponse`**(access+refresh, 이미 존재).
- `POST /logout`: 신규, body `RefreshRequest`, **204 No Content**. 멱등.
- `AccessTokenResponse`: refresh가 유일 소비자였으므로 제거.

### 5.12 carry-user `AuthExceptions`
- 신규 `RefreshTokenReuseException : BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "...")` (리뷰 #8: 반드시 `BusinessException` 서브클래스 → 기존 `@RestControllerAdvice`가 **동일 401 봉투** 생성, 핸들러 변경 불필요). 별도 타입은 warn 로깅·향후 감사로그(#2) 연결용 식별자.

## 6. Redis 장애 정책 (리뷰 #5 — fail-closed)

런타임 Redis 접속불가 시 연산별 정책. 세 연산 모두 **예외를 전파(catch 안 함) = fail-closed**:
- **login(`start`) 실패** → 추적불가 토큰을 발급하지 않음(로그인 5xx). 추적 못 할 refresh를 쥐여주지 않는다.
- **refresh(`rotate`) 실패** → 5xx 전파(재발급 거부). 무상태 우회 없음.
- **logout(`delete`) 실패** → 5xx 전파. 보안 연산(폐기)이 조용히 no-op 되지 않게 정직하게 실패(클라 재시도). "멱등 204"는 **Redis 정상 + 없는 키 삭제** 시의 의미이지 Redis 다운 시가 아니다.

## 7. API 변경 요약

| 엔드포인트 | 변경 전 | 변경 후 |
|-----------|---------|---------|
| `POST /api/v2/auth/refresh` | `{accessToken}` 200 | `{accessToken, refreshToken}` 200 (회전) |
| `POST /api/v2/auth/logout` | (없음) | 신규, 204, 멱등 |

⚠️ refresh 응답 계약 변경 — 클라이언트는 회전된 새 refresh를 저장해야 함. **Carry 미런칭(실트래픽 0) → 마이그레이션 무관**. 기존 발급된 구 refresh(sid/jti 없음)는 parse에서 null → 401 → 재로그인(허용).
⚠️ 프론트 노트(리뷰 #10): logout 후에도 **기존 access는 자연 만료(≤1h)까지 유효**(D3). 서버 즉시 무효화 아님 — 클라가 access를 즉시 폐기해야 체감 로그아웃.

## 8. 테스트 (TDD, Red→Green)

| 레이어 | 케이스 |
|--------|--------|
| `JwtProvider` 단위 | sid/jti 라운드트립(create→parse 일치), 잘못된 purpose 거부, sid/jti 누락 토큰 거부 |
| **parity 매트릭스**(InMemory 단위 + Redis 통합 동일 시나리오) | start→rotate(정상) / 재사용(옛 jti→REUSE+키삭제) / ABSENT(없는 sid) / 유예 재시도(prev within grace→ROTATED) / 유예 만료(prev beyond grace→REUSE) / **덮인 cur 제시**(동시 2요청 산출 B를 grace 내 제시→ROTATED, v2 #1 회귀) / 동일 토큰 3회(→REUSE 안전 degrade). InMemory는 Clock 주입으로, Redis는 실 Lua로 **동일 기대값** |
| `RedisRefreshTokenStore` 단위(mockk) | execute(script, key, args) 호출·반환문자열→RotateResult 매핑 |
| `JwtAuthTokenAdapter` 단위 | 새 세션 vs 회전(sid 고정·jti 변화), parse 매핑 |
| `AuthService` 단위(store mock) | 회전(ROTATED→TokenPair, start/rotate 호출) / REUSE(→RefreshTokenReuseException) / ABSENT(→AuthTokenInvalidException) / logout(검증 성공→delete 호출, 검증 실패→delete 미호출) |
| `carry-app` `AuthControllerTest` | refresh 200+access+refresh / logout 204 (mock UseCase) |

⚠️ 기존 `carry-app/.../AuthControllerTest`의 "refresh는 200과 새 access 반환" 케이스는 `authUseCase.refresh`가 `String`→`TokenPair` 반환으로 바뀌어 **수정 필수**(리뷰 #9): mock 반환·assertion에 `refreshToken` 추가. 기존 `carry-user/.../AuthServiceTest`의 AuthService 생성자 인자도 정합.

## 9. 검증 흐름

전체 `compileTestKotlin`(cross-module 호출자 깨짐 검출) → 영향모듈 `:test`(carry-security/carry-user) → `:carry-app:test`(Testcontainers) → **라이브 풀스택 스모크**(`MANAGEMENT_TRACING_ENABLED=false`). 이슈 먼저 → PR base develop → **dev 머지=사용자 게이트**.

## 10. 영향 범위 (호출자 정합)

- `AuthService` 생성자 변경 → `AuthServiceTest`.
- `AuthUseCase.refresh` 반환 변경 → `AuthController.refresh`, `AuthControllerTest`.
- `AuthTokenPort.parseRefreshToken`/`issueRefreshToken` 변경 → `JwtAuthTokenAdapter`, `AuthService`, mock.
- `JwtProvider.parseRefreshToken`/`createRefreshToken` 변경 → JwtProvider 테스트, 어댑터.
