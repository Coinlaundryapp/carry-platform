# 04. 모듈 간 통신 및 데이터베이스 전략

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 모듈 간 통신 — 두 가지 패턴

### 패턴 A — 동기 조회 (Query)

모듈 A가 모듈 B의 데이터를 조회해야 할 때 사용한다.

```
[carry-order]                              [carry-laundromat]
OrderCreationService                       LaundromatQueryService
      │                                          ▲
      │ outbound port                             │ inbound port
      ▼                                          │
LaundromatQueryPort ───(모놀리스: 직접 빈 주입)───→ │
                    ───(분리 시: HTTP/gRPC)──────→ │
```

```kotlin
// carry-order 모듈 내 Outbound Port 정의
interface LaundromatQueryPort {
    fun findById(id: Long): LaundromatView
}

// carry-app 또는 carry-order/infrastructure에서 구현
@Component
class LaundromatQueryAdapter(
    private val laundromatQueryService: LaundromatQueryService  // carry-laundromat의 inbound
) : LaundromatQueryPort {
    override fun findById(id: Long): LaundromatView {
        return laundromatQueryService.getById(id)
    }
}
```

**분리 시**: `LaundromatQueryAdapter` 내부만 HTTP 클라이언트로 교체. 도메인 코드 변경 없음.

### 패턴 B — 비동기 이벤트 (Event)

모듈 A의 상태 변경을 모듈 B에 전파할 때 사용한다.

```
[carry-order]              [Kafka]                [carry-payment]
OrderService               carry.order.events     PaymentEventConsumer
  │ 주문 생성                     │                       │
  │ + Outbox 저장 ──(Debezium)──→│                       │
  │                              │── OrderCreatedEvent ──→│
  │                              │                       │ 결제 레코드 생성
```

- 모듈 간 상태 변경은 **반드시** 이 패턴을 사용한다.
- 모놀리스 내부에서도 Spring `ApplicationEvent`가 아닌 Kafka를 사용한다.
- 이는 물리적 분리 시 코드 변경이 불필요함을 보장한다.

### 패턴 선택 기준

| 상황 | 패턴 | 예시 |
|------|------|------|
| 주문 생성 시 세탁소 정보 조회 | A (동기) | Order → Laundromat |
| 주문 생성 후 결제 레코드 생성 | B (이벤트) | Order → Payment |
| 주문 상세 조회 시 결제 상태 필요 | A (동기) | Order → Payment |
| 결제 완료 후 배차 생성 | B (이벤트) | Payment → Dispatch |

원칙: **상태를 변경하는 건 이벤트, 읽기만 하는 건 동기 조회.**

---

## 데이터베이스 전략

### 단일 인스턴스, 논리적 분리

하나의 PostgreSQL 인스턴스를 사용하되, 모듈별 테이블 프리픽스로 소유권을 구분한다.

| 모듈 | 테이블 프리픽스 | 예시 |
|------|----------------|------|
| carry-order | `order_` | `order_orders`, `order_specs`, `order_outbox` |
| carry-payment | `payment_` | `payment_payments`, `payment_outbox` |
| carry-dispatch | `dispatch_` | `dispatch_dispatches`, `dispatch_jobs`, `dispatch_outbox` |
| carry-user | `user_` | `user_users`, `user_shipping_addresses` |
| carry-laundromat | `laundromat_` | `laundromat_laundromats` |
| carry-review | `review_` | `review_reviews`, `review_media` |

### 마이그레이션

각 모듈이 자체 Flyway 마이그레이션을 소유한다:

```
carry-order/src/main/resources/db/migration/
  V1__create_order_tables.sql
  V2__add_order_outbox.sql

carry-payment/src/main/resources/db/migration/
  V1__create_payment_tables.sql
```

### 금지 규칙

- 모듈 A가 모듈 B의 테이블을 직접 JOIN/조회하는 것을 금지한다.
- 크로스 모듈 데이터가 필요하면 반드시 Outbound Port(동기 조회) 또는 이벤트(비동기 동기화)를 사용한다.
- 이 규칙은 ArchUnit 테스트로 강제한다.

### 마이크로서비스 전환 시 DB 분리 전략

```
Phase 1 (모놀리스): 단일 PostgreSQL, 테이블 프리픽스로 논리 분리
     │
     ▼
Phase 2 (스키마 분리): 같은 인스턴스, 모듈별 PostgreSQL 스키마
     │                  order 스키마, payment 스키마, dispatch 스키마 ...
     ▼
Phase 3 (인스턴스 분리): 모듈별 독립 PostgreSQL 인스턴스
                         서비스별 자체 DB
```

프리픽스 규칙을 지켰다면 Phase 2 전환은 스키마 이름만 바꾸면 된다.
Phase 3은 Debezium connector의 DB 연결 정보만 변경하면 된다.
