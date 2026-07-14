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
  - 신규 `GoogleOAuthClient`(`https://openidconnect.googleapis.com/v1/userinfo`, **액세스 토큰 기반**; `sub`→oauthId, `email`, `email_verified`). ⚠️ 와이어 계약이 `{provider, accessToken}`만 전달하므로 id_token 검증 방식은 배제(id_token을 넘기려면 계약 확장 필요 — 하지 않음).
  - 각자 `*Properties`(base-url) + `*ClientConfig` + prod base-url 가드(`KakaoBaseUrlGuard` 패턴 준용).
  - provider→어댑터 분기: `OAuthProfileClientResolver`(provider→client 맵)로 단일화(어댑터 맵/Resolver 중 Resolver로 확정).

### 4.2 요청/유스케이스

- `LoginRequest(kakaoAccessToken)` → **`LoginRequest(provider: String, accessToken: String)`**. (WebView 카카오도 `provider:"KAKAO"` 전달.)
- `AuthUseCase.loginWithKakao` → **`login(provider: OAuthProvider, accessToken: String): LoginResult`**.

### 4.3 이메일 계정 연동 로직

`login(provider, accessToken)`:
1. `profile = oAuthProfileClient.fetchProfile(provider, accessToken)`.
2. `findByOAuthAccount(provider, profile.oauthId)` 존재 → `Registered(issueTokens)`.
3. 미존재 + **양측 이메일이 모두 검증됨** + `profile.email != null` + `findByEmail(profile.email)` 존재(그 유저의 `emailVerified == true`) → **연동**: 그 유저에 `user_oauth_accounts` 1행 추가(2번째 `user_users` 행 아님 → email UNIQUE 위반 없음) → `Registered(issueTokens)`.
4. 그 외 → `RegistrationRequired(issueSignupToken(provider, oauthId, email, emailVerified, nickname))`.

⚠️ **계정 탈취 방지 — 핵심**: 연동 키는 "**검증된 이메일**"만이다. 두 조건이 동시에 성립해야 자동 연동한다:
- (a) 로그인하는 provider의 `profile.emailVerified == true` (들어오는 이메일이 provider-검증됨).
- (b) 매치된 기존 유저의 `emailVerified == true` (저장된 이메일이 provider-검증에서 왔음).

이유: 유저의 canonical 이메일은 §4.4에 `email_verified` 플래그로 provenance를 기록한다. 가입 폼에서 유저가 **임의로 타이핑한 이메일은 `email_verified=false`** 라, 아무도 그 이메일로 자동 연동할 수 없다(공격자가 피해자 이메일을 미리 입력해도 연동 대상이 되지 않음). Google은 항상 `email_verified`, 카카오는 `is_email_verified`(스코프 동의 필요, email null 가능 → null이면 연동 불가), 네이버는 검증 플래그 미제공 → 보수적으로 `emailVerified=false` 취급(→ 항상 registration-required).

### 4.4 스키마 (다중 provider 신원)

한 유저가 여러 provider를 가지려면 신원을 별도 테이블로 분리한다.

**도메인 모델 변경**: 현재 `User` 애그리거트는 단일 `oauthInfo: OAuthInfo`(1:1)를 보유하고 `User.create`/`reconstitute`/`UserJpaEntity`가 이를 전제한다. 다중 provider 연동을 위해 **`User` 애그리거트에서 `oauthInfo`를 제거**하고, OAuth 신원을 별도 관심사로 분리한다 — 신원 조회는 `user_oauth_accounts`(oauthId→userId) → `User` 로드로 이행. `User.create(email, emailVerified, name, phone, role)`로 시그니처 변경(신원은 생성 후 `linkOAuthAccount`로 별도 기록). (컬렉션을 애그리거트에 두는 대안보다 경계가 깔끔하고 조회 경로가 단순.)

**신규 컬럼**: `user_users`에 **`email_verified BOOLEAN NOT NULL DEFAULT false`** 추가(연동 키 provenance — §4.3).

**신규 테이블**: `user_oauth_accounts(id, user_id BIGINT FK→user_users, provider VARCHAR, oauth_id VARCHAR, created_at, UNIQUE(provider, oauth_id), INDEX(user_id))`.

**마이그레이션**(dev 데이터만; 학습 프로젝트): 기존 `user_users.oauth_provider/oauth_id`를 `user_oauth_accounts`로 유저당 1행 이관 → 기존 유저 `email_verified`는 provider가 KAKAO면 보수적으로 `false`로 두거나(재로그인 시 갱신 안 함) 운영 판단(dev라 무해) → `user_users.oauth_provider/oauth_id` 컬럼 제거.

**포트**: `findByOAuthAccount(provider, oauthId): User?`(테이블 조회 후 유저 로드), `linkOAuthAccount(userId, provider, oauthId)`, 기존 `findByEmail` 재사용.

### 4.5 가입 완결

기존 `POST /api/v2/auth/signup(signupToken, name, phone, email)` + `completeSignup` 재사용하되:
- **이메일 출처**: signup 토큰의 `emailVerified` 클레임(§4.3 step4에서 발급 시 포함)이 true면, 유저의 `email_verified=true`로 저장하고 **폼의 이메일 필드를 검증된 prefill 값으로 고정**(프론트 §5.3)해 임의 입력을 막는다. false면 유저 입력을 받되 `email_verified=false`로 저장(→ 연동 대상 아님).
- **이메일 충돌 가드**: `completeSignup`은 `User.create` 전에 `findByEmail(email)`을 확인한다. 이미 존재하면(특히 검증 플래그 없는 네이버는 항상 이 경로) **`EmailAlreadyExistsException`(409)** 으로 명확히 거부 — email UNIQUE 위반의 raw 500을 방지. (미검증 입력 이메일은 자동 연동하지 않는다 — 탈취 방지.)
- 신원은 `user_oauth_accounts`에 1행 기록. `role=CUSTOMER` 기본.

## 5. 프론트 설계 (customer-web)

### 5.1 NextAuth provider

`auth.ts`: 기존 Credentials(dev-login·WebView 카카오 네이티브) **유지** + 빌트인 `Kakao`·`Naver`·`Google` OAuth provider **추가**.
- **jwt 콜백 분기**: `account`는 최초 sign-in에만 존재한다. `account.provider`로 분기 — credentials(기존: `user.accessToken`)와 oauth(신규)를 구분. oauth면 `account.access_token`을 백엔드 `/v2/auth/login {provider, accessToken}`으로 교환.
- 교환 결과가 `Registered`면 우리 access/refresh를 JWT에 저장. `RegistrationRequired`면 **signup 토큰을 JWT에 담고 accessToken은 비운다** — jwt 콜백에서 리다이렉트는 불가하므로, "signupToken은 있고 accessToken은 없는 세션"을 **클라이언트 게이트**(미들웨어/레이아웃)가 감지해 가입 폼(`§5.3`)으로 보낸다.
- ⚠️ WebView 카카오 Credentials 경로의 백엔드 요청도 바뀐다: `token.ts`의 `loginWithKakao`가 현재 `{ kakaoAccessToken }`을 POST → **`{ provider: 'KAKAO', accessToken }`**로 변경(+ `token.test.ts` 갱신). `refreshAccessToken`·`isJwtExpired`·session 콜백 로직은 재사용.

### 5.2 로그인 UI

`login/[[...redirect]]/page.tsx`: 카카오 버튼 + **네이버·구글 버튼** 추가. 브라우저 = `signIn('kakao'|'naver'|'google')`, WebView = 카카오 네이티브 브릿지(`KakaoLoginButton` 기존 분기 유지). 죽은 `/api/kakao` 라우트 제거(NextAuth가 `/api/auth/callback/<provider>` 처리). ⚠️ 브라우저 카카오가 NextAuth로 이행하므로, 이 페이지의 수동 Kakao authorize URL 구성(`NEXT_PUBLIC_KAKAO_REST_API_KEY`·`NEXT_PUBLIC_KAKAO_REDIRECT_URL`)과 `KakaoLoginButton`의 브라우저 `<Link href={oauthUrl}>` 분기는 제거/재작업 대상.

### 5.3 가입 완결 폼

signup 토큰 보유 시(§5.1의 클라이언트 게이트) 진입하는 2단계 가입 페이지 신설: 이름·전화·이메일 입력 → `POST /v2/auth/signup {signupToken, name, phone, email}` → 토큰 발급 → 로그인 완료. (현재 미구현 TODO를 완결.)
- **이메일 필드**: signup 토큰의 검증 이메일 prefill이 있으면 그 값으로 **고정(비편집)** — 임의 이메일 입력에 의한 탈취 벡터(§4.3) 차단. prefill이 없으면(카카오 email null/네이버) 편집 가능하되, 백엔드가 미검증으로 저장하고 충돌 시 409를 반환(§4.5).
- 409(이메일 중복) 응답은 "이미 사용 중인 이메일" 안내로 처리.

## 6. 설정/준비물 (사용자 제공)

각 provider 개발자 콘솔에서 **OAuth 앱 등록** → client id/secret + redirect URI `{BASE_URL}/api/auth/callback/{provider}`.
- 프론트 env: `AUTH_KAKAO_ID/SECRET`, `AUTH_NAVER_ID/SECRET`, `AUTH_GOOGLE_ID/SECRET`(+ 기존 `AUTH_SECRET`) — 전부 **server-only**(NextAuth v5가 자동 판독). `env.ts`(t3-env)의 `server` 스키마에 추가. 브라우저 카카오 이행으로 `NEXT_PUBLIC_KAKAO_REST_API_KEY`·`NEXT_PUBLIC_KAKAO_REDIRECT_URL`는 미사용화(제거 검토). 시크릿은 env로(채팅 금지).
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
- **이메일 선점(squatting) 완화**: `email` 전역 UNIQUE + 미검증 타이핑 이메일 허용의 부산물로, 공격자가 피해자 이메일을 미검증으로 선점해 피해자 가입을 409로 막을 수 있다(탈취는 불가 — §4.3에서 연동 대상 아님, 피해자 가입은 명확한 409). dev 스코프에서 수용하고 의식적으로 제외. 운영화 시 대안: email 전역 UNIQUE 완화(신원은 provider+oauthId만) 또는 미검증 이메일이 주소를 선점하지 않도록 스코핑.
