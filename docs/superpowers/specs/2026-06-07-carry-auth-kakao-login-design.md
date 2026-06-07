# Carry 로그인/토큰 발급 wiring — Kakao 검증 + 2-step 가입 + refresh

- 작성일: 2026-06-07
- 대상 레포: `carry-platform` (분기: `feature/auth-kakao-login`, base `origin/develop` @ `297ca16`)
- 선행: RBAC(JWT role 실반영) + 메서드 시큐리티(#75) 머지 완료

## 1. 배경 / 문제

RBAC/JWT 기반은 #75로 구축됐으나 **토큰을 발급하는 로그인 경로가 없다**:
- `JwtProvider.createAccessToken`/`createRefreshToken` 호출자 부재(테스트 제외).
- `AuthController` 부재 — `AuthUseCase.loginOrRegister`(find-or-create)는 존재하나 아무도 호출하지 않음.
- Kakao 신원을 **서버가 검증할 방법이 없음**(OAuth 클라이언트·설정 전무).

즉 #75에서 단단히 만든 인증/인가가 **운영에서 도달 불가능**하다. 이 작업으로 인증 플로우를 실제 작동시킨다.

## 2. 범위

### 포함
- Kakao access token **서버측 검증**(`kapi.kakao.com/v2/user/me`).
- **2-step 가입**(신규): Kakao 신원 확보 → 가입 폼(name/phone/email) → 유저 생성 + 토큰.
- 기존 유저 즉시 로그인(토큰 발급).
- access token **refresh**.
- JWT `purpose` claim(ACCESS/REFRESH/SIGNUP)으로 토큰 종류 분리(보안 하드닝).

### 제외 (후속 / YAGNI)
- logout, refresh 회전(rotation)·Redis 저장·폐기(무상태 결정과 배치).
- 비밀번호 로그인·타 OAuth provider(현 OAuthProvider=KAKAO only).
- 프로필 수정(별도 UserController 책임).
- access token에 prefill 외 부가 클레임.

## 3. 확정 결정

| # | 결정 | 선택 | 근거 |
|---|------|------|------|
| 1 | Kakao 신원 확보 | **서버가 access token 검증**(user/me) | 모바일앱 표준·안전. client 위조 차단. |
| 2 | 신규 프로필 결손 처리 | **2-step 가입**(Kakao=신원, 폼=name/phone/email) | strict Email/Phone VO 충족. Kakao phone(비즈앱 게이트) 의존 회피. |
| 3 | refresh 전략 | **무상태 서명 JWT**(저장X) | 무상태 JWT 서사 일관. 회전·폐기는 후속. |
| 4 | 엔드포인트 | **login + signup + refresh** | 2-step 로그인 플로우의 최소 일습. |
| 5 | 토큰 종류 분리 | **purpose claim** ACCESS/REFRESH/SIGNUP | refresh/signup 토큰의 베어러 악용 차단(현 구멍 봉합). |

## 4. 플로우

```
[client: Kakao SDK 로그인 → kakaoAccessToken]

POST /api/v2/auth/login { kakaoAccessToken }
  → 서버가 user/me 호출(Bearer)로 신원 검증 → (oauthId, email?, nickname?)
  → findByOAuthInfo(KAKAO, oauthId)?
      존재 → 200 { status: "REGISTERED", accessToken, refreshToken }
      없음 → 200 { status: "REGISTRATION_REQUIRED", signupToken, prefill: { email?, nickname? } }

POST /api/v2/auth/signup { signupToken, name, phone, email }   // signupToken이 Kakao 신원 보유
  → signupToken 검증(purpose=SIGNUP) → identity(provider, oauthId)
  → loginOrRegister(provider, oauthId, email, name, phone)
  → 201 { accessToken, refreshToken }

POST /api/v2/auth/refresh { refreshToken }
  → refreshToken 검증(purpose=REFRESH) → userId → user.role 조회 → 200 { accessToken }
```

`/api/v2/auth/**`는 이미 permitAll. **signup/refresh 토큰은 SecurityContext가 아니라 요청 바디로 전달**되어 엔드포인트가 자체 검증한다(인증 필터 우회).

## 5. 토큰 종류 분리 (보안 하드닝)

JWT에 `purpose` claim 도입:
- `createAccessToken(userId, role)` → `purpose=ACCESS` + `role`
- `createRefreshToken(userId)` → `purpose=REFRESH`
- `createSignupToken(provider, oauthId, email?, nickname?)` → `purpose=SIGNUP`, subject=oauthId(유저 아님)

`JwtAuthenticationFilter`가 쓰는 `parseToken`은 **purpose=ACCESS만** 인증으로 수용한다. (현재는 refresh 토큰을 베어러로 제시하면 role claim 부재→CUSTOMER로 인증되는 구멍 존재 → 이걸 봉합.) `/refresh`는 REFRESH만, `/signup`은 SIGNUP만 수용.

## 6. 컴포넌트 (헥사고날, carry-user)

`carry-user`에 `carry-security` 의존 신설(토큰 발급).

- **inbound** `AuthUseCase` 확장:
  - `loginWithKakao(kakaoAccessToken): LoginResult`(REGISTERED+토큰 | REGISTRATION_REQUIRED+signupToken+prefill)
  - `completeSignup(signupToken, name, phone, email): TokenPair`
  - `refresh(refreshToken): AccessToken`
  - 기존 `loginOrRegister`는 내부 단계로 재사용.
- **outbound 포트**
  - `OAuthProfileClient.fetchKakaoProfile(accessToken): OAuthProfile(oauthId, email?, nickname?)` — 어댑터 `KakaoOAuthClient`(RestClient → `${kakao.api.base-url}/v2/user/me`). client secret 불요(user/me는 베어러만).
  - `AuthTokenPort`(issueAccess/issueRefresh/issueSignup/parseRefresh→userId/parseSignup→identity) — 어댑터 `JwtAuthTokenAdapter`가 carry-security `JwtProvider`에 위임(앱 레이어를 JWT 구현에서 격리).
- **inbound 어댑터** `AuthController`(carry-user) `POST /login·/signup·/refresh` + DTO.
- **config**: `kakao.api.base-url`(기본 `https://kapi.kakao.com`) — **e2e 스텁 오버라이드 seam(§9)**.

> **모듈 경계**: Kakao 검증은 `OAuthProfileClient` 단일 경계 뒤에 격리(테스트·e2e 스텁이 이 한 점만 대체). 토큰 발급은 `AuthTokenPort` 뒤에 격리. 두 포트가 인증 플로우의 외부 의존 전부.

## 7. 에러 처리 (ErrorCode 추가)

| 상황 | 응답 |
|---|---|
| Kakao 토큰 무효/만료(user/me 401) | 401 (`OAUTH_TOKEN_INVALID`) |
| Kakao API 장애/타임아웃 | 503 (`OAUTH_PROVIDER_UNAVAILABLE`) |
| signup/refresh 토큰 무효·만료·purpose 불일치 | 401 (`AUTH_TOKEN_INVALID`) |
| signup 입력(name/phone/email) 형식 오류 | 400 (VO 검증 → `INVALID_INPUT`) |

(401은 §5 기준 — 인증 필터 밖 자체검증이라 `GlobalExceptionHandler`의 BusinessException 경유로 ApiResponse 봉투를 따른다.)

## 8. 테스트 (TDD)

- **JwtProvider**: ACCESS/REFRESH/SIGNUP round-trip + purpose 격리(parseToken이 비-ACCESS 거부, parseRefresh가 ACCESS 거부, parseSignup이 identity 복원).
- **AuthService**(포트 mock): 기존유저 login→토큰 / 신규 login→signupToken+REGISTRATION_REQUIRED / completeSignup→유저 생성+토큰 / refresh→role 재조회 후 새 access / 무효 토큰·Kakao 실패 에러 매핑.
- **KakaoOAuthClient**: `MockRestServiceServer`로 user/me 응답 매핑·401·5xx.
- **AuthController**(carry-app 슬라이스): 3 엔드포인트 happy+error, permitAll(인증 불요).
- 전체 `./gradlew test` GREEN(Testcontainers IT·ArchUnit 포함).

## 9. E2E 테스트 전략 — Kakao 프론트 연동 후 (사용자 요구: "충분한 계획")

OAuth e2e의 본질적 난점: **실 Kakao 로그인 UI 자동화는 brittle**(동의창·캡차·2FA·레이트리밋·계정잠금)하고 CI를 외부 제공자에 결합시킨다. 따라서 **결정적 백엔드 경계 스텁을 주력**으로 한 계층 전략을 채택한다. 핵심 설계 전제: Kakao 의존이 `OAuthProfileClient`(+`kakao.api.base-url`) **단일 경계 한 점**에만 있다(§6) → 그 점만 대체하면 전 구간이 결정적이 된다.

**계층 1 — 백엔드 통합 (지금 구현):**
`OAuthProfileClient` mock / `KakaoOAuthClient`를 `MockRestServiceServer`로. Kakao 외부 의존 0. (§8)

**계층 2 — 프론트 e2e (주력·결정적): Kakao 경계 스텁.**
- e2e/스테이징 환경에서 `kakao.api.base-url`을 **스텁 서버**(WireMock 또는 경량 fake, docker-compose에 1 서비스)로 지정. 스텁은 사전 정의한 테스트 access token → 결정적 프로필(oauthId/email/nickname) 매핑을 반환.
- 프론트는 두 방식 중 택:
  - (a) **실 Kakao SDK가 스텁을 가리키도록** 설정(SDK 도메인 오버라이드 가능 시) — 가장 실제에 가까움.
  - (b) e2e 전용 플래그로 **알려진 테스트 kakaoAccessToken**을 `/auth/login`에 직접 전달(SDK 우회) — 단순·안정.
- Playwright로 프론트→백엔드→스텁 전 구간 검증. 시나리오: 신규(REGISTRATION_REQUIRED→가입폼→토큰) / 기존(즉시 토큰) / refresh / 무효 토큰(401).
- **권장 기본값 = (b)**: SDK UI 자동화를 피해 안정적. (a)는 보강용.

**계층 3 — 비프로덕션 dev-login(편의, 후속 affordance):**
다른 기능 e2e가 매번 Kakao 플로우를 거치지 않도록, `@Profile("!prod")` 게이트된 `POST /api/v2/auth/dev-login { userId | seedKey }` 지름길로 인증 세션을 즉시 확보. **본 스코프 밖**이나, §6 설계(토큰 발급이 `AuthTokenPort` 뒤에 격리)가 이를 저비용으로 허용한다. 프론트 e2e 셋업 시 추가 권장. (prod 노출 금지 — pfplay `EasyUserManagementController @Profile("!prod")` 선례.)

**계층 4 — 실 Kakao 스모크(수동/주기적, CI 게이트 외):**
전용 테스트 Kakao 계정으로 스테이징에서 1회성 수동 또는 주기 스모크. 실제 토큰 발급 경로의 회귀만 확인. brittle하므로 **CI 차단 게이트엔 미포함**.

**프론트 측 함정 (전이 — pfplay 소셜로그인 경험 반영):**
- OAuth 콜백 페이지는 히스토리 잔존 시 뒤로가기로 1회용 코드/PKCE 재실행 → **콜백은 `router.replace`**(push 금지).
- 콜백 전 me-fetch 401은 정상·무해(좀비 방어) — fresh me + isNewUser 다중 방어.
- preview/스테이징 공유 alias 레이스 → e2e concurrency 가드.
- (Carry 프론트 스택 확정 시 위 항목을 프론트 e2e 설계에 반영.)

## 10. 영향 / 회귀 리스크

- `JwtAuthenticationFilter.parseToken`에 purpose=ACCESS 게이트 추가 → 새 `createAccessToken`(purpose=ACCESS 포함) 사용 시 RBAC 동작 불변. #75의 슬라이스 테스트는 `authentication()` 포스트프로세서라 무관. (단, 라이브 RBAC 경로는 새 access token 발급이 필요 → 본 작업으로 비로소 완전 가동.)
- `carry-user → carry-security` 모듈 의존 신설.
- 기존 `AuthService.loginOrRegister` 시그니처 불변(내부 재사용) → `AuthServiceTest` 영향 없음.
