# RBAC (JWT role 실반영) + 코디네이터 PAID 취소·환불 트리거

- 작성일: 2026-06-07
- 대상 레포: `carry-platform` (분기: `feature/rbac-coordinator-order-cancel`, base `origin/develop` @ `75b8cd1`)
- 선행: saga 보상 트랜잭션(#72), CDC 파이프라인 복구(#73) 머지 완료

## 1. 배경 / 문제

두 개의 맞물린 갭을 함께 닫는다.

- **갭 A — RBAC 미작동**: `JwtProvider.createAccessToken(userId, role)`는 `role` claim을 발급하지만, `JwtProvider.validateToken`은 subject(userId)만 반환하고 `JwtAuthenticationFilter`는 authority를 `ROLE_USER`로 하드코딩한다. 결과적으로 role claim이 버려지고, `SecurityConfig`에는 role 기반 matcher도 `@EnableMethodSecurity`도 없다. 따라서 코디네이터·어드민 의도의 모든 엔드포인트가 **"로그인만 하면 누구나"** 호출 가능하다.
- **갭 B — 운영 트리거 부재**: PAID 주문을 환불 경로로 보내는 도메인 로직(`OrderCommandService.cancelOrder(orderId, reason, cancelledBy)` → PAID면 `markRefundPending()` + `OrderCancelledEvent` 발행 → 환불·캐스케이드)은 saga로 완성됐다. 그러나 이 3-arg 경로의 유일한 호출자는 `PaymentRetryDeadlineSweeper`(PAYMENT_FAILED 전용)뿐이다. REST 입구는 `OrderController.cancel` → `cancelOrderByCustomer`(고객 전용, PAID 차단)만 존재한다. **코디가 PAID 주문을 취소·환불할 입구가 없다.**

갭 B의 입구는 "아무나 코디 취소를 해서는 안 된다"는 제약 때문에 갭 A(RBAC)와 분리할 수 없어 함께 처리한다.

## 2. 범위

### 포함
- JWT role claim을 실제 authority로 반영(무상태).
- `@EnableMethodSecurity` + 클래스 레벨 `@PreAuthorize`로 기존 코디·어드민 엔드포인트 가드.
- 코디 전용 PAID 취소·환불 트리거 엔드포인트 신설.
- `carry-common`에 `spring-security-core`를 `api`로 추가(@PreAuthorize·AccessDeniedException 전이 노출 — 모든 컨트롤러 모듈이 공유, carry-operation 갭도 함께 해소).
- `GlobalExceptionHandler`에 `AccessDeniedException → 403` 핸들러 추가(아래 §6).

### 제외 (후속/YAGNI)
- 실제 로그인 → 토큰 발급 wiring (현재 토큰은 테스트에서만 생성됨; 운영 OAuth/로그인 컨트롤러는 별도 작업).
- Laundromat owner 모델, 듀얼액터 read, 세밀한 권한 행렬.
- `CancelledBy` enum에 `ADMIN` 추가 (이번 취소 주체는 COORDINATOR 전용).
- carrier 엔드포인트(`/api/v2/dispatches`, `/api/v2/carrier-areas`) 가드.
- SecurityConfig URL path matcher를 통한 중복 가드(@PreAuthorize 단일 메커니즘 채택).

## 3. 결정 사항 (확정)

| # | 결정 | 선택 | 근거 |
|---|------|------|------|
| 1 | PAID 취소 권한 주체 | **COORDINATOR 전용** | 고객 PAID self-cancel 금지 정책 유지. 어드민/`CancelledBy.ADMIN`은 후속. |
| 2 | 엔드포인트 위치 | **신규 `OrderCoordinatorController` (carry-order)** | 주문 책임은 order 모듈. `/api/v2/coordinator/orders` 컨벤션. dispatch 컨트롤러에 섞지 않음. |
| 3 | RBAC 메커니즘 | **`@EnableMethodSecurity` + `@PreAuthorize`** | 엔드포인트 옆에 권한 명시(세밀·선언적). |
| 4 | role 소스 | **JWT role claim (무상태)** | 무상태 JWT 서사와 일관. 매 요청 DB 조회 회피. role 변경 즉시반영 필요성 낮음. |
| 5 | 가드 범위 | **신규 + 기존 코디·어드민 전체** | 기존 갭(누구나 호출)을 함께 닫음. IDOR 감사와 일관된 보안 자세. |

## 4. 설계

### 4.1 `JwtProvider` (carry-security)
- `data class JwtPrincipal(val userId: Long, val role: String)` 도입.
- `validateToken(token): Long?` → `parseToken(token): JwtPrincipal?`로 대체. 단일 검증으로 subject + `role` claim 추출. role claim 부재 시(예: refresh 토큰) 안전 기본값 `"CUSTOMER"`.
- 유일 호출자가 `JwtAuthenticationFilter`뿐이므로 dead code 없이 교체(`validateToken` 제거). (repo 전체 grep으로 다른 호출자 없음 확인.)
- `createAccessToken(userId: Long, role: String)` / `createRefreshToken`은 시그니처 변경 없음.

**role-claim 문자열 계약 (중요)**: `hasRole('COORDINATOR')`는 authority `ROLE_COORDINATOR`를 요구하고, 필터는 `"ROLE_" + role`을 만든다. 따라서 `createAccessToken`에 넘기는 `role` 문자열은 **반드시 `UserRole`의 bare enum 이름**(`"COORDINATOR"` 등)이어야 한다. 현재 `createAccessToken`은 호출자가 없으므로(로그인 wiring은 범위 밖) 이 계약을 코드 타입으로 강제할 수 없다. `role: UserRole` 타입화는 carry-security → carry-user(UserRole 소재) **모듈 의존 역전**을 유발하므로 채택하지 않는다. 대신 **`JwtProviderTest` round-trip이 claim 값이 정확히 `"COORDINATOR"`임을 단언**하여 계약을 잠그고, 향후 로그인 wiring이 `UserRole.name`을 넘기도록 한다(후속 작업의 책임).

### 4.2 `JwtAuthenticationFilter` (carry-security)
- `parseToken` 사용. authority를 `SimpleGrantedAuthority("ROLE_" + principal.role)`로 세팅.
- **principal은 계속 `userId: Long`** → 모든 컨트롤러의 `@AuthenticationPrincipal userId: Long` 시그니처 무변경.

### 4.3 `SecurityConfig` (carry-security)
- 클래스에 `@EnableMethodSecurity` 추가.
- `authorizeHttpRequests`(permitAll: `/api/v2/auth/**`,`/actuator/**`,`/swagger-ui/**`,`/v3/api-docs/**` + `anyRequest().authenticated()`)는 그대로. 인가 결정은 `@PreAuthorize`로 위임.

### 4.4 가드 적용 (클래스 레벨 `@PreAuthorize`)
URL 컨벤션이 깔끔하여 컨트롤러 단위로 역할이 동질적 → 클래스 레벨 1줄로 충분.

| 컨트롤러 | 매핑 | 가드 |
|---|---|---|
| `DispatchCoordinatorController` | `/api/v2/coordinator/dispatches` | `hasRole('COORDINATOR')` |
| `OperationDashboardController` | `/api/v2/admin/dashboard` | `hasRole('ADMIN')` |
| `TermAdminController` | `/api/v2/admin/terms` | `hasRole('ADMIN')` |
| `OrderCoordinatorController` (신규) | `/api/v2/coordinator/orders` | `hasRole('COORDINATOR')` |

> `@PreAuthorize` 애너테이션은 `spring-security-core`에서 제공. carry-order·carry-dispatch에는 이미 의존하나 **`carry-operation`에는 spring-security 의존이 전무**했다. 모듈별로 개별 추가하는 대신 **`carry-common`에 `api`로 `spring-security-core`를 추가**해 전이 노출한다(GlobalExceptionHandler의 `AccessDeniedException`도 같은 모듈에서 필요하므로 한 곳에 모음). `@EnableMethodSecurity`는 `spring-security-config`(carry-security)에 위치.

### 4.5 신규 엔드포인트 `OrderCoordinatorController` (carry-order)
```
@RestController
@RequestMapping("/api/v2/coordinator/orders")
@PreAuthorize("hasRole('COORDINATOR')")
class OrderCoordinatorController(private val orderCommandUseCase: OrderCommandUseCase)

POST /{orderId}/cancel
  body: CancelOrderRequest(reason: @NotBlank String)   // 기존 DTO 재사용
  → orderCommandUseCase.cancelOrder(orderId, request.reason, "COORDINATOR")
  → 204 No Content
```
- 기존 `cancelOrder` 3-arg 재사용: PAID면 `markRefundPending()` + 환불 캐스케이드, isCancellable면 즉시 취소, 그 외 상태면 도메인 예외(409).
- `CancelledBy.COORDINATOR` 재사용 → enum 변경 없음.

## 5. 동작 흐름 (코디 PAID 취소)
1. 코디 토큰(role=COORDINATOR) Bearer 요청 → 필터가 `ROLE_COORDINATOR` authority 세팅.
2. `@PreAuthorize("hasRole('COORDINATOR')")` 통과 (CUSTOMER 등은 403).
3. `cancelOrder(orderId, reason, "COORDINATOR")` 호출.
4. 주문이 PAID → `markRefundPending()` 저장 + `OrderCancelledEvent(by=COORDINATOR)` 아웃박스 발행.
5. (기존 saga) Payment가 이벤트 수신 → 환불 처리, dispatch/delivery 캐스케이드 취소.

## 6. 에러 처리
| 상황 | 응답 |
|---|---|
| 미인증(토큰 없음/위조) | 401 |
| 인증 O, role≠COORDINATOR | 403 |
| `reason` blank | 400 |
| 주문 없음 | 404 (`OrderNotFoundException`) |
| 취소 불가 상태 | 409 (`ORDER_NOT_CANCELLABLE`) |

> **응답 바디**: 메서드 시큐리티(`@PreAuthorize`) 인가 실패는 컨트롤러 호출 중 `AccessDeniedException`으로 발생하므로 `@RestControllerAdvice`가 먼저 가로챈다. 명시 핸들러가 없으면 catch-all로 **500**이 되는 것이 확인되어, `GlobalExceptionHandler`에 `AccessDeniedException → 403` 핸들러를 추가했다. 따라서 **403은 `ApiResponse` 봉투(`code=FORBIDDEN`)를 따른다**. 반면 **401(미인증)**은 시큐리티 필터 단계(AuthenticationEntryPoint)에서 처리되어 `@RestControllerAdvice`를 거치지 않으므로 Spring 기본 응답(빈 바디)이며 봉투를 따르지 않는다(이번 범위 수용, 통일은 후속). 테스트는 상태 코드 위주로 단언한다. 400/404/409는 기존대로 `GlobalExceptionHandler` 경유 봉투.

## 7. 테스트 (TDD)
현재 `carry-security` 테스트 0개, 코디·어드민 컨트롤러 테스트 0개.

- **carry-security**
  - `JwtProviderTest`: 액세스 토큰 round-trip이 `(userId, role)` 반환 **+ claim 값이 정확히 `"COORDINATOR"`임을 단언**(role-claim 계약 잠금) / role claim 없는 토큰(refresh) → `CUSTOMER` 기본값 / 위조·만료 토큰 → `null`.
  - `JwtAuthenticationFilterTest`: role claim → `ROLE_<role>` authority, principal=userId.
- **carry-app**
  - `OrderCoordinatorControllerTest`: COORDINATOR → 204 + `cancelOrder(orderId, reason, "COORDINATOR")` 호출 검증 / ROLE_CUSTOMER → 403 / 미인증 → 401 / blank reason → 400. (401/403은 **상태 코드만** 단언 — §6 봉투 주의 참조.)
  - 가드 회귀(대표 403): 기존 코디·어드민 컨트롤러에 잘못된 role 접근 시 403 검증(컨트롤러 테스트 신규).

**⚠️ 슬라이스 테스트 method-security 배선 (필수)**: 기존 컨트롤러 슬라이스 테스트는 `@WebMvcTest(... excludeFilters = SecurityConfig, JwtAuthenticationFilter)`로 **SecurityConfig를 제외**한다(401은 @WebMvcTest 기본 시큐리티 자동설정으로 통과). 따라서 그 패턴을 그대로 쓰면 `@EnableMethodSecurity`가 **활성화되지 않아 `@PreAuthorize`가 동작하지 않고 403 테스트가 거짓 통과**한다. 신규/가드 테스트는 method-security를 명시 배선해야 한다:
- `@TestConfiguration @EnableMethodSecurity class MethodSecurityTestConfig`를 `@Import`(SecurityConfig 전체를 끌어와 JwtProvider→필터 빈 체인을 만들지 않도록, 메서드 시큐리티만 켜는 최소 config 권장), `SecurityConfig`/`JwtAuthenticationFilter` exclude는 유지(필터 의존 체인 회피, 인증은 `authentication(...)` 포스트프로세서로 주입).
- 검증: 동일 테스트에서 COORDINATOR=204(통과), CUSTOMER=403(차단) **양방향** 단언으로 애너테이션이 실제로 발동함을 증명.
- 테스트는 `SecurityMockMvcRequestPostProcessors.authentication(...)`으로 authority(`ROLE_COORDINATOR` 등) 주입.

## 8. 검증 흐름 (머지 게이트)
1. 전체 `compileTestKotlin` (cross-module/saga 호출자 정합 검출).
2. 영향 모듈 `:carry-security:test`, `:carry-order:test`.
3. `:carry-app:test` (Testcontainers).
4. 머지 전 풀스택 라이브 e2e: 코디 토큰으로 PAID 주문 취소 → REFUND_PENDING/환불 확인 (CDC 파이프라인 동작 이후 가능).
5. dev 머지 = **사용자 게이트** (자동 dev 배포).

## 9. 영향 / 회귀 리스크
- 컨트롤러 principal 타입(`Long`) 불변 → 기존 컨트롤러 시그니처·테스트 무영향.
- 고객 엔드포인트(`/api/v2/orders` 등)는 role 가드 없음(authenticated만) → 기존 `ROLE_USER` 주입 테스트 그대로 통과.
- 가드 추가 대상 컨트롤러는 기존 테스트가 없어 회귀 표면 작음. 신규 테스트로 가드 실효 검증.
