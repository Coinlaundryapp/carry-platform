# 소셜 로그인 다중 provider(카카오·네이버·구글) — 설계

> 작성일: 2026-07-14
> 상태: 설계 승인 (구현 전)
> 범위: carry-platform(carry-user 백엔드) + carry-app(customer-web 프론트). 고객 앱 전용.

---

## 1. 배경과 현재 구조

현재 소셜 로그인은 **카카오 단일**이고, 유일한 경로가 **네이티브 WebView(AndroidBridge)가 카카오 토큰을 넘기는 방식**이다. 브라우저 OAuth 코드 흐름은 의도적으로 비활성(`/api/kakao`가 에러로 리다이렉트, "옵션 B 보류").

트러스트 모델: **백엔드가 provider 액세스 토큰을 받아 서버에서 검증**한다(`kapi.kakao.com/v2/user/me` 호출). NextAuth는 토큰을 백엔드로 전달하는 Credentials passthrough일 뿐이고 자체 검증은 하지 않는다.

핵심 코드 현황:
- 프론트 `auth.ts`: NextAuth v5 Credentials 단일 provider(필드 `kakaoAccessToken`, `devRole`). `@auth/core@0.34.2`에 `providers/{kakao,naver,google}` 빌트인 존재(설치됨).
- 백엔드 `AuthController` `POST /api/v2/auth/login`은 `LoginRequest(kakaoAccessToken)` — **provider 필드 없음**. `AuthService.loginWithKakao`가 `OAuthProfileClient.fetchKakaoProfile`로 검증.
- `OAuthProvider { KAKAO, DEV }`. 신원키 = `user_users(oauth_provider, oauth_id)` UNIQUE. `email` 전역 UNIQUE NOT NULL.
- signup 토큰은 이미 `provider` 클레임을 담아 provider-무관(enum만 확장하면 됨). 소셜 가입은 전부 `role=CUSTOMER`.

## 2. 결정 사항 (브레인스토밍 확정)

1. **웹 OAuth 흐름 신규 구축** — NextAuth 빌트인 OAuth provider로 브라우저 로그인. (네이티브 토큰 모델 대신.)
2. **카카오도 웹 OAuth 활성화** — 3사 전부 브라우저에서 일관 동작. WebView 카카오 네이티브 브릿지는 유지(이중 지원).
3. **검증된 이메일 기반 계정 연동** — 같은 사람이 다른 provider로 로그인하면 한 계정으로 수렴.
4. **신규 유저 2단계 가입 폼 완결** — signup 토큰 → 이름·전화·이메일 입력 → `/v2/auth/signup`.

## 3. 통합 트러스트 모델 (아키텍처)

```
브라우저: signIn('kakao'|'naver'|'google')
  → NextAuth 빌트인 OAuth provider가 redirect + code 교환
  → NextAuth jwt/signIn 콜백에서 account.access_token(provider 토큰) 확보
  → 백엔드 POST /api/v2/auth/login { provider, accessToken }
  → 백엔드가 provider userinfo API로 검증 + 우리 access/refresh 발급(또는 signup 토큰)
  → NextAuth JWT에 우리 토큰 저장(기존 refreshAccessToken 로직 재사용)

WebView(카카오): 기존 네이티브 브릿지 → signIn('credentials', { provider:'KAKAO', accessToken }) 유지
```

백엔드가 여전히 토큰 권위이고 NextAuth는 OAuth 춤만 담당한다. 커스텀 `/api/{provider}/callback` 수동 교환 대안보다 코드가 적고 v5 관용적이다.

## 4. 백엔드 설계 (carry-user)

### 4.1 provider 일반화

- `OAuthProvider { KAKAO, DEV }` → **`KAKAO, NAVER, GOOGLE, DEV`**.
- 아웃바운드 포트 `OAuthProfileClient` 일반화: `fetchKakaoProfile(token)` → **`fetchProfile(provider: OAuthProvider, accessToken: String): OAuthProfile`**. `OAuthProfile`에 **`emailVerified: Boolean`** 필드 추가.
- provider별 어댑터 + provider→어댑터 resolver:
  - 기존 `KakaoOAuthClient`(`kapi.kakao.com/v2/user/me`) — 새 인터페이스에 맞춤, `kakao_account.is_email_verified` → `emailVerified`.
  - 신규 `NaverOAuthClient`(`openapi.naver.com/v1/nid/me`; 응답 `response.id`→oauthId, `response.email`).
  - 신규 `GoogleOAuthClient`(`https://openidconnect.googleapis.com/v1/userinfo` 또는 id_token 검증; `sub`→oauthId, `email`, `email_verified`).
  - 각자 `*Properties`(base-url) + `*ClientConfig` + prod base-url 가드(`KakaoBaseUrlGuard` 패턴 준용).
  - `OAuthProfileClient` 구현이 provider로 분기(어댑터 맵), 또는 `OAuthProfileClientResolver`.

### 4.2 요청/유스케이스

- `LoginRequest(kakaoAccessToken)` → **`LoginRequest(provider: String, accessToken: String)`**. (WebView 카카오도 `provider:"KAKAO"` 전달.)
- `AuthUseCase.loginWithKakao` → **`login(provider: OAuthProvider, accessToken: String): LoginResult`**.

### 4.3 이메일 계정 연동 로직

`login(provider, accessToken)`:
1. `profile = oAuthProfileClient.fetchProfile(provider, accessToken)`.
2. `findByOAuthAccount(provider, profile.oauthId)` 존재 → `Registered(issueTokens)`.
3. 미존재 + `profile.emailVerified == true` + `findByEmail(profile.email)` 존재 → **연동**: 그 유저에 `(provider, oauthId)` 신원 추가 → `Registered(issueTokens)`.
4. 그 외(이메일 미검증 or 이메일 유저 없음) → `RegistrationRequired(issueSignupToken(provider, oauthId, email, nickname))`.

⚠️ **연동은 `emailVerified=true`일 때만** 자동 수행 — 미검증 이메일로의 계정 탈취 방지. Google은 항상 `email_verified` 제공, 카카오는 `is_email_verified`, 네이버는 이메일 제공(검증 플래그 없으면 보수적으로 미검증 취급 → 연동 안 함, registration-required).

### 4.4 스키마 (다중 provider 신원)

한 유저가 여러 provider를 가지려면 신원을 별도 테이블로 분리한다.
- 신규 `user_oauth_accounts(id, user_id FK, provider VARCHAR, oauth_id VARCHAR, created_at, UNIQUE(provider, oauth_id))`.
- 마이그레이션: 기존 `user_users.oauth_provider/oauth_id`를 `user_oauth_accounts`로 유저당 1행 이관 후, `user_users`의 두 컬럼 제거(또는 유지-데드; 제거 권장). `user_users`는 canonical `email`·`name`·`phone`·`role` 유지.
- `findByOAuthInfo` → `user_oauth_accounts` 조회 후 유저 로드. `linkOAuthAccount(userId, provider, oauthId)` 추가.

### 4.5 가입 완결

기존 `POST /api/v2/auth/signup(signupToken, name, phone, email)` + `completeSignup` 재사용. signup 토큰의 `provider` 클레임으로 신원 생성 시 `user_oauth_accounts`에 1행 기록. `role=CUSTOMER` 기본 유지.

## 5. 프론트 설계 (customer-web)

### 5.1 NextAuth provider

`auth.ts`: 기존 Credentials(dev-login·WebView 카카오 네이티브) **유지** + 빌트인 `Kakao`·`Naver`·`Google` OAuth provider **추가**.
- 각 OAuth provider의 `jwt`/`signIn` 콜백에서 `account.provider` + `account.access_token`을 백엔드 `/v2/auth/login {provider, accessToken}`으로 교환 → 우리 access/refresh를 NextAuth JWT에 저장. `RegistrationRequired`면 signup 토큰을 세션에 담아 가입 폼으로 유도.
- 기존 `refreshAccessToken`·`isJwtExpired`·session 콜백 로직 재사용.
- ⚠️ NextAuth 빌트인 provider는 자체 세션/account를 관리하려 하므로, jwt 콜백에서 우리 백엔드 토큰으로 override하는 federated-login 패턴을 명시.

### 5.2 로그인 UI

`login/[[...redirect]]/page.tsx`: 카카오 버튼 + **네이버·구글 버튼** 추가. 브라우저 = `signIn('kakao'|'naver'|'google')`, WebView = 카카오 네이티브 브릿지(`KakaoLoginButton` 기존 분기 유지). 죽은 `/api/kakao` 라우트 제거(NextAuth가 `/api/auth/callback/<provider>` 처리).

### 5.3 가입 완결 폼

signup 토큰 보유 시 진입하는 2단계 가입 페이지 신설: 이름·전화·이메일(prefill 있으면 채움) 입력 → `POST /v2/auth/signup {signupToken, name, phone, email}` → 토큰 발급 → 로그인 완료. (현재 미구현 TODO를 완결.)

## 6. 설정/준비물 (사용자 제공)

각 provider 개발자 콘솔에서 **OAuth 앱 등록** → client id/secret + redirect URI `{BASE_URL}/api/auth/callback/{provider}`.
- 프론트 env: `AUTH_KAKAO_ID/SECRET`, `AUTH_NAVER_ID/SECRET`, `AUTH_GOOGLE_ID/SECRET`(+ 기존 `AUTH_SECRET`). 시크릿은 env로(채팅 금지).
- 백엔드 env/yml: `naver.api.base-url`(`https://openapi.naver.com`), `google.api.base-url`(userinfo/JWKS). 기존 `kakao.api.base-url` 유지.
- 실 OAuth 라이브 검증은 키 준비 시(개발용 테스트 앱으로 브라우저 검증 가능).

## 7. 테스트

- **백엔드 단위**: `OAuthProvider` 확장; `login` 3분기(기존 신원/이메일 연동/신규 registration-required); Naver·Google 클라이언트 프로필 매핑·401→invalid(모킹); 이메일 미검증 시 연동 거부; `linkOAuthAccount` 왕복; signup 완결이 `user_oauth_accounts`에 기록. **통합**: 두 provider(예: 카카오+구글 동일 검증 이메일)가 한 유저로 수렴; 마이그레이션(기존 유저 신원 이관) 검증. JUnit XML 기준.
- **프론트 단위**: provider별 signIn→백엔드 교환 콜백(MSW), registration-required→가입 폼 라우팅, 로그인 버튼 3종 렌더, 가입 폼 제출. Vitest+MSW.
- **e2e/수동**: 실 OAuth 리다이렉트는 키 필요(개발 테스트 앱), 스펙·discovery 확인 + 수동 실행.

## 8. 구현 순서

백엔드(4장: provider 일반화 → 스키마·연동 → 요청/유스케이스) 먼저 완결·머지 → 프론트(5장: NextAuth provider → 버튼 → 가입 폼). 스펙은 두 저장소를 함께 담되 구현 계획에서 백엔드 우선 청크로 분리.

## 9. 의도적 제외

- carrier/coordinator 소셜 로그인(dev-login 유지).
- 계정 연동 해제(unlink) UI/API.
- 실 OAuth 라이브 운영(라이브 키·검수 — 개발 검증 후 별도).
- 네이티브 앱의 네이버/구글 SDK 통합(브라우저 웹 OAuth로 대체하므로 불요).
