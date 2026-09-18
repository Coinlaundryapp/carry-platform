# 15. 불변식 카탈로그

> 최종 수정일: 2026-09-08
> 상태: 사후 기록 (코드에서 역추출)
> 범위: Order, Dispatch, Invoice/Payment/BillingKey/Ledger, Delivery 애그리거트와 사가·스위퍼가 지키는 시스템 수준 불변식

이 문서는 코드에 실제로 존재하는 불변식을 모아 놓은 목록이다. 각 행은 작성 시점에 확인한 파일 경로와 줄 범위를 인용한다. "검증 상태" 열은 해당 규칙을 직접 건드리는 단위 테스트가 테스트 디렉터리에 있는지를 grep 으로 확인한 결과다. 통합 테스트만 있는 경우는 별도로 적었다. 확인하지 못한 항목은 `미확인`으로 남겼다.

## 읽는 법

- 위반 시 예외의 HTTP 매핑은 `carry-common/src/main/kotlin/com/carry/common/exception/ErrorCode.kt` 기준이다.
- `requireInput`/`checkState`는 `carry-common/src/main/kotlin/com/carry/common/exception/DomainValidation.kt:16-30` 에 정의된 헬퍼로, 각각 `BusinessException(INVALID_INPUT, 400)`, `BusinessException(CONFLICT, 409)` 를 던진다. 아래 표에서 예외 클래스명이 `BusinessException(INVALID_INPUT)` 처럼 적힌 행은 이 헬퍼를 쓴다는 뜻이다.
- 모든 애그리거트는 `private constructor` + `create`/`reconstitute` 팩토리 구조다. `reconstitute` 는 검증 없이 DB 값을 복원하므로 생성 불변식은 `create` 경로에서만 강제된다.

---

## 1. Order (`carry-order`)

파일: `carry-order/src/main/kotlin/com/carry/order/domain/model/Order.kt`, `carry-order/src/main/kotlin/com/carry/order/domain/vo/OrderEnums.kt`

### 1.1 생성 불변식

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| O-C1 | 선택 옵션이 1개 이상이어야 한다 | `Order.kt:48` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`carry-order/src/test/kotlin/com/carry/order/domain/model/OrderTest.kt:80-84`) |
| O-C2 | `desiredDeliveryAt` 는 `desiredPickupAt` 보다 뒤여야 한다 | `Order.kt:49` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`OrderTest.kt:88-92`) |
| O-C3 | 생성 직후 상태는 `CREATED`, carrierId/actualWeight/cancellation/completedAt 은 모두 null | `Order.kt:51-67` | 해당 없음 (구조적 보장) | 단위 테스트 있음 (`OrderTest.kt:65-77`) |
| O-C4 | 배송지 VO 의 도로명주소·수령인 이름·전화·지역코드는 공백일 수 없다 | `carry-order/src/main/kotlin/com/carry/order/domain/vo/OrderShippingAddress.kt:16-21` | `BusinessException(INVALID_INPUT)` | 없음 (테스트 디렉터리에서 해당 메시지·공백 입력 케이스를 찾지 못함) |
| O-C5 | 취소 정보(사유·주체·시각)는 셋 다 있거나 셋 다 없다 | `carry-order/src/main/kotlin/com/carry/order/domain/vo/OrderCancellation.kt:12-16` (data class 로 타입 수준 표현) | 해당 없음 (타입으로 강제) | 단위 테스트 있음 (취소 테스트가 `cancellation` 을 함께 확인, `OrderTest.kt:145-160`) |

### 1.2 상태 전이표

허용 전이의 원천은 `OrderEnums.kt:7-14` (`canTransitionTo`) 이고, 실제 전이는 `Order.kt:121-126` (`transitTo`) 가 수행한다. 취소만 `canTransitionTo` 를 거치지 않고 `isCancellableBy` (`OrderEnums.kt:17-21`) 를 쓴다 (`Order.kt:113-119`).

| from | to | 허용 메서드 | 부수 효과 | 위반 시 예외 (HTTP) |
|------|----|-------------|-----------|---------------------|
| CREATED | DISPATCHED | `markDispatched(carrierId)` (`Order.kt:94-97`) | `carrierId` 기록 | `InvalidOrderStatusTransitionException` (400, `ErrorCode.kt:46`) |
| DISPATCHED | PICKED_UP | `markPickedUp(actualWeight)` (`Order.kt:99-102`) | `actualWeight` 기록 | 동일 |
| PICKED_UP | IN_PROGRESS | `markInProgress()` (`Order.kt:104-106`) | 없음 | 동일 |
| IN_PROGRESS | COMPLETED | `markCompleted(now)` (`Order.kt:108-111`) | `completedAt` 기록 | 동일 |
| CREATED, DISPATCHED | CANCELLED | `cancel(reason, by=CUSTOMER/COORDINATOR/SYSTEM, now)` (`Order.kt:113-119`) | `cancellation` 기록 | `OrderNotCancellableException` (409, `ErrorCode.kt:47`) |
| PICKED_UP, IN_PROGRESS | CANCELLED | `cancel(reason, by=COORDINATOR/SYSTEM, now)` | 동일 | `OrderNotCancellableException` (고객이 시도하면 거부) |
| COMPLETED, CANCELLED | (없음) | 종결 상태 | | `InvalidOrderStatusTransitionException` / `OrderNotCancellableException` |

검증 상태: 단위 테스트 있음. `OrderTest.kt:100-190` (전이·취소 행위자별), `carry-order/src/test/kotlin/com/carry/order/domain/vo/OrderStatusTest.kt:9-108` (enum 규칙 전수).

### 1.3 도메인 규칙

| # | 규칙 | 강제 위치 | 검증 상태 |
|---|------|-----------|-----------|
| O-R1 | 고객은 수거 전(CREATED, DISPATCHED)에만 취소할 수 있고, 코디네이터·시스템은 완료 전까지 취소할 수 있다 | `OrderEnums.kt:17-21` | 단위 테스트 있음 (`OrderStatusTest.kt:78-98`, `OrderTest.kt:145-190`) |
| O-R2 | `isForwardActive()` 는 COMPLETED·CANCELLED 에서만 false 다 (늦게 도착한 forward 이벤트의 no-op 판정 술어) | `OrderEnums.kt:24` | 단위 테스트 있음 (`OrderStatusTest.kt:100-110`) |
| O-R3 | 주문 상태는 물리 세계의 사실만 기술하고 결제 상태를 포함하지 않는다 (INVOICED/PAID 등 없음) | `OrderEnums.kt:3-5` (enum 정의 자체) | 단위 테스트 있음 (`OrderStatusTest.kt:53-66` Happy Path 가 6개 상태만 사용) |

---

## 2. Dispatch (`carry-dispatch`)

파일: `carry-dispatch/src/main/kotlin/com/carry/dispatch/domain/model/Dispatch.kt`, `.../domain/vo/DispatchEnums.kt`, `.../domain/model/PenaltyRecord.kt`, `.../domain/model/CarrierArea.kt`

### 2.1 생성 불변식

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| D-C1 | 생성 직후 상태는 `PENDING`, carrierId/assignedBy/assignedAt/acceptedAt/cancelReason 은 null | `Dispatch.kt:36-44` | 해당 없음 (구조적 보장) | 단위 테스트 있음 (`carry-dispatch/src/test/kotlin/com/carry/dispatch/domain/model/DispatchTest.kt` 의 fixture 가 `create` 사용) |
| D-C2 | `Dispatch.create` 는 입력값 검증(`requireInput`)이 없다 | `Dispatch.kt:36-44` | 없음 | 해당 없음 |
| D-C3 | `CarrierArea.create` 는 `areaCode` 가 공백이면 거부한다 | `CarrierArea.kt:19` | `BusinessException(INVALID_INPUT)` | 없음 (`CarrierArea.create` 를 호출하는 테스트를 찾지 못함) |
| D-C4 | `PenaltyRecord` 는 생성 후 변경할 수 없다 (모든 필드 `val`, 변경 메서드 없음) | `PenaltyRecord.kt:6-22` | 해당 없음 | 해당 없음 |

### 2.2 상태 전이표

`canTransitionTo` 는 `DispatchEnums.kt:10-16` 에 정의돼 있으나 `Dispatch.cancel()` 만 이를 참조한다 (`Dispatch.kt:115`). 나머지 메서드는 현재 상태를 직접 비교한다. 대부분 메서드는 멱등이라 목표 상태에 이미 있으면 `false` 를 반환하고 예외를 던지지 않는다.

| from | to | 허용 메서드 | 멱등 조건 (false 반환) | 위반 시 예외 (HTTP) |
|------|----|-------------|------------------------|---------------------|
| PENDING | ACCEPTED | `claimByCarrier(carrierId, now)` (`Dispatch.kt:61-71`) | 이미 ACCEPTED 이고 같은 carrierId 이고 assignedBy=CARRIER | `DispatchNotPendingException` (400, `ErrorCode.kt:71`) |
| PENDING | ASSIGNED | `assignByCoordinator(carrierId, now)` (`Dispatch.kt:77-85`) | 이미 ASSIGNED 이고 같은 carrierId | `DispatchNotPendingException` (400) |
| ASSIGNED | ACCEPTED | `acceptAssignment(now)` (`Dispatch.kt:91-97`) | 이미 ACCEPTED | `DispatchAlreadyAcceptedException` (409, `ErrorCode.kt:72`) |
| ASSIGNED | PENDING | `rejectAssignment(now)` (`Dispatch.kt:99-107`) | 멱등 아님 | `DispatchAlreadyAcceptedException` (409) |
| PENDING, ASSIGNED, ACCEPTED | CANCELLED | `cancel(reason)` (`Dispatch.kt:113-121`) | 이미 CANCELLED (기존 사유 유지) | `DispatchNotCancellableException` (409, `ErrorCode.kt:73`) |
| PENDING | TIMEOUT | `timeout()` (`Dispatch.kt:127-132`) | 이미 TIMEOUT | `DispatchTimeoutNotAllowedException` (400, `ErrorCode.kt:74`) |
| CANCELLED, TIMEOUT | (없음) | 종결 상태 (`DispatchEnums.kt:14-15`) | | 위 예외들 |

검증 상태: 단위 테스트 있음. `DispatchTest.kt:45-305` 가 정상 전이·멱등 no-op·충돌 예외를 메서드별로 다룬다.

### 2.3 도메인 규칙

| # | 규칙 | 강제 위치 | 검증 상태 |
|---|------|-----------|-----------|
| D-R1 | 강제 배정을 거절하면 배차는 PENDING 으로 돌아가고 carrierId/assignedBy/assignedAt 이 지워지며, 거절한 캐리어에게 `REJECTED_FORCED_ASSIGNMENT` 패널티 레코드가 생성된다 | `Dispatch.kt:99-107`; 저장은 `carry-dispatch/src/main/kotlin/com/carry/dispatch/application/service/DispatchCommandService.kt:120-137` | 단위 테스트 있음 (`DispatchTest.kt:186-198`; 저장 검증 `carry-dispatch/src/test/kotlin/com/carry/dispatch/application/service/DispatchCommandServiceTest.kt:289`) |
| D-R2 | `rejectAssignment` 은 영속화된 배차(id != null)와 배정된 캐리어(carrierId != null)를 전제한다 (`!!`) | `Dispatch.kt:101,106` | 미확인 (전제 위반 케이스 테스트 없음, 위반 시 `NullPointerException`) |
| D-R3 | 캐리어는 자기 구역(active `CarrierArea`)의 PENDING 배차만 선점할 수 있다. 이미 소유한 배차의 멱등 재시도는 구역 검사를 건너뛴다 | `DispatchCommandService.kt:45-50` | 단위 테스트 있음 (`DispatchCommandServiceTest.kt:122`, `CarrierNotInAreaException` 케이스) |
| D-R4 | 수락·거절·소유 검증은 서비스 계층에서 `carrierId` 비교로 수행한다 (`DispatchNotOwnedException`) | `DispatchCommandService.kt:94-96, 122-124` | 단위 테스트 있음 (`DispatchCommandServiceTest.kt`) |
| D-R5 | PENDING 배차는 `desiredPickupAt` 30분 전이 지나면 만료다 | 도메인 술어 `Dispatch.kt:134-136`; 실제 스위퍼 조회는 SQL `carry-dispatch/src/main/kotlin/com/carry/dispatch/adapter/outbound/persistence/repository/DispatchJpaRepository.kt:49` (`INTERVAL '30 minutes'`) | 단위 테스트 있음 (`DispatchTest.kt:290-310`, 도메인 술어만) |
| D-R6 | 멱등 no-op(false) 전이에서는 이벤트 발행·메트릭을 억제한다 | `DispatchCommandService.kt:52-53, 165-166` | 단위 테스트 있음 (`DispatchCommandServiceTest.kt`) |

---

## 3. Payment 모듈 (`carry-payment`)

파일: `carry-payment/src/main/kotlin/com/carry/payment/domain/model/{Invoice,Payment,BillingKey,LedgerEntry}.kt`, `.../domain/vo/{PaymentEnums,InvoiceLineItem}.kt`

### 3.1 Invoice

#### 생성 불변식

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| I-C1 | 청구 항목이 1개 이상이어야 한다 | `Invoice.kt:32` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/domain/model/InvoiceTest.kt:57-61`) |
| I-C2 | `totalAmount` 는 항상 `lineItems.amount` 의 합이다 | `Invoice.kt:34` (create 에서 계산, `val`) | 해당 없음 | 단위 테스트 있음 (`InvoiceTest.kt:51-55`) |
| I-C3 | 라인아이템 금액은 0 이상이다 | `InvoiceLineItem.kt:10-12` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`InvoiceTest.kt:65-69`) |
| I-C4 | 생성 직후 상태는 `ISSUED` | `Invoice.kt:39` | 해당 없음 | 단위 테스트 있음 (`InvoiceTest.kt:42-49`) |

#### 상태 전이표 (`PaymentEnums.kt:6-13`, 전이 실행 `Invoice.kt:80-85`)

| from | to | 허용 메서드 | 위반 시 예외 (HTTP) |
|------|----|-------------|---------------------|
| ISSUED | PAID | `markPaid()` (`Invoice.kt:63-65`) | `BusinessException(CONFLICT)` (409, `checkState`) |
| ISSUED | OVERDUE | `markOverdue()` (`Invoice.kt:68-70`) | 동일 |
| ISSUED | CANCELLED | `cancel()` (`Invoice.kt:72-74`) | 동일 |
| OVERDUE | PAID | `markPaid()` (재과금 성공으로 회복) | 동일 |
| OVERDUE | CANCELLED | `cancel()` | 동일 |
| PAID | REFUNDED | `refund()` (`Invoice.kt:76-78`) | 동일 |
| CANCELLED, REFUNDED | (없음) | 종결 | 동일 |

검증 상태: 단위 테스트 있음 (`InvoiceTest.kt:77-137`, OVERDUE 왕복 포함).

### 3.2 Payment

#### 생성 불변식

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| P-C1 | 결제 금액은 0 보다 커야 한다 | `Payment.kt:50` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/domain/model/PaymentTest.kt:56-60`) |
| P-C2 | 생성 직후 상태는 `PENDING`, retryCount=0, nextRetryAt/paidAt/failReason/pgTransactionId 는 null | `Payment.kt:52-67` | 해당 없음 | 단위 테스트 있음 (`PaymentTest.kt:46-54`) |

#### 상태 전이표 (`PaymentEnums.kt:19-26`, 전이 실행 `Payment.kt:125-130`)

| from | to | 허용 메서드 | 부수 효과 | 위반 시 예외 (HTTP) |
|------|----|-------------|-----------|---------------------|
| PENDING | COMPLETED | `markCompleted(pgTransactionId, now)` (`Payment.kt:91-95`) | pgTransactionId, paidAt 기록 | `BusinessException(CONFLICT)` (409) |
| PENDING | FAILED | `markFailed(reason)` (`Payment.kt:97-100`) | failReason 기록 | 동일 |
| FAILED | PENDING | `markRetrying()` (`Payment.kt:120-123`) | nextRetryAt 초기화 | 동일 |
| COMPLETED | REFUND_PENDING | `markRefundPending()` (`Payment.kt:103-105`) | 없음 | 동일 |
| REFUND_PENDING | REFUNDED | `markRefunded()` (`Payment.kt:108-110`) | 없음 | 동일 |
| REFUNDED | (없음) | 종결 | | 동일 |

검증 상태: 단위 테스트 있음 (`PaymentTest.kt:68-158`).

#### 도메인 규칙

| # | 규칙 | 강제 위치 | 검증 상태 |
|---|------|-----------|-----------|
| P-R1 | 재시도 예약은 FAILED 상태에서만 가능하고, 매 예약마다 retryCount 가 1 증가한다 | `Payment.kt:113-117` | 단위 테스트 있음 (`PaymentTest.kt:130-143`) |
| P-R2 | 백오프는 1h → 4h → 12h → 24h, 이후 24h 고정 (도메인 상수) | `Payment.kt:35-40` | 단위 테스트 있음 (`PaymentTest.kt:160`) |
| P-R3 | 환불은 REFUND_PENDING 을 거쳐야 하며 COMPLETED 에서 REFUNDED 로 직접 갈 수 없다 | `PaymentEnums.kt:22-23` | 단위 테스트 있음 (`PaymentTest.kt:85-99`) |

### 3.3 BillingKey

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| B-C1 | `cardLast4` 는 정확히 4자리다 | `BillingKey.kt:38` | `BusinessException(INVALID_INPUT)` | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/domain/model/BillingKeyTest.kt:40-44`) |
| B-C2 | 생성 직후 상태는 `ACTIVE`, invalidatedAt 은 null | `BillingKey.kt:39-40` | 해당 없음 | 단위 테스트 있음 (`BillingKeyTest.kt:19-22`) |
| B-T1 | ACTIVE → INVALID 는 `invalidate(now)` 로만 가능하고, INVALID 에서 다시 호출하면 거부된다 (전이는 이 하나뿐) | `BillingKey.kt:27-31` | `BusinessException(CONFLICT)` (409) | 단위 테스트 있음 (`BillingKeyTest.kt:24-37`) |
| B-R1 | 고객당 ACTIVE 빌링키는 최대 1개다. 재등록 시 기존 키를 먼저 무효화하고 flush 한 뒤 새 키를 저장한다 | 서비스 `carry-payment/src/main/kotlin/com/carry/payment/application/service/BillingKeyService.kt:48-60`; DB 부분 유니크 인덱스 `carry-payment/src/main/resources/db/migration/V26__create_customer_billing_keys.sql:17-18` | 서비스 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/application/service/BillingKeyServiceTest.kt`). 인덱스 자체를 검증하는 테스트는 찾지 못함 (`uq_billing_keys` grep 결과 없음) |
| B-R2 | billingKey 값은 영속화 시 암호화된다 | `BillingKey.kt:8-12` 주석; 구현체 `BillingKeyCryptoConverter` (docs/06-saga.md:262 에 테스트 언급) | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/adapter/outbound/persistence/crypto/BillingKeyCryptoConverterTest.kt`, 내용은 미열람) |

### 3.4 Ledger (append-only 정산 원장)

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| L-R1 | 거래 그룹(결제 1건 또는 환불 1건) 안의 `Σamount == 0` | `LedgerEntry.kt:67-73` (`balanced`, Kotlin `require`) | `IllegalArgumentException` (BusinessException 아님, 500 으로 매핑됨) | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/domain/model/LedgerEntriesTest.kt:81`) |
| L-R2 | 결제 그룹 = 고객 총액 차변 1행 + 라인아이템별 수취 행. LAUNDRY_PRICE·DELIVERY_FEE 는 CARRIER, SERVICE_FEE 는 PLATFORM 으로 귀속된다 | `LedgerEntry.kt:38-65` | 해당 없음 | 단위 테스트 있음 (`LedgerEntriesTest.kt:37-66`) |
| L-R3 | 환불 그룹은 결제 그룹과 행 구성이 같고 부호만 반대다 (`sign = -1`) | `LedgerEntry.kt:34-36` | 해당 없음 | 단위 테스트 있음 (`LedgerEntriesTest.kt:68-79`) |
| L-R4 | 원장 행은 append 만 가능하고 수정·삭제 경로가 없다 | `carry-payment/src/main/kotlin/com/carry/payment/application/port/outbound/LedgerPort.kt:8-9` (주석과 인터페이스 형태), `LedgerPersistenceAdapter.kt:15-17` (`saveAll` 만 호출), 테이블 `V25__create_payment_ledger_entries.sql` (트리거·REVOKE 없음) | 해당 없음 (코드 관례) | 없음 (수정 불가를 검증하는 테스트 없음) |
| L-R5 | 원장 기입은 결제 완료·환불 확정과 같은 트랜잭션에서 일어난다 | `carry-payment/src/main/kotlin/com/carry/payment/application/service/AutoChargeService.kt:121-127`, `.../PaymentCommandService.kt:83-96` | 해당 없음 | 서비스 단위 테스트 있음 (`AutoChargeServiceTest.kt`, `PaymentCommandServiceTest.kt` 가 `LedgerPort.record` 호출 검증). 통합: `carry-app/src/test/kotlin/com/carry/app/saga/SettlementLedgerIntegrationTest.kt` |

---

## 4. Delivery (`carry-delivery`)

파일: `carry-delivery/src/main/kotlin/com/carry/delivery/domain/model/Delivery.kt`, `.../DeliveryStep.kt`, `.../domain/vo/DeliveryEnums.kt`

### 4.1 생성 불변식

| # | 불변식 | 강제 위치 | 위반 시 예외 | 검증 상태 |
|---|--------|-----------|--------------|-----------|
| V-C1 | 생성 시 PICKUP, WEIGHING, WASHING, DRYING, DELIVERY 5개 스텝이 모두 PENDING 으로 만들어진다 | `Delivery.kt:35-41`, `DeliveryStep.kt:22-30` | 해당 없음 | 단위 테스트 있음 (`carry-delivery/src/test/kotlin/com/carry/delivery/domain/model/DeliveryTest.kt:58-79`) |
| V-C2 | 생성 직후 상태는 `PICKUP_PENDING`, actualWeight 는 null | `Delivery.kt:48-49` | 해당 없음 | 단위 테스트 있음 (동일) |
| V-C3 | `Delivery.create` 는 입력값 검증이 없다 (orderId/dispatchId/carrierId/laundromatId 를 그대로 받는다) | `Delivery.kt:28-54` | 없음 | 해당 없음 |

### 4.2 상태 전이표 (`DeliveryEnums.kt:12-20`, 전이 실행 `Delivery.kt:136-141`)

모든 전이 메서드는 멱등이다. 목표 상태에 이미 있으면 사전 검증(무게·사진)도 건너뛰고 `false` 를 반환한다.

| from | to | 허용 메서드 | 사전 조건 | 부수 효과 | 위반 시 예외 (HTTP) |
|------|----|-------------|-----------|-----------|---------------------|
| PICKUP_PENDING | PICKED_UP | `completePickup(weight, photoIds, now)` (`Delivery.kt:85-94`) | weight > 0, 사진 1장 이상 | actualWeight 기록, PICKUP·WEIGHING 스텝 완료 | `DeliveryWeightRequiredException` (400, `ErrorCode.kt:82`), `DeliveryPhotoRequiredException` (400, `ErrorCode.kt:83`), `DeliveryNotInExpectedStatusException` (400, `ErrorCode.kt:81`) |
| PICKED_UP | IN_LAUNDRY | `startWashing(photoIds, now)` (`Delivery.kt:96-102`) | 사진 1장 이상 | WASHING 스텝 완료 | `DeliveryPhotoRequiredException`, `DeliveryNotInExpectedStatusException` |
| IN_LAUNDRY | LAUNDRY_COMPLETE | `completeDrying(photoIds, now)` (`Delivery.kt:104-110`) | 사진 1장 이상 | DRYING 스텝 완료 | 동일 |
| LAUNDRY_COMPLETE | DELIVERY_PENDING | `startDelivery()` (`Delivery.kt:112-116`) | 없음 | 없음 | `DeliveryNotInExpectedStatusException` |
| DELIVERY_PENDING | DELIVERED | `completeDelivery(photoIds, now)` (`Delivery.kt:118-124`) | 사진 1장 이상 | DELIVERY 스텝 완료 | `DeliveryPhotoRequiredException`, `DeliveryNotInExpectedStatusException` |
| 비종결 상태 전부 | CANCELLED | `cancel()` (`Delivery.kt:126-130`) | 없음 | 없음 | `DeliveryNotInExpectedStatusException` |
| DELIVERED, CANCELLED | (없음) | 종결 | | | 동일 |

검증 상태: 단위 테스트 있음 (`DeliveryTest.kt:81-290`, 멱등 no-op 케이스 포함).

### 4.3 도메인 규칙

| # | 규칙 | 강제 위치 | 검증 상태 |
|---|------|-----------|-----------|
| V-R1 | `DeliveryStep.complete` 는 상태 가드가 없다. 이미 COMPLETED 인 스텝을 다시 완료하면 mediaIds 가 누적된다. 실제 보호는 `Delivery` 의 멱등 가드(목표 상태면 조기 반환)에 의존한다 | `DeliveryStep.kt:51-56`, `Delivery.kt:86,97,105,119` | 없음 (`DeliveryStep` 단독 테스트 없음, `DeliveryTest.kt` 에서 간접 확인) |
| V-R2 | 멱등 no-op 전이에서는 이벤트를 재발행하지 않는다 (사가 이중 트리거 방지) | `carry-delivery/src/main/kotlin/com/carry/delivery/application/service/DeliveryCommandService.kt:42-43, 69-70, 89-90, 97-98, 110-111` | 서비스 단위 테스트 있음 (`carry-delivery/src/test/kotlin/com/carry/delivery/application/service/DeliveryCommandServiceTest.kt`) |
| V-R3 | 행위자는 항상 배정된 캐리어 본인이며 소유 검증은 서비스가 한다 (`DeliveryNotOwnedException`) | `Delivery.kt:81-83` 주석, `DeliveryCommandService.kt` | 서비스 단위 테스트 있음 (동일 파일, 상세 케이스 미확인) |

---

## 5. 시스템 수준 불변식 (사가·스위퍼·크로스 모듈)

애그리거트 하나로는 지킬 수 없고, 사가 핸들러·스위퍼·포트 어댑터가 함께 지키는 규칙이다. 근거 문서는 `docs/06-saga.md` 다.

| # | 불변식 | 강제 위치 | 검증 상태 |
|---|--------|-----------|-----------|
| S-1 | 존재하는 주문은 결제 때문에 멈추지 않는다. 물리 사가는 결제 이벤트를 소비하지 않고, 대신 주문 생성 시점에 지불수단을 확보한다 | 문서 `docs/06-saga.md:29, 108-109`; 코드 `carry-order/src/main/kotlin/com/carry/order/application/service/OrderSagaHandler.kt:31-107` (핸들러가 Dispatch/Delivery 이벤트뿐), `OrderCommandService.kt:54-58` (`BILLING_KEY_REQUIRED` 409, `ErrorCode.kt:50`) | 단위 테스트 있음 (`carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt:116-127`); 통합 `carry-app/src/test/kotlin/com/carry/app/saga/AutoChargeSagaIntegrationTest.kt` |
| S-2 | OVERDUE 인보이스가 있는 고객은 신규 주문을 만들 수 없다 (409 `OVERDUE_INVOICE_EXISTS`). 진행 중인 기존 주문에는 영향이 없다 | `OrderCommandService.kt:60-62`; 조회 `BillingKeyService.kt:81-83` → `InvoicePersistencePort.existsOverdueByCustomerId`; `ErrorCode.kt:51` | 단위 테스트 있음 (`OrderCommandServiceTest.kt:136-145`); 통합 `AutoChargeSagaIntegrationTest.kt` |
| S-3 | 빌링 전제조건 검사는 멱등 예약 슬롯을 소비하기 전에 수행한다 (교정 가능한 거부가 PENDING 슬롯을 남기지 않도록) | `OrderCommandService.kt:53-70` (검사 순서) | 미확인 (순서를 검증하는 테스트를 찾지 못함) |
| S-4 | ISSUED 인보이스는 발행 후 72h(설정값) 경과 시 OVERDUE 로 확정된다. 갱신은 `status = ISSUED` 조건부 UPDATE 로만 하여 동시에 PAID 된 행을 덮어쓰지 않는다 | `carry-payment/src/main/kotlin/com/carry/payment/application/service/OverdueSweeper.kt:26-37`; `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/repository/InvoiceJpaRepository.kt:20-29` | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/application/service/OverdueSweeperTest.kt`) |
| S-5 | 과금 실패는 FAILED + nextRetryAt 예약으로 남고 `ChargeRetrySweeper` 가 백오프로 재과금한다. 매 시도마다 그 시점의 활성 빌링키를 다시 조회한다 | `carry-payment/src/main/kotlin/com/carry/payment/application/service/ChargeRetrySweeper.kt:27-30`; `AutoChargeService.kt:79-88, 140-159` | 단위 테스트 있음 (`ChargeRetrySweeperTest.kt`, `AutoChargeServiceTest.kt`) |
| S-6 | `PaymentFailedEvent` 는 최초 실패 1회만 발행한다 (재시도 실패는 이벤트 없음) | `AutoChargeService.kt:149-156` (`isFirstAttempt`) | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/application/service/AutoChargeServiceTest.kt:125-130`) |
| S-7 | 취소·종결된 주문에 인보이스를 발행하지 않는다 (`isInvoiceable` = 주문 존재 && `isForwardActive`) | `carry-payment/src/main/kotlin/com/carry/payment/application/service/PaymentSagaHandler.kt:33-40`; `carry-app/src/main/kotlin/com/carry/app/adapter/OrderStateQueryPortAdapter.kt:18-25` | 단위 테스트 있음 (`carry-payment/src/test/kotlin/com/carry/payment/application/service/PaymentSagaHandlerTest.kt:62-72`, `isInvoiceable` true/false 양쪽) |
| S-8 | forward 진행이 끝난 주문(COMPLETED/CANCELLED)에 늦게 도착한 forward 이벤트는 멱등 no-op 으로 흡수한다. 반대로 "이른" 이벤트는 도메인 가드가 throw 해 Kafka 재시도에 맡긴다 | `OrderSagaHandler.kt:101-114` (`skipIfForwardStopped`) | 단위 테스트 있음 (`carry-order/src/test/kotlin/com/carry/order/application/service/OrderSagaHandlerTest.kt`, `InvalidOrderStatusTransitionException` 케이스 포함) |
| S-9 | `OrderCancelledEvent` 하나가 Dispatch·Delivery 취소 캐스케이드와 결제 보상을 함께 트리거한다. 각 핸들러는 `canTransitionTo(CANCELLED)` 가드를 통과할 때만 전이하고 아니면 조용히 skip 한다 | `carry-dispatch/.../application/service/DispatchSagaHandler.kt:41-58`, `carry-delivery/.../application/service/DeliverySagaHandler.kt:39-49`, `PaymentSagaHandler.kt:45-67` | 단위 테스트 있음 (`DispatchSagaHandlerTest`, `DeliverySagaHandlerTest`, `PaymentSagaHandlerTest`); 통합 `AutoChargeSagaIntegrationTest.kt` |
| S-10 | 결제 보상: COMPLETED 결제가 있으면 REFUND_PENDING 으로만 표시하고 PG 호출은 `RefundRetrySweeper` 가 한다. 미과금 인보이스(ISSUED/OVERDUE)는 CANCELLED 로 전환한다. 대상이 없으면 throw 하지 않는다 (DLQ 회피) | `PaymentSagaHandler.kt:45-67`; `carry-payment/.../application/service/RefundRetrySweeper.kt:31-33` | 단위 테스트 있음 (`PaymentSagaHandlerTest.kt`) |
| S-11 | 환불 확정(REFUNDED)과 원장 역분개, `RefundCompletedEvent` 발행은 같은 트랜잭션에서 일어난다. PG 취소 멱등키는 `refund-{paymentId}` 로 결정적이다 | `PaymentCommandService.kt:60-62, 83-110` | 단위 테스트 있음 (`PaymentCommandServiceTest.kt`); 통합 `SettlementLedgerIntegrationTest.kt` |
| S-12 | PENDING 배차는 수거 희망 30분 전까지 수락되지 않으면 TIMEOUT 되고, `DispatchTimeoutEvent` 로 주문이 SYSTEM 취소된다 (취소 가능 상태일 때만) | `carry-dispatch/.../application/service/DispatchTimeoutSweeper.kt:27-30`; `DispatchCommandService.kt:160-181`; `OrderSagaHandler.kt:43-52` | 단위 테스트 있음 (`DispatchTimeoutSweeperTest.kt`, `OrderSagaHandlerTest.kt`) |
| S-13 | 이벤트 소비는 `(consumerGroup, eventId)` 단위로 정확히 1회 처리된다. 처리 실패 시 claim 행도 롤백돼 at-least-once 재처리가 가능하다 | `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt:20-52`; `ProcessedEventRepository.kt:22-28` (`ON CONFLICT DO NOTHING`) | 단위 테스트 있음 (`carry-infra-kafka/src/test/.../EventConsumerSupportTest.kt`); 통합 `carry-app/src/test/kotlin/com/carry/app/idempotency/ConsumerIdempotencyIntegrationTest.kt` |
| S-14 | 스위퍼는 모두 `@Scheduled` + `@SchedulerLock` 으로 다중 인스턴스에서 한 노드만 실행한다 | `DispatchTimeoutSweeper.kt:27-28`, `OverdueSweeper.kt:30-31`, `ChargeRetrySweeper.kt:27-28`, `RefundRetrySweeper.kt:31-32` | 미확인 (락 동작 자체를 검증하는 테스트를 찾지 못함) |
| S-15 | 물리 사가가 24h(설정값) 이상 비종결 상태에 머물면 `StuckSagaDetector` 가 메트릭·경고만 남긴다. 자동 취소는 하지 않는다 | `carry-order/src/main/kotlin/com/carry/order/application/service/StuckSagaDetector.kt:35-47` | 단위 테스트 있음 (`carry-order/src/test/kotlin/com/carry/order/application/service/StuckSagaDetectorTest.kt`, 케이스 내용은 미열람) |
| S-16 | 도메인 패키지는 Spring·JPA·application·adapter 에 의존하지 않는다 | ArchUnit `carry-order/src/test/kotlin/com/carry/order/architecture/HexagonalArchitectureTest.kt:12-45` (다른 9개 모듈에 동일 파일 존재) | 아키텍처 테스트 있음 |

---

## 6. 카탈로그가 드러낸 갭

코드를 읽으면서 확인한 것 중, 문서·주석과 코드가 어긋나거나 한쪽에만 있는 항목이다. 발견한 사실을 적어 두는 것이 목적이며, 각 항목을 고칠지는 별도로 판단한다.

### 6.1 도메인에는 있는데 프로덕션 경로가 쓰지 않는 규칙

1. `Invoice.markOverdue()` (`Invoice.kt:68-70`) 는 main 코드 어디서도 호출되지 않는다. 실제 OVERDUE 확정은 `InvoiceJpaRepository.markOverdueIfIssued` 의 조건부 UPDATE (`InvoiceJpaRepository.kt:24-29`) 가 애그리거트를 우회해서 수행한다. 동시성 이유는 주석에 있으나, 결과적으로 ISSUED → OVERDUE 전이 규칙이 enum 과 JPQL 두 곳에 존재한다.
2. `Dispatch.isExpired(now)` (`Dispatch.kt:134-136`) 도 main 코드에서 호출되지 않는다. 스위퍼는 `DispatchJpaRepository.kt:49` 의 SQL(`INTERVAL '30 minutes'`) 로 만료 건을 조회한다. 30분이라는 정책값이 도메인과 SQL 에 중복돼 있어 한쪽만 바뀌면 어긋난다.
3. `InvoiceAlreadyPaidException`, `PaymentAlreadyCompletedException` 이 `Invoice.kt:5`, `Payment.kt:5` 에 import 돼 있으나 두 파일 모두 사용하지 않는다. 실제 전이 위반은 모두 `checkState` 의 일반 `BusinessException(CONFLICT)` 로 나간다. Order/Dispatch/Delivery 는 전용 예외 클래스를 쓰는 것과 대비된다.

### 6.2 코드가 강제하지 않고 관례·주석에만 있는 규칙

4. 원장 append-only (L-R4): `LedgerPort` 주석과 어댑터가 `saveAll` 만 호출한다는 사실에 의존한다. `LedgerEntryJpaRepository` 는 `JpaRepository` 를 상속하므로 `delete*`/`save` 가 열려 있고, `V25` 마이그레이션에는 UPDATE/DELETE 를 막는 트리거나 권한 제한이 없다.
5. `DeliveryStep.complete` (V-R1) 에 상태 가드가 없다. `Delivery` 의 멱등 조기 반환이 유일한 보호막이며, `DeliveryStep` 단독 테스트도 없다.
6. `Dispatch.create`, `Delivery.create`, `PenaltyRecord.create` 에는 `requireInput` 이 하나도 없다 (예: `desiredPickupAt` 이 과거인지, id 가 양수인지). 상위 계층(DTO 검증)에서 걸러지는지는 본 문서 작성 시 확인하지 않았다 (미확인).
7. `LedgerEntries.balanced` (L-R1) 는 Kotlin 표준 `require` 를 쓴다. `DomainValidation.kt:3-14` 의 설명대로라면 이 예외는 500 으로 매핑된다. 내부 일관성 위반이므로 500 이 의도일 수 있으나, 다른 도메인 검증과 다른 헬퍼를 쓴다는 점은 기록해 둔다.

### 6.3 테스트가 없는 불변식

8. `OrderShippingAddress` 의 공백 검증 4건 (O-C4): 해당 메시지나 공백 입력 케이스를 테스트에서 찾지 못했다.
9. `CarrierArea.create` 의 `areaCode` 공백 검증 (D-C3): `CarrierArea.create` 를 호출하는 테스트가 없다.
10. `uq_billing_keys_active_per_customer` 부분 유니크 인덱스 (B-R1): 서비스 로직은 테스트가 있으나 인덱스가 실제로 두 번째 ACTIVE 행을 거부하는지 확인하는 테스트는 없다.
11. `PaymentTest.kt:101` 의 테스트명은 "FAILED 상태에서 다시 PENDING 으로 전이할 수 없다" 인데, `PaymentStatus.canTransitionTo` (`PaymentEnums.kt:24`) 는 FAILED → PENDING 을 허용하고 `markRetrying` 이 그 경로다. 테스트 본문은 `markCompleted` 를 검사하므로 동작은 맞고 이름만 오래됐다.

### 6.4 문서와 코드의 불일치

12. `docs/06-saga.md:256` 은 배차 대기 한도를 "정책값" 이라고 적었으나, 코드에서는 30분이 `Dispatch.kt:136` 과 `DispatchJpaRepository.kt:49` 에 하드코딩돼 있다. 설정 프로퍼티로 뺀 것은 스윕 주기(`carry.dispatch.timeout-sweep-interval-ms`)뿐이다.
13. `docs/03-architecture.md:91` 의 모듈 레이아웃은 `domain/event/` (모듈 내부 도메인 이벤트) 디렉터리를 보여주지만, 어떤 모듈에도 그런 패키지가 없다. 도메인 이벤트 계층이 없다는 결정은 [ADR-0008](adr/0008-no-domain-event-layer.md) 에 정리했다.
14. `docs/05-cdc-outbox.md:209-210` 이 인용하는 `carry-event/.../DomainEvent.kt` 는 현재 저장소에 없다. ADR-0005:61 은 이를 "사용되지 않는 잔재" 로 기록했는데, 지금은 파일 자체가 제거된 상태라 문서 인용이 남아 있다.
15. `Order.cancel` 은 `canTransitionTo` 를 거치지 않고 `isCancellableBy` 만 본다 (`Order.kt:113-119`). 현재는 두 표가 일치하지만(비종결 상태 전부 → CANCELLED 허용), `OrderStatusTest.kt:68` 이 이 일치를 간접적으로만 확인한다.

---

## 참조

- [06. Saga 설계](06-saga.md)
- [ADR-0001 도메인 모델과 JPA 엔티티 분리](adr/0001-domain-jpa-separation.md)
- [ADR-0004 Choreography Saga](adr/0004-choreography-saga.md)
- [ADR-0008 도메인 이벤트 계층을 두지 않는다](adr/0008-no-domain-event-layer.md)
