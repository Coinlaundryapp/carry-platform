# 감사 로그 (Audit Log) 설계 — ROADMAP 4.2

> 작성일: 2026-06-07 · 브랜치 `feature/audit-log` (base `origin/develop`, A-3 머지 후)
> 백로그 출처: `2026-06-07-carry-remaining-backlog.md` P1 #2. RBAC(#75)·인증(#77)·토큰 라이프사이클(#82)과 묶여 "보안 스토리" 완결.

## 1. 문제

민감 운영 작업(코디 수동배차·주문취소·환불·배차거부 페널티)에 대한 **감사 추적이 없다**. 누가(actor)·언제·무엇을·어떤 상태에서 어떤 상태로 바꿨는지 기록이 남지 않아 보안/운영 책임추적성(accountability)이 결여돼 있다.

## 2. 결정 (사용자 확정 2026-06-07)

| # | 결정 | 선택 | 근거 |
|---|------|------|------|
| D1 | 저장 | **전용 `audit_logs` 테이블** (신규 `carry-audit` 모듈) | 조회 용이·컨슈머 불필요·표준 감사 트레일. |
| D2 | 쓰기 | **동기 같은 tx + best-effort** | 액션과 원자(롤백 동반)=일관성. 가용성 우선(별도 fail-closed 게이트 없음). |
| D3 | actor 범위 | **전부 감사, actor=SYSTEM 구분** | 사람=userId, saga=SYSTEM. 완전한 트레일, actor 필드로 구분. |

YAGNI 제외: 감사 조회 API(어드민 콘솔 별도), 보존정책/파티셔닝, 비동기 outbox.

## 3. 핵심 통찰 — actor 컨텍스트는 ambient

actor·role·traceId·IP가 이미 ThreadLocal 기반 홀더에 존재 → **별도 request-context 인프라·컨트롤러 시그니처 변경 불필요**. 어댑터가 호출 시점(같은 요청 스레드)에 수집한다:

| 필드 | 출처 | 부재 시 |
|------|------|---------|
| actor (userId) | `SecurityContextHolder.context.authentication.principal` (Long, JwtAuthenticationFilter가 세팅) | `"SYSTEM"` |
| role | authentication.authorities 첫 `ROLE_*` 스트립 | null |
| traceId | `MDC.get("traceId")` (관측성 작업 기존 자산) | null |
| ip | `RequestContextHolder` → `X-Forwarded-For ?: remoteAddr` | null (saga 스레드) |
| timestamp | 어댑터가 `Instant.now()`로 설정(엔티티 `createdAt`) | (항상 존재) |

- **principal 가드 캐스트**(리뷰 #3): `(principal as? Long)?.toString() ?: "SYSTEM"` — 미인증·익명("anonymousUser" String)·비-Long 모두 SYSTEM으로 degrade(하드 캐스트는 ClassCastException→§6대로 액션 롤백 유발하므로 금지).
- saga 자동 트리거는 요청 스레드/SecurityContext가 없어 자연히 actor=SYSTEM·ip=null로 기록된다(D3). `RequestContextHolder`는 요청 스레드 바인딩이라 Kafka 컨슈머/`@Async` 스레드에 상속되지 않음 → saga ip=null 자동(현 4개 타깃 중 `@Async` 래핑 없음).

## 4. 모듈 — 신규 `carry-audit` (self-contained)

carry-event(순수 포트 모듈) 선례를 참고하되 **단일 모듈**로 포트+도메인+JPA 어댑터+마이그레이션을 담는다. 모듈 과분해(백로그 P3 #13)를 피하는 의도적 단순화 — 도메인 모듈은 **포트만** 사용한다.

```
carry-audit/
  build.gradle.kts
  src/main/kotlin/com/carry/audit/
    port/AuditPort.kt          // record(action, targetType, targetId, before, after)
    port/AuditAction.kt        // enum
    domain/AuditLog.kt         // 순수 도메인 (Spring/JPA 비의존)
    adapter/outbound/persistence/
      AuditLogJpaEntity.kt     // ⚠️ BaseEntity 미상속(append-only 불변 → updated_at 무의미). 자체 @Id + createdAt만
      AuditLogJpaRepository.kt
      AuditPersistenceAdapter.kt   // AuditPort 구현 + ambient 수집 + JSON 직렬화
  src/test/kotlin/com/carry/audit/architecture/HexagonalArchitectureTest.kt  // 타 모듈과 동일 레이어 가드(리뷰 완성도)
  src/main/resources/db/migration/V19__create_audit_logs_table.sql
```

**의존**: carry-infra-persistence(BaseEntity/JPA), `org.springframework.security:spring-security-core`(SecurityContextHolder), `spring-web`(RequestContextHolder), jackson(JSON). **사이클 없음** — carry-audit는 어떤 도메인 모듈도 의존하지 않는다. `settings.gradle.kts`에 `include("carry-audit")` 추가, carry-order/payment/dispatch + carry-app이 의존.

### 4.1 포트
```kotlin
enum class AuditAction { ORDER_CANCEL, PAYMENT_REFUND, DISPATCH_ASSIGN, DISPATCH_REJECT_PENALTY }

interface AuditPort {
    /** before/after는 임의 객체(Map 등) — 어댑터가 JSON 직렬화. null 허용. */
    fun record(action: AuditAction, targetType: String, targetId: String, before: Any?, after: Any?)
}
```

## 5. 스키마 (V19, 전역 순차 — 현재 최신 V18)

```sql
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 행위 시각(어댑터가 Instant.now() 설정)
    actor       VARCHAR(64) NOT NULL,                -- userId 문자열 또는 'SYSTEM'
    role        VARCHAR(32),
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    before      JSONB,
    after       JSONB,
    ip          VARCHAR(64),
    trace_id    VARCHAR(64)
);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
```

⚠️ **BaseEntity 미상속**(리뷰 #1 BLOCKER): BaseEntity는 `created_at`+`updated_at`(NOT NULL) 둘 다 가지므로 상속하면 `updated_at` 컬럼 누락으로 `validate` 부팅 실패. 감사 행은 append-only 불변이라 `updated_at`이 무의미 → 자체 `@Id`(BIGSERIAL `IDENTITY`/`@GeneratedValue`) + `createdAt: Instant`만 둔다.
⚠️ test 프로파일 = `ddl-auto: validate` → 엔티티가 이 DDL과 **정확히 일치**해야 함(DDL-drift 교훈). local = `ddl-auto: update`(자동).
- before/after 매핑: **outbox_events 선례를 그대로 따름** — `String` 필드에 `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`. 즉 **어댑터가 `Any?`→JSON `String`으로 직렬화**한 뒤 엔티티에 담는다(Map/Any 직접 매핑 아님).

## 6. 쓰기 정책 (D2 정밀화 — 리뷰 #2 반영)

- `audit.record(...)`는 호출 서비스의 `@Transactional` 내부에서 실행 → **액션 + 감사 INSERT가 같은 트랜잭션**. 액션이 이후 롤백되면 감사도 롤백(일관성: 성공한 액션만 감사됨).
- **같은 tx의 함의(정직하게)**: 같은 트랜잭션이므로 감사 INSERT가 실패하면 설계상 **비즈니스 액션도 롤백된다**(일관성을 가용성보다 우선, D2). REQUIRES_NEW로 분리하면 "감사 실패가 액션을 안 깸"은 얻지만 롤백된 액션의 감사 행이 남아 **거짓 트레일**이 되므로 채택하지 않는다.
- **best-effort = 어댑터 내부 방어로 INSERT 실패 확률 최소화**(액션을 깨지 않으려는 목적):
  - context 해석 실패(미인증/비-Long principal/request 부재)는 throw 없이 degrade(actor=SYSTEM, role/ip=null).
  - `before/after` JSON 직렬화 실패는 catch → 해당 값 `null`로 degrade.
  - `actor/role/target_type/target_id`는 컬럼 한도(64/32/64/64)로 **방어적 truncate** 후 저장(과길이 값이 INSERT를 깨지 않게).
- 위 방어로 남는 실패 모드는 사실상 **DB-unavailable**인데, 그 경우 액션의 자기 쓰기도 실패하므로 감사가 "성공할 액션"을 추가로 깨는 일은 드물다. 별도 fail-closed 게이트는 두지 않는다.

## 7. 감사 지점 (서비스 액션, before는 변이 전 캡처)

| 서비스 메서드 | action | target | before | after |
|---|---|---|---|---|
| `OrderCommandService.cancelOrder` | ORDER_CANCEL | ORDER / orderId | `{status: 변이전, reason, cancelledBy}` | `{status: REFUND_PENDING\|CANCELLED}` |
| `PaymentCommandService.requestRefund` | PAYMENT_REFUND | PAYMENT / orderId | `{status: COMPLETED}` | `{status: REFUNDED, reason}` |
| `DispatchCommandService.assignDispatch` | DISPATCH_ASSIGN | DISPATCH / dispatchId | `{carrierId: 변이전, status: 변이전}` | `{carrierId: 신규, status: ASSIGNED}` |
| `DispatchCommandService.rejectAssignment` | DISPATCH_REJECT_PENALTY | DISPATCH / dispatchId | `{carrierId, status: 변이전}` | `{status, penaltyReason}` |

- before 스냅샷은 변이 메서드 호출 **전** 로컬 변수로 캡처(예: `val beforeStatus = order.status`).
- ⚠️ `after`의 필드는 **구현 시 실제 도메인에서 확인**(리뷰 #4): `rejectAssignment()`가 반환하는 `penaltyRecord`의 실제 필드(`reason: PenaltyReason` 등)에서 `penaltyReason`을 캡처. 코드에 없는 필드는 넣지 않는다. ORDER_CANCEL의 `reason`/`cancelledBy`는 액션 입력이므로 `after`(결과 컨텍스트)에 둔다.
- `cancelOrder`는 코디·saga 공유 경로 → audit.record 1개로 양쪽 처리(actor가 자연히 코디 userId 또는 SYSTEM). `cancelOrderByCustomer`(고객 본인 경로)는 본 스코프 외(필요 시 후속).
- 각 서비스에 `AuditPort` 생성자 주입.

## 8. 테스트 (TDD)

| 레이어 | 케이스 |
|--------|--------|
| `AuditPersistenceAdapter` 단위 | SecurityContext(userId)+MDC(traceId)+RequestContext(ip) 세팅 → AuditLog 필드 정확·before/after JSON 직렬화 / **context 부재 → actor=SYSTEM·role/ip=null degrade** / **비-Long principal("anonymousUser") → SYSTEM** / 직렬화 예외 → throw 안 함(해당 값 null) / **과길이 actor·target → 컬럼 한도로 truncate** |
| `carry-audit` ArchUnit | 도메인 레이어 Spring/JPA 비의존 등(타 모듈 HexagonalArchitectureTest와 동일) |
| 4개 서비스 단위(AuditPort mock) | 각 메서드가 올바른 action/target/before/after로 record 호출 |
| `carry-app` 통합(Testcontainers) | 코디 REST cancel → `audit_logs` 행(actor=userId·role=COORDINATOR·ip·traceId·before/after) / saga 경로 cancel → actor=SYSTEM·ip=null / V19 적용 + validate |
| 라이브 스모크 | docker 풀스택: 코디 cancel REST → DB audit_logs 행 확인 |

## 9. 검증 흐름

전체 `compileTestKotlin`(신규 모듈 의존 정합) → 영향모듈 `:test`(carry-audit/order/payment/dispatch) → `:carry-app:test`(Testcontainers, V19+validate) → **라이브 풀스택 스모크**(`MANAGEMENT_TRACING_ENABLED=false`). 이슈 먼저 → PR base develop → **dev 머지=사용자 게이트**.

## 10. 영향 범위 (호출자 정합)

- `OrderCommandService`/`PaymentCommandService`/`DispatchCommandService` 생성자에 `AuditPort` 추가 → 각 서비스 테스트의 생성자 인자.
- `settings.gradle.kts` + carry-order/payment/dispatch/app `build.gradle.kts`에 carry-audit 의존 추가.
- carry-app 컴포넌트 스캔에 carry-audit 패키지 포함(어댑터/엔티티/리포지토리 빈 등록).
