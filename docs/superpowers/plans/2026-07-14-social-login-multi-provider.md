# 소셜 로그인 다중 provider(카카오·네이버·구글) — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오 단일 소셜 로그인을 카카오·네이버·구글 3사 웹 OAuth로 확장하고, 검증된 이메일 기반 계정 연동과 신규 가입 폼을 완결한다.

**Architecture:** 백엔드(carry-user)가 provider 토큰을 서버 검증하고 우리 토큰을 발급하는 트러스트 모델 유지. `OAuthProfileClient`를 provider 일반화(Resolver + Naver/Google 클라이언트), 신원을 `user_oauth_accounts`로 분리해 다중 provider 지원, `email_verified` provenance로 안전 연동. 프론트(customer-web)는 NextAuth 빌트인 OAuth provider로 브라우저 로그인 → jwt 콜백에서 백엔드 교환. 백엔드 우선.

**Tech Stack:** Kotlin/Spring(carry-user 헥사고날), Flyway/JPA(PostgreSQL), auth0 JWT(HS256), carry-security. 프론트 Next.js 14 App Router, NextAuth v5(@auth/core 0.34.2), Vitest+MSW. 테스트는 mockk(백엔드 단위).

**Spec:** `docs/superpowers/specs/2026-07-14-social-login-multi-provider-design.md`

**공통 규칙:**
- 백엔드 빌드 JDK 21(`gradle.properties` `org.gradle.java.home` 로컬, 미커밋). ⚠️ Gradle BUILD SUCCESSFUL은 0매칭에도 뜸 — `build/test-results/test/*.xml`의 tests/failures로 검증.
- 커밋: conventional prefix(영어) + 한국어 본문 + `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. PowerShell 커밋은 `git commit -F`.
- 백엔드 브랜치 `feat/social-login-multi-provider`(스펙 커밋됨), base develop. 프론트는 carry-app에서 별도 브랜치.
- 신규 Flyway 마이그레이션은 **V30**(글로벌 최고 V29).
- ⚠️ 코드 사실: `OAuthInfo(provider, id)`(필드명 `id`), `OAuthProfile(oauthId, email, nickname)`, `UserPersistencePort.findByOAuthInfo(OAuthInfo)`, `AuthService.loginWithKakao`/`loginOrRegister(provider, oauthId, email, name, phone)`/`completeSignup(signupToken, name, phone, email)`. 테스트는 mockk.

---

## Chunk 1: 백엔드 — 신원 모델 + provider 일반화 + 연동

### Task 1: OAuthProvider 확장 + OAuthProfile.emailVerified

**Files:**
- Modify: `carry-user/src/main/kotlin/com/carry/user/domain/vo/OAuthInfo.kt`
- Modify: `carry-user/src/main/kotlin/com/carry/user/application/port/outbound/OAuthProfileClient.kt`
- Modify: `carry-user/src/main/kotlin/com/carry/user/adapter/outbound/auth/KakaoOAuthClient.kt` (is_email_verified 매핑)
- Test: `carry-user/src/test/kotlin/com/carry/user/domain/vo/OAuthInfoTest.kt`

- [ ] **Step 1: OAuthProvider에 NAVER/GOOGLE 추가**

`OAuthInfo.kt`의 enum:
```kotlin
enum class OAuthProvider {
    KAKAO,
    NAVER,
    GOOGLE,
    /** 비프로덕션 dev-login 전용 합성 신원. prod에서는 발급 경로가 봉인된다. */
    DEV,
}
```

- [ ] **Step 2: OAuthProfile에 emailVerified 추가 (기본값 false)**

`OAuthProfileClient.kt`:
```kotlin
data class OAuthProfile(
    val oauthId: String,
    val email: String?,
    val nickname: String?,
    val emailVerified: Boolean = false,   // 기본값 — 기존 3-arg 생성부(AuthServiceTest 등)가 그대로 컴파일
)
```
(인터페이스는 Task 3에서 일반화 — 이 태스크는 데이터 형태만. **기본값 false가 핵심**: `AuthServiceTest`·`AuthServiceRotationIntegrationTest`의 `OAuthProfile(oauthId, email, nickname)` 3-arg 호출을 깨지 않는다.)

- [ ] **Step 3: 컴파일 확인**

⚠️ `OAuthProfile` 생성자 변경으로 `KakaoOAuthClient`가 깨진다. 같은 커밋에서 `KakaoOAuthClient.fetchKakaoProfile`의 `OAuthProfile(...)` 호출에 `emailVerified = response.kakaoAccount?.isEmailVerified ?: false` 추가(응답 DTO `KakaoAccount`에 `@JsonProperty("is_email_verified") val isEmailVerified: Boolean? = null` 필드 추가). 이것만으로 컴파일 유지.

Run: `./gradlew :carry-user:compileKotlin` → 성공.

- [ ] **Step 4: OAuthInfoTest 유지 확인**

`OAuthInfoTest`는 provider enum 값에 무관(KAKAO 사용) — 무변경. Run: `./gradlew :carry-user:test --tests "*OAuthInfoTest"` → XML tests>0 failures=0.

- [ ] **Step 5: Commit**

```
git add carry-user/src/main/kotlin/com/carry/user/domain/vo/OAuthInfo.kt carry-user/src/main/kotlin/com/carry/user/application/port/outbound/OAuthProfileClient.kt carry-user/src/main/kotlin/com/carry/user/adapter/outbound/auth/KakaoOAuthClient.kt
git commit -m "feat: OAuthProvider에 NAVER/GOOGLE + OAuthProfile.emailVerified"
```

### Task 2: 신원 모델 분리 — User 애그리거트 + V30 스키마 + 영속성

가장 큰 변경. `User`에서 `oauthInfo`를 제거하고 `email_verified`를 추가, 신원은 `user_oauth_accounts`로 분리한다. **커밋 하나로 도메인·엔티티·포트·어댑터·마이그레이션·깨지는 호출부(AuthService·devLogin)를 함께 정리**해 컴파일을 유지한다.

**Files:**
- Modify: `carry-user/.../domain/model/User.kt`
- Modify: `carry-user/.../adapter/outbound/persistence/entity/UserJpaEntity.kt`
- Create: `carry-user/.../adapter/outbound/persistence/entity/UserOAuthAccountJpaEntity.kt`
- Create: `carry-user/.../adapter/outbound/persistence/repository/UserOAuthAccountJpaRepository.kt`
- Modify: `carry-user/.../adapter/outbound/persistence/repository/UserJpaRepository.kt`
- Modify: `carry-user/.../adapter/outbound/persistence/UserPersistenceAdapter.kt`
- Modify: `carry-user/.../application/port/outbound/UserPersistencePort.kt`
- Modify: `carry-user/.../application/service/AuthService.kt` (devLogin·loginOrRegister·loginWithKakao·**completeSignup** 호출부 정리)
- Modify: `carry-user/.../application/port/inbound/AuthUseCase.kt` (loginOrRegister emailVerified)
- Create: `carry-user/src/main/resources/db/migration/V30__user_oauth_accounts_and_email_verified.sql`
- Modify(픽스처): `carry-app/src/test/.../TestFixtures.kt`(insertCustomer/insertCarrier — user_oauth_accounts로), `carry-loadtest/.../seed.sql`(INSERT user_users → user_oauth_accounts + email_verified)
- Test: `carry-user/.../domain/model/UserTest.kt`, `AuthServiceTest.kt`, **`UserCommandServiceTest.kt`**, **`UserQueryServiceTest.kt`**, **`integration/AuthServiceRotationIntegrationTest.kt`**

⚠️ **호출부 추적**: `User.create`/`reconstitute`·`findByOAuthInfo` 변경은 위 5개 테스트 + carry-app TestFixtures + carry-loadtest seed를 전부 깬다. 같은 커밋에서 모두 수정한다.

- [ ] **Step 1: User 애그리거트 변경**

`User`에서 `oauthInfo: OAuthInfo` 제거, `emailVerified` 추가:
```kotlin
class User private constructor(
    val id: Long?,
    val email: Email,
    val emailVerified: Boolean,
    private var _name: String,
    private var _phone: Phone,
    val role: UserRole,
    private var _active: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    // ... getters/updateProfile/deactivate 동일 ...
    companion object {
        fun create(email: Email, emailVerified: Boolean, name: String, phone: Phone, role: UserRole = UserRole.CUSTOMER): User {
            requireInput(name.isNotBlank()) { "이름은 비어있을 수 없습니다" }
            return User(null, email, emailVerified, name, phone, role, true, null, null)
        }
        fun reconstitute(id: Long, email: Email, emailVerified: Boolean, name: String, phone: Phone, role: UserRole, isActive: Boolean, createdAt: Instant, updatedAt: Instant): User =
            User(id, email, emailVerified, name, phone, role, isActive, createdAt, updatedAt)
    }
}
```
(`OAuthInfo` import 제거.)

- [ ] **Step 2: V30 마이그레이션**

```sql
-- 다중 provider 신원 분리 + 이메일 검증 provenance
ALTER TABLE user_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE user_oauth_accounts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES user_users(id),
    provider    VARCHAR(20) NOT NULL,
    oauth_id    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oauth_account UNIQUE (provider, oauth_id)
);
CREATE INDEX idx_oauth_account_user ON user_oauth_accounts(user_id);

-- 기존 user_users 신원을 이관(dev 데이터만; 학습 프로젝트)
INSERT INTO user_oauth_accounts (user_id, provider, oauth_id)
SELECT id, oauth_provider, oauth_id FROM user_users;

ALTER TABLE user_users DROP COLUMN oauth_provider;
ALTER TABLE user_users DROP COLUMN oauth_id;
```

- [ ] **Step 3: JPA 엔티티·리포지토리**

`UserJpaEntity`: `oauthProvider`/`oauthId` 컬럼·`toDomain`/`fromDomain` 매핑 제거, `email_verified` 추가. `toDomain`→`User.reconstitute(..., emailVerified, ...)`, `fromDomain`→`User`에서 email_verified 읽음. `updateFrom`은 name/phone/isActive 유지(email_verified는 불변으로 두거나 갱신 필요시 추가 — 연동 시 유저 이메일 검증 상태가 바뀔 일 없으므로 불변).

신규 `UserOAuthAccountJpaEntity(@Table("user_oauth_accounts"))`: `userId: Long`, `provider: OAuthProvider @Enumerated(STRING)`, `oauthId: String`, BaseEntity. `UserOAuthAccountJpaRepository: JpaRepository<..., Long>` — `findByProviderAndOauthId(provider, oauthId): Optional<UserOAuthAccountJpaEntity>`.

`UserJpaRepository`: `findByOauthProviderAndOauthId` 제거(신원 조회는 accounts 리포지토리로 이동).

- [ ] **Step 4: 포트·어댑터**

`UserPersistencePort`:
```kotlin
interface UserPersistencePort {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByEmail(email: Email): User?
    fun findByOAuthInfo(oauthInfo: OAuthInfo): User?   // accounts 조인 → 유저 로드
    fun existsByEmail(email: Email): Boolean
    fun linkOAuthAccount(userId: Long, oauthInfo: OAuthInfo)
}
```
`UserPersistenceAdapter`: `findByOAuthInfo` → `userOAuthAccountJpaRepository.findByProviderAndOauthId(oauthInfo.provider, oauthInfo.id).map { userJpaRepository.findById(it.userId) }...toDomain()`. `linkOAuthAccount` → accounts 엔티티 저장. `save`의 insert 경로는 이제 oauth를 쓰지 않음(신원은 별도 link).

- [ ] **Step 5: AuthService 호출부 정리(컴파일 유지)**

`User.create`/`loginOrRegister`/`devLogin`이 `oauthInfo`를 넘기던 부분 수정:
- `AuthUseCase.loginOrRegister`·`AuthService.loginOrRegister` 시그니처에 `emailVerified: Boolean` 추가. 본문: `findByOAuthInfo(OAuthInfo(provider, oauthId))` → 있으면 반환; 없으면 `User.create(Email(email), emailVerified, name, Phone(phone))` save 후 `linkOAuthAccount(saved.id!!, OAuthInfo(provider, oauthId))`. (연동 로직 본체는 Task 4 — 여기선 컴파일 유지 최소 수정.)
- `devLogin`: `User.create(Email(...), emailVerified=false, name, Phone(...), role)` save 후 `linkOAuthAccount(saved.id!!, OAuthInfo(DEV, "dev:$slug"))`. 조회는 `findByOAuthInfo(OAuthInfo(DEV, "dev:$slug"))` 유지.
- **`completeSignup`(loginOrRegister 호출부)**: 새 `emailVerified` 인자를 넘겨야 함 — Task 2에선 `emailVerified = false`로 고정 전달(전체 로직은 Task 4). 컴파일 유지 목적.
- `loginWithKakao`는 이 태스크에서 **4-arg signup 토큰 호출 그대로**(emailVerified 전파는 Task 4). 즉 `loginWithKakao` 시그니처 무변경.

- [ ] **Step 6: 테스트 갱신**

`UserTest`: `User.create`/`reconstitute`가 oauthInfo 대신 emailVerified를 받도록 갱신. `AuthServiceTest`·`UserCommandServiceTest`·`UserQueryServiceTest`·`AuthServiceRotationIntegrationTest`: 이들이 만드는 `User.reconstitute(..., oauthInfo=...)` 헬퍼를 `emailVerified` 시그니처로 전부 갱신, `findByOAuthInfo`/`save`/`linkOAuthAccount` mockk 스텁 갱신(`linkOAuthAccount`는 `every {...} just Runs`). carry-app `TestFixtures`·carry-loadtest `seed.sql`은 `user_users`에서 oauth 컬럼을 빼고 `email_verified` 넣은 뒤 `user_oauth_accounts`에 신원 행 삽입.

- [ ] **Step 7: 검증 + Commit**

Run: `./gradlew :carry-user:test` → XML failures=0(HexagonalArchitectureTest 포함).
```
git add carry-user/ && git commit -m "refactor!: 신원을 user_oauth_accounts로 분리 + email_verified — 다중 provider 기반, V30"
```

### Task 3: OAuthProfileClient 일반화 + Naver/Google 클라이언트

**Files:**
- Modify: `carry-user/.../application/port/outbound/OAuthProfileClient.kt`
- Create: `carry-user/.../adapter/outbound/auth/OAuthProfileClientResolver.kt`
- Modify: `carry-user/.../adapter/outbound/auth/KakaoOAuthClient.kt` (+ Config)
- Create: `carry-user/.../adapter/outbound/auth/NaverOAuthClient.kt` + `NaverProperties.kt` + `NaverClientConfig.kt` + `NaverBaseUrlGuard.kt`
- Create: `carry-user/.../adapter/outbound/auth/GoogleOAuthClient.kt` + `GoogleProperties.kt` + `GoogleClientConfig.kt` + `GoogleBaseUrlGuard.kt`
- Test: `carry-user/.../adapter/outbound/auth/NaverOAuthClientTest.kt`, `GoogleOAuthClientTest.kt`, `OAuthProfileClientResolverTest.kt`

⚠️ **컴파일 유지 전략**: 이 태스크는 인터페이스에 `supports()`/`fetchProfile()`을 **추가**만 하고 기존 `fetchKakaoProfile`은 **남긴다**(AuthService·KakaoOAuthClientTest가 아직 그걸 씀). 실제 스왑·`fetchKakaoProfile` 제거·`login` 리네임은 Task 4에서 한꺼번에(원자적).

- [ ] **Step 1: 포트에 메서드 추가(기존 유지)**

```kotlin
interface OAuthProfileClient {
    fun supports(): OAuthProvider
    fun fetchProfile(accessToken: String): OAuthProfile
    fun fetchKakaoProfile(accessToken: String): OAuthProfile   // Task 4에서 제거 — 그때까지 유지
}
```
(provider별 빈이 자신을 `supports()`로 선언. Resolver가 provider→client 맵.)

- [ ] **Step 2: Resolver**

```kotlin
@Component
class OAuthProfileClientResolver(clients: List<OAuthProfileClient>) {
    private val byProvider = clients.associateBy { it.supports() }
    fun resolve(provider: OAuthProvider): OAuthProfileClient =
        byProvider[provider] ?: throw BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 provider: $provider")
}
```

- [ ] **Step 3: Kakao 어댑터에 새 메서드 추가(기존 유지)**

`KakaoOAuthClient`: `override fun supports() = OAuthProvider.KAKAO` + `override fun fetchProfile(accessToken)` 추가(기존 `fetchKakaoProfile` 본문을 `fetchProfile`이 호출하거나 동일 구현; `fetchKakaoProfile`은 Task 4까지 유지). `KakaoClientConfig` 빈 타입 `OAuthProfileClient` 그대로(Resolver의 List 주입에 포함).

- [ ] **Step 4: Naver 클라이언트 (TDD)**

`NaverOAuthClientTest`(`KakaoOAuthClientTest`의 `MockRestServiceServer` 패턴 준용): `GET /v1/nid/me` 200 → `{resultcode:"00", response:{id, email, name}}` → `OAuthProfile(oauthId=response.id, email, emailVerified=false, nickname=name)`. 네이버는 검증 플래그 미제공 → **emailVerified=false 고정**. 401 → `OAuthTokenInvalidException`, 기타 → `OAuthProviderUnavailableException`.
구현 `NaverOAuthClient`(base-url `https://openapi.naver.com`), `NaverProperties(prefix="naver.api")`, `NaverClientConfig`(@Bean), `NaverBaseUrlGuard`(prod 가드, `KakaoBaseUrlGuard` 준용).

- [ ] **Step 5: Google 클라이언트 (TDD)**

`GoogleOAuthClientTest`: `GET /v1/userinfo`(base `https://openidconnect.googleapis.com`) 200 → `{sub, email, email_verified, name}` → `OAuthProfile(oauthId=sub, email, emailVerified=email_verified, nickname=name)`. 401/기타 동일. **id_token 검증 미사용**(액세스 토큰 userinfo).
구현 `GoogleOAuthClient`·`GoogleProperties(prefix="google.api")`·`GoogleClientConfig`·`GoogleBaseUrlGuard`.

- [ ] **Step 6: Resolver 테스트 + 전체**

`OAuthProfileClientResolverTest`: 3 provider resolve + 미지원 예외. Run: `./gradlew :carry-user:test` → failures=0.

- [ ] **Step 7: Commit**

```
git add carry-user/ && git commit -m "feat: OAuthProfileClient 일반화 + Naver/Google 클라이언트·Resolver"
```

### Task 4: AuthService.login 일반화 + 이메일 연동 + 가입 충돌

⚠️ **이 태스크는 원자적**: `login` 리네임 + resolver 스왑 + `fetchKakaoProfile` 제거 + AuthController/DTO + carry-app AuthControllerTest를 **한 커밋**에. 중간 커밋 컴파일 깨짐 방지.

**Files:**
- Modify: `carry-user/.../application/service/AuthService.kt` (생성자 `oAuthProfileClient`→`oAuthProfileClientResolver`, `loginWithKakao`→`login`, completeSignup 연동 로직)
- Modify: `carry-user/.../application/port/inbound/AuthUseCase.kt` (loginWithKakao→login)
- Modify: `carry-user/.../application/port/outbound/OAuthProfileClient.kt` (`fetchKakaoProfile` 제거)
- Modify: `carry-user/.../adapter/outbound/auth/KakaoOAuthClient.kt` (`fetchKakaoProfile` 제거) + `carry-user/.../adapter/outbound/auth/KakaoOAuthClientTest.kt`(→ `fetchProfile` 호출로)
- Modify: `carry-user/.../adapter/inbound/rest/AuthController.kt` + `dto/AuthWebDto.kt` (LoginRequest {provider, accessToken})
- Modify: `carry-app/src/test/.../AuthControllerTest.kt` (mock `login(...)`, body `{provider, accessToken}`)
- Modify: `carry-security/.../jwt/JwtProvider.kt`(createSignupToken/parseSignupToken emailVerified, **기본값 false**) + `SignupClaims.kt` + `carry-security/.../JwtProviderTest.kt`(기본값이라 무변경이면 스킵, 명시 호출 있으면 갱신)
- Modify: `carry-user/.../adapter/outbound/auth/JwtAuthTokenAdapter.kt` + `application/port/outbound/AuthTokenPort.kt` (SignupIdentity.emailVerified, issueSignupToken emailVerified 기본값)
- Modify: `carry-user/.../domain/exception/UserExceptions.kt` (EmailAlreadyExistsException)
- Test: `AuthServiceTest.kt`, `JwtAuthTokenAdapterTest.kt`

- [ ] **Step 1: signup 토큰에 emailVerified 클레임 (carry-security)**

`JwtProvider.createSignupToken(provider, oauthId, email, nickname, emailVerified: Boolean = false)` — **기본값 false**(기존 4-arg 호출·`JwtProviderTest`가 안 깨짐) — `withClaim("email_verified", emailVerified)`. `parseSignupToken` → `SignupClaims(..., emailVerified = decoded.getClaim("email_verified").asBoolean() ?: false)`. `SignupClaims`에 `emailVerified: Boolean` 추가. companion에 `CLAIM_EMAIL_VERIFIED="email_verified"`.

- [ ] **Step 2: 포트 전파**

`AuthTokenPort.SignupIdentity`에 `emailVerified: Boolean`. `issueSignupToken(provider, oauthId, email, nickname, emailVerified: Boolean = false)`(기본값). `JwtAuthTokenAdapter`: `issueSignupToken`·`parseSignupToken`이 emailVerified 전달.

- [ ] **Step 3: 실패 테스트 (AuthServiceTest 3분기)**

```kotlin
@Test fun `기존 신원이면 즉시 토큰`()  // findByOAuthInfo hit
@Test fun `미존재+양측 검증 이메일 매치면 연동 후 토큰`()  // profile.emailVerified && user.emailVerified && findByEmail hit → linkOAuthAccount + issueTokens
@Test fun `이메일 미검증이면 연동 안 하고 RegistrationRequired`()  // profile.emailVerified=false → signup token
@Test fun `검증돼도 기존 유저 email_verified=false면 연동 안 함`()  // 탈취 방지
@Test fun `이메일 null이면 연동 안 함`()
@Test fun `completeSignup 이메일 충돌이면 EmailAlreadyExists 409`()
```

- [ ] **Step 4: login 구현**

`AuthUseCase.loginWithKakao` → `login(provider: OAuthProvider, accessToken: String): LoginResult`. `AuthService` — ⚠️ **`@Transactional(readOnly=true)` 오버라이드 삭제**(연동이 write. 클래스 기본 `@Transactional` 상속):
```kotlin
override fun login(provider: OAuthProvider, accessToken: String): LoginResult {
    val profile = oAuthProfileClientResolver.resolve(provider).fetchProfile(accessToken)
    userPersistencePort.findByOAuthInfo(OAuthInfo(provider, profile.oauthId))?.let {
        if (!it.isActive) throw InactiveUserException()
        return LoginResult.Registered(issueTokens(it))
    }
    // 검증된 이메일 연동: 양측 모두 검증 필요(탈취 방지)
    if (profile.emailVerified && profile.email != null) {
        userPersistencePort.findByEmail(Email(profile.email))?.let { existing ->
            if (existing.emailVerified) {
                if (!existing.isActive) throw InactiveUserException()
                userPersistencePort.linkOAuthAccount(existing.id!!, OAuthInfo(provider, profile.oauthId))
                return LoginResult.Registered(issueTokens(existing))
            }
        }
    }
    val signupToken = authTokenPort.issueSignupToken(provider, profile.oauthId, profile.email, profile.nickname, profile.emailVerified)
    return LoginResult.RegistrationRequired(signupToken, Prefill(profile.email, profile.nickname))
}
```
⚠️ 연동은 write라 `@Transactional(readOnly=true)` 제거(클래스 기본 @Transactional 상속 — readOnly 오버라이드 삭제).

`completeSignup`: `parseSignupToken` → `identity`. `effectiveEmail`·`effectiveEmailVerified` 결정 — `identity.emailVerified && identity.email != null`이면 폼 email 무시하고 `identity.email` 고정 + verified=true; 아니면 폼 email + verified=false. ⚠️ **충돌 가드는 두 분기 모두**(검증 이메일이라도 기존에 email_verified=false 유저가 그 주소를 점유하면 UNIQUE 위반 500이 남): `User.create` 전에 `existsByEmail(effectiveEmail)`가 true면 `EmailAlreadyExistsException()`. → `loginOrRegister(provider, oauthId, effectiveEmail, name, phone, effectiveEmailVerified)`.

`EmailAlreadyExistsException`: `UserExceptions.kt`에 `class EmailAlreadyExistsException : BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다")`.

`AuthService` 생성자: `oAuthProfileClient` → `oAuthProfileClientResolver: OAuthProfileClientResolver`로 교체.

- [ ] **Step 5: 검증 + Commit**

Run: `./gradlew :carry-user:test :carry-security:test` → failures=0.
```
git add carry-user/ carry-security/ && git commit -m "feat: login provider 일반화 + 검증된 이메일 연동(탈취 방지) + 가입 이메일 충돌 409"
```

### Task 5: application.yml provider base-url

(AuthController/LoginRequest 계약 변경은 Task 4에서 원자적으로 완료됨. 이 태스크는 설정만.)

**Files:**
- Modify: `carry-platform/carry-app/src/main/resources/application.yml`

- [ ] **Step 1: application.yml**

```yaml
naver:
  api:
    base-url: ${NAVER_API_BASE_URL:https://openapi.naver.com}
google:
  api:
    base-url: ${GOOGLE_API_BASE_URL:https://openidconnect.googleapis.com}
```
(기존 `kakao.api.base-url` 유지. Naver/Google `*BaseUrlGuard`가 prod에서 실 URL을 강제 — Task 3.)

- [ ] **Step 2: 검증 + Commit**

Run: `./gradlew :carry-app:compileKotlin` 성공(설정만이라 컴파일 영향 없음, yml 로딩 확인).
```
git add carry-app/src/main/resources/application.yml && git commit -m "feat: naver/google api base-url 설정"
```

### Task 6: 백엔드 전체 검증 + PR

- [ ] **Step 1: 전체 빌드·테스트**

Run: `./gradlew :carry-user:test :carry-security:test :carry-app:test` → 전 모듈 XML failures=0. ⚠️ **`:carry-app:test` 필수**(AuthControllerTest·TestFixtures·V30 마이그레이션이 실제 부팅·DB에서 검증되는 지점 — Testcontainers). Docker 미기동이면 BLOCKED 보고.

- [ ] **Step 2: PR**

```
git push -u origin feat/social-login-multi-provider
gh pr create --base develop --title "feat: 소셜 로그인 다중 provider(카카오·네이버·구글) — 웹 OAuth·이메일 연동" --body-file <파일>
```
PR 본문: 스펙 링크, 신원 모델 변경(V30·user_oauth_accounts), 연동 탈취 방지 규칙, provider 일반화. `Closes #<이슈>`. 머지는 `gh pr merge --merge --delete-branch`.

---

## Chunk 2: 프론트 — NextAuth provider + 버튼 + 가입 폼

> carry-app(`C:\Users\Eisen\Desktop\Labs\[projects] carry\carry-app`), customer-web. 백엔드 PR 머지 후 시작. 별도 브랜치 `feat/social-login-web-oauth`. Vitest+MSW, `tsc --noEmit`.

### Task 7: env 스키마 + NextAuth provider 배선

**Files:**
- Modify: `apps/customer-web/src/shared/config/env.ts`
- Modify: `apps/customer-web/src/features/auth/api/auth.ts` (providers + jwt/session 콜백 + **module augmentation**)
- Modify: `apps/customer-web/src/features/auth/api/token.ts` (+ test)
- Modify: `apps/customer-web/src/test/mocks/handlers.ts` (로그인 기본 핸들러 분기)
- Test: `apps/customer-web/src/features/auth/api/token.test.ts`

- [ ] **Step 1: env server 스키마**

`env.ts` `server`에 `AUTH_KAKAO_ID`, `AUTH_KAKAO_SECRET`, `AUTH_NAVER_ID`, `AUTH_NAVER_SECRET`, `AUTH_GOOGLE_ID`, `AUTH_GOOGLE_SECRET`(전부 string). `NEXT_PUBLIC_KAKAO_REST_API_KEY`/`NEXT_PUBLIC_KAKAO_REDIRECT_URL`은 Task 8에서 제거하므로 여기선 유지. (기존 `NEXT_PUBLIC_NAVER_ID`는 **네이버 지도** ncpClientId라 무관 — 건드리지 않음.)

- [ ] **Step 2: token.ts 계약 + 공유 MSW 핸들러**

`loginWithKakao`(또는 신설 `loginWithProvider`)가 `POST /api/v2/auth/login`에 `{ provider, accessToken }` 전송. 기존 `{ kakaoAccessToken }` 제거. WebView 카카오도 `provider:'KAKAO'` 전달. ⚠️ **공유 핸들러 갱신 필수**: `src/test/mocks/handlers.ts`의 로그인 기본 핸들러가 `body.kakaoAccessToken`로 분기 중 → `body.accessToken`/`body.provider`로 변경(안 바꾸면 REGISTERED 테스트가 400). `token.test.ts` 요청 본문 단언 갱신.

- [ ] **Step 3: NextAuth 빌트인 provider + module augmentation + 게이트 안전**

`auth.ts`: 기존 Credentials 유지 + `Kakao`·`Naver`·`Google`(`@auth/core/providers/{kakao,naver,google}`) 추가(clientId/secret은 NextAuth v5가 `AUTH_*_ID/SECRET` env를 자동 판독). **`declare module 'next-auth'`의 `Session`·`JWT`에 `signupToken?: string` 추가**(안 하면 `token.signupToken`/`session.signupToken` 접근이 tsc 실패).
`jwt` 콜백:
```ts
async jwt({ token, user, account }) {
  if (account && account.provider !== 'credentials') {
    const res = await exchangeOAuth(account.provider, account.access_token!);
    if (res.status === 'REGISTERED') { token.accessToken = res.accessToken; token.refreshToken = res.refreshToken; token.signupToken = undefined; }
    else { token.signupToken = res.signupToken; token.accessToken = ''; }
    return token;
  }
  if (user) { token.accessToken = (user as any).accessToken; token.refreshToken = (user as any).refreshToken; }
  // ⚠️ 가입 대기 세션(signupToken 있고 accessToken 빈 문자열) 단락 — 만료검사 전에 반환.
  // isJwtExpired('')는 decodeJwt('')에서 throw하므로 이 가드가 없으면 매 요청 예외.
  if (token.signupToken && !token.accessToken) return token;
  if (token.accessToken && isJwtExpired(token.accessToken as string)) { /* refreshAccessToken 기존 로직 */ }
  return token;
}
```
`exchangeOAuth`(token.ts): `POST /api/v2/auth/login {provider, accessToken}` → `LoginResponse`. session 콜백에 `session.signupToken = token.signupToken` 노출(게이트용).

- [ ] **Step 4: 검증 + Commit**

Run: `pnpm --filter customer-web test token` → PASS. `tsc --noEmit` clean(module augmentation 확인).
```
git add apps/customer-web/src/features/auth/ apps/customer-web/src/shared/config/env.ts apps/customer-web/src/test/mocks/handlers.ts
git commit -m "feat: NextAuth 카카오·네이버·구글 OAuth provider + 백엔드 교환·가입게이트 세션, login 계약 {provider,accessToken}"
```

### Task 8: 로그인 버튼 3종 + 죽은 Kakao 플러밍 제거

**Files:**
- Modify: `apps/customer-web/src/app/(fullscreen)/login/[[...redirect]]/page.tsx`
- Create: `apps/customer-web/src/features/auth/ui/{NaverLoginButton,GoogleLoginButton}.tsx` (또는 공용 `SocialLoginButton`)
- Modify: `apps/customer-web/src/features/auth/ui/KakaoLoginButton.tsx`
- Delete: `apps/customer-web/src/app/api/kakao/route.ts`
- Modify: `apps/customer-web/src/shared/config/env.ts` (NEXT_PUBLIC_KAKAO_* 제거)
- Test: 버튼 렌더 테스트

- [ ] **Step 1: 버튼**

로그인 페이지: 브라우저에서 카카오·네이버·구글 버튼 각각 `signIn('kakao'|'naver'|'google')`. WebView는 카카오 네이티브 브릿지 유지(`KakaoLoginButton`의 WebView 분기). 공용 `SocialLoginButton({ provider })` 권장. 각 provider 브랜드 색/아이콘은 기존 디자인 시스템 수준.

- [ ] **Step 2: 죽은 플러밍 제거 + WebView 폴백**

`login/page.tsx`의 수동 Kakao authorize URL 구성 + `KakaoLoginButton`의 브라우저 `<Link href={oauthUrl}>` 분기 제거(브라우저는 `signIn('kakao')`). ⚠️ `KakaoLoginButton`의 WebView 브릿지 실패 폴백이 현재 `window.location.href = oauthUrl`인데 `oauthUrl`을 제거하므로 **대체 폴백 지정**: 브릿지 실패 시 `signIn('kakao')`(웹 OAuth로 폴백) 또는 에러 토스트. `/api/kakao/route.ts` 삭제. `env.ts`에서 `NEXT_PUBLIC_KAKAO_REST_API_KEY`/`NEXT_PUBLIC_KAKAO_REDIRECT_URL` 제거(`NEXT_PUBLIC_NAVER_ID`는 지도용이라 유지; 다른 사용처 grep 확인).

- [ ] **Step 3: 검증 + Commit**

Run: `pnpm --filter customer-web test` → green. `tsc --noEmit` clean. `grep NEXT_PUBLIC_KAKAO apps/customer-web/src` → 없음.
```
git add -A apps/customer-web/ && git commit -m "feat: 로그인 카카오·네이버·구글 버튼 + 죽은 웹 Kakao 플러밍 제거"
```

### Task 9: 신규 가입 폼 + 클라이언트 게이트

**Files:**
- Create: `apps/customer-web/src/app/(fullscreen)/signup/page.tsx` + `features/auth/ui/SignupForm.tsx` + `features/auth/api/signup.ts`
- Modify: `apps/customer-web/src/middleware.ts` 또는 게이트 컴포넌트 (signupToken 있고 accessToken 없으면 /signup)
- Test: SignupForm 테스트

- [ ] **Step 1: 게이트**

세션에 `signupToken`은 있고 `accessToken`은 없으면 `/signup`으로 유도(미들웨어 또는 보호 레이아웃). 로그인 완료(accessToken 있음)면 통과. ⚠️ **미들웨어 matcher/제외**: 현재 `middleware.ts`는 `export { auth as middleware }`로 matcher가 없어 전 경로에서 돈다. `/signup` 리다이렉트가 무한루프가 안 되도록 `/signup`·`/api/auth/*`·정적 자산(`_next`, 이미지)을 matcher/조건으로 제외한다.

- [ ] **Step 2: 가입 폼**

`SignupForm`: 이름·전화·이메일 입력. signup 토큰의 검증 이메일 prefill(세션/쿼리)이 있으면 **이메일 필드 고정(비편집)**; 없으면 편집 가능. 제출 → `signup.ts`가 `POST /api/v2/auth/signup {signupToken, name, phone, email}` → 토큰 발급 → `signIn`으로 세션 갱신/로그인 완료. 409(이메일 중복)면 "이미 사용 중인 이메일" 안내.

- [ ] **Step 3: 검증 + Commit**

Run: `pnpm --filter customer-web test SignupForm` → PASS. `tsc --noEmit` clean.
```
git add apps/customer-web/ && git commit -m "feat: 신규 소셜 가입 2단계 폼 + signupToken 게이트(검증 이메일 고정)"
```

### Task 10: 프론트 최종 검증 + PR

- [ ] **Step 1: 전체 검증**

Run: `pnpm --filter customer-web exec tsc --noEmit` clean, `pnpm --filter customer-web test` green, `pnpm --filter customer-web build` 성공(env 더미 필요 시 SKIP_ENV_VALIDATION/더미).

- [ ] **Step 2: PR**

```
git push -u origin feat/social-login-web-oauth
gh pr create --base develop --title "feat: customer-web 소셜 로그인 3사(카카오·네이버·구글) 웹 OAuth + 가입 폼" --body-file <파일>
```
PR 본문: 백엔드 PR 참조, NextAuth provider 배선·버튼·가입 폼·죽은 Kakao 제거. `Closes #<이슈>`.

---

## 참고: 실 OAuth 검증(사용자 준비물)

각 provider 개발자 콘솔에서 OAuth 앱 등록 → client id/secret + redirect URI `{BASE_URL}/api/auth/callback/{provider}`. 프론트 env `AUTH_{KAKAO,NAVER,GOOGLE}_ID/SECRET`(server-only), 백엔드 base-url env. 실 리다이렉트 검증은 키 준비 후(개발용 테스트 앱). 이 계획의 자동 테스트는 mockk(백엔드)·MSW(프론트) 기반이라 키 없이 통과한다.
