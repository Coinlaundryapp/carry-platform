# 빌링키 자동과금 — 결제·물리 흐름 완전 분리 설계

> 작성일: 2026-07-12
> 상태: 설계 승인 (구현 전)
> 범위: carry-platform 백엔드 전체 (프론트는 후속 사이클)

---

## 1. 배경과 문제

현행 주문 사가는 결제를 물리 흐름의 게이트로 사용한다: `PICKED_UP → INVOICED → PAID → IN_PROGRESS`. 고객이 결제해야 세탁이 시작되므로, 캐리어는 수거한 세탁물을 든 채 고객의 결제 액션을 기다린다. `PAYMENT_FAILED` 이후 24h 초과 시 시스템이 주문을 취소하는데, 이 시점엔 세탁물이 이미 수거된 상태라 "빨지 않은 빨래를 되돌려주는 배차"가 필요해진다.

근본 원인: **비동기적 인간 행동(결제)이 물리 물류의 크리티컬 패스에 게이트로 박혀 있다.** 기획 의도였던 후불 원칙(무게 실측 후 과금)은 유지하되, 게이트를 제거한다.

해법: **과금 시점은 늦추고(수거 후 실측 무게로), 지불수단 확보는 앞당기고(주문 생성 전제조건), 물리 흐름에는 게이트를 두지 않는다.** 지불수단은 토스페이먼츠 자동결제(빌링키) 모델을 따르되, 실 PG 연동은 구조적 보류 상태이므로 mock 어댑터로 구현한다.

## 2. 새 시나리오 (end-to-end)

1. **결제수단 등록 (최초 1회)** — 고객이 카드 등록. 백엔드는 PG로부터 `billingKey`를 발급받아 암호화 저장. 등록 시 결제는 발생하지 않는다.
2. **주문 생성** — 전제조건 검증: ⓐ 활성 빌링키 존재, ⓑ 연체(OVERDUE) 인보이스 없음. 미충족 시 주문 생성 거부. 이로써 불변식 성립: **존재하는 주문은 결제 때문에 멈추지 않는다.**
3. **물리 흐름** — 배차 → 수거(무게 실측) → 세탁 → 반납. 어떤 단계도 결제 결과를 기다리지 않는다.
4. **결제 흐름 (병렬 사가)** — 수거 완료 시 실측 무게로 인보이스 발행(현행 유지) → 발행 이벤트를 결제 모듈이 소비해 빌링키 자동과금 → 성공 시 원장 기입.
5. **과금 실패** — 재시도 스위퍼가 백오프로 재과금. 인보이스 발행 72h 초과 시 OVERDUE 마킹(미수금 확정) → 신규 주문만 차단. 고객이 카드를 재등록하면 다음 스윕에서 재과금되고, 성공 시 차단은 자연 해제된다. 물리 흐름·기존 주문에는 어떤 영향도 없다.

## 3. 상태 기계 재설계

### 3.1 OrderStatus — 물리 사실만 (11개 → 6개)

```
CREATED → DISPATCHED → PICKED_UP → IN_PROGRESS → COMPLETED
CREATED / DISPATCHED           → CANCELLED  (고객·코디네이터·시스템)
PICKED_UP / IN_PROGRESS        → CANCELLED  (코디네이터·시스템 전용)
```

- 삭제되는 상태: `INVOICED`, `PAID`, `PAYMENT_FAILED`, `REFUND_PENDING`, `REFUNDED`.
- 고객 self-cancel은 현행대로 수거 전(CREATED/DISPATCHED)만 허용.
- `IN_PROGRESS` 전이는 결제 이벤트가 아니라 물리 행동(수거 완료 이후 delivery 흐름의 세탁 시작)이 트리거한다.
- `isForwardActive()` 등 상태 술어는 축소된 상태 집합에 맞게 재정의한다.

### 3.2 InvoiceStatus — 결제 모듈 내부

```
ISSUED → PAID                    (자동과금 성공)
ISSUED → OVERDUE → PAID          (연체 후 재과금 성공)
ISSUED / OVERDUE → CANCELLED     (미과금 상태에서 주문 취소)
PAID → REFUNDED                  (과금 후 주문 취소 보상)
```

- `OVERDUE` 신설: 발행 후 72h(설정값) 미결제. 의미는 "미수금 확정 + 해당 고객 신규 주문 차단"이며, 과금 재시도는 계속된다.
- `PaymentStatus`(PENDING/COMPLETED/FAILED/REFUND_PENDING/REFUNDED)는 유지 — 인보이스당 Payment 1행, 재시도는 동일 행의 FAILED → PENDING 재전이(현행 전이 규칙에 이미 존재).

## 4. 빌링키 도메인 (carry-payment)

### 4.1 저장 모델

새 테이블 `customer_billing_keys`:

| 컬럼 | 설명 |
|---|---|
| id | PK |
| customer_id | 고객 (활성 키는 고객당 1개 — 부분 유니크 인덱스) |
| customer_key | PG에 전달하는 고객 식별자. 추측 불가능한 랜덤(UUID). DB id 재사용 금지 |
| billing_key | 암호화 저장(AES-GCM, 키는 환경변수). 유출 시 임의 과금 가능한 크리덴셜 |
| card_company / card_last4 | 표시용 메타데이터 |
| status | ACTIVE / INVALID |
| created_at / invalidated_at | |

재등록 시 기존 ACTIVE 키를 INVALID로 전환 후 새 키 저장(같은 트랜잭션).

### 4.2 API

- `POST /api/billing-keys` — 요청: `{ authKey }` (프론트 SDK 카드 등록창 결과. mock에선 임의 문자열). 처리: `customerKey` 생성(최초) → PG `issueBillingKey` → 저장. 응답: 카드사·마지막 4자리.
- `GET /api/billing-keys/me` — 활성 키의 마스킹 정보 조회 (없으면 404).
- 삭제 API는 제외(YAGNI) — 재등록이 교체를 겸한다.

### 4.3 주문 생성 전제조건 (carry-order)

- 신규 아웃바운드 포트 `BillingQueryPort` (order → payment 방향, 기존 `OrderStateQueryPort`의 역방향 대칭): `hasActiveBillingKey(customerId)`, `hasOverdueInvoice(customerId)`.
- carry-app에서 payment 모듈의 인바운드 유스케이스로 위임하는 어댑터로 배선.
- 실패 시 주문 생성 API가 409 + 사유 코드(빌링키 없음 / 연체 존재)로 응답.

## 5. 자동과금 사가 (carry-payment)

### 5.1 과금 흐름

1. `PickupCompletedEvent` → 인보이스 발행 (현행 `InvoiceService.createInvoiceFromPickup` 유지, `isInvoiceable` 가드 포함).
2. `InvoiceIssuedEvent`를 **payment 모듈 자신이 소비** (기존 outbox → Kafka → consumer 인프라와 멱등 처리 재사용).
3. `AutoChargeService`: 활성 빌링키 조회 → `PaymentGatewayPort.chargeBilling(billingKey, customerKey, amount, idempotencyKey = "charge-{invoiceId}")`.
4. 성공: Payment COMPLETED + 인보이스 PAID + 원장 기입(현행 분배 로직 재사용: 세탁비·배달비 → CARRIER, 수수료 → PLATFORM) + `PaymentCompletedEvent` 발행.
5. 실패: Payment FAILED + `PaymentFailedEvent` 발행. **주문 모듈은 이 이벤트를 더 이상 소비하지 않는다.** notification만 소비해 "카드 확인/재등록 안내" 발송.
6. 활성 빌링키가 없는 경우(이론상 주문 전제조건으로 차단되지만, 재등록 전 INVALID 처리 직후 등 틈새): 과금 시도 없이 Payment FAILED와 동일 경로.

### 5.2 재시도 — ChargeRetrySweeper

- 기존 `RefundRetrySweeper`와 대칭. `@Scheduled`로 FAILED Payment 중 `next_retry_at <= now`인 건을 재과금.
- 백오프: 1h → 4h → 12h → 24h → 이후 일 1회, 상한 없음(주문 취소 없음). 값은 설정으로 외부화.
- 매 시도는 그 시점의 **활성** 빌링키를 다시 조회 — 카드 재등록이 자연스럽게 복구 경로가 된다.
- 멱등키는 `charge-{invoiceId}`로 고정 — "PG 성공·로컬 마킹 실패" 후 재시도가 이중과금이 되지 않도록 PG가 dedup.

### 5.3 연체 — OverdueSweeper

- 발행 후 72h 경과한 미결제(ISSUED) 인보이스를 OVERDUE로 마킹. 상태 가드로 멱등.
- OVERDUE는 신규 주문 차단에만 사용. 이후 과금 성공 시 OVERDUE → PAID.

### 5.4 제거되는 것

- `OrderSagaHandler`의 결제 이벤트 핸들러 일체 (onInvoiceIssued / onPaymentCompleted / onPaymentFailed).
- `PaymentRetryDeadlineSweeper` (24h 미결제 → 주문 시스템 취소) — 미결제는 더 이상 주문을 죽이지 않는다.
- delivery 모듈이 세탁 시작을 `PaymentCompletedEvent`로 게이트하고 있다면 해당 핸들러 제거, 세탁 시작을 수거 완료 이후 캐리어 행동으로 연결. **(플랜 단계 확인 항목 §9-1)**

## 6. 취소·환불

- **수거 전 취소 (CREATED/DISPATCHED)**: 인보이스 없음. 현행 캐스케이드(dispatch/delivery 취소) 유지, payment는 skip.
- **수거 후 취소 (코디/시스템 전용)**: 주문은 바로 CANCELLED (REFUND_PENDING을 거치지 않음 — 환불 진행 상태는 결제 모듈 소관). payment 모듈 `onOrderCancelled` 분기:
  - Payment COMPLETED → 환불 대기 마킹 → 기존 `RefundRetrySweeper`가 PG 환불 (현행 유지).
  - 인보이스 존재·미과금(ISSUED/OVERDUE) → 인보이스 CANCELLED, FAILED Payment의 재시도 대상 제외.
  - 인보이스 없음 → skip.
- 환불 완료 알림은 notification이 `RefundCompletedEvent`로 처리. 주문 상태는 이미 CANCELLED라 추가 전이 없음.

## 7. PG 포트 확장 + mock 어댑터

### 7.1 PaymentGatewayPort 변경

- **제거**: `requestPayment(PgPaymentRequest)` — paymentKey(위젯) 기반 수동 결제 경로 폐기. `PaymentController`의 위젯 승인 엔드포인트도 함께 제거.
- **추가**:
  - `issueBillingKey(authKey, customerKey): PgBillingKeyResult` — 성공 시 billingKey·카드 메타 반환.
  - `chargeBilling(billingKey, customerKey, amount, orderName, idempotencyKey): PgPaymentResult`.
- **유지**: `cancelPayment`(환불), `listTransactions`(대사). 빌링 과금도 CHARGE 레코드로 기록되어 기존 reconciliation 잡이 무변경으로 동작.

### 7.2 Mock 어댑터

- 가짜 billingKey 발급(랜덤), 발급·과금·취소 거래를 인메모리/테이블에 기록해 `listTransactions` 지원.
- 실패 시뮬레이션: 테스트 전용 마커(예: 과금 금액 끝자리 규칙 또는 특수 authKey 접두사)로 결정적 실패 유도 — 통합 테스트의 실패 경로 검증에 사용.
- 실 토스 자동결제 API(`POST /v1/billing/authorizations/issue`, `POST /v1/billing/{billingKey}`)와 시그니처를 맞춰, 실 어댑터 교체 시 포트 무변경.

## 8. 마이그레이션·테스트

### 8.1 Flyway

- V-next 1: `customer_billing_keys` 생성.
- V-next 2: 인보이스 상태 CHECK/enum에 OVERDUE 추가(스키마 표현에 따름), Payment 재시도 컬럼(`next_retry_at`, `retry_count`) 추가.
- V-next 3: 기존 주문 status 매핑 — `INVOICED → PICKED_UP`, `PAID → IN_PROGRESS`, `PAYMENT_FAILED → PICKED_UP`, `REFUND_PENDING/REFUNDED → CANCELLED`. 실데이터 없는 학습 프로젝트라 dev 데이터만 해당.

### 8.2 테스트 (검증은 JUnit XML 기준)

- 단위: 축소된 `OrderStatus` 전이 전수, `InvoiceStatus`(OVERDUE 포함) 전이 전수, 빌링키 등록/재등록(기존 키 INVALID), `AutoChargeService` 성공/실패/중복 이벤트 멱등, 스위퍼 백오프 계산.
- 통합 (기존 saga 통합 테스트 패턴 재사용):
  - ⓐ 해피 패스: 주문이 결제 이벤트 소비 없이 COMPLETED 도달 + 인보이스 PAID + 원장 분배 정확.
  - ⓑ 실패 경로: 과금 실패에도 주문 COMPLETED → 72h 경과 시 OVERDUE → 해당 고객 신규 주문 409 → 카드 재등록 → 스윕 재과금 성공 → PAID + 신규 주문 허용.
  - ⓒ 수거 후 취소: 과금 완료 건은 환불 경로, 미과금 건은 인보이스 CANCELLED + 재시도 중단.
- 기존 테스트 갱신: 주문 상태 축소·핸들러 제거로 깨지는 테스트 전면 정리(삭제가 아니라 새 시나리오로 재작성).

## 9. 플랜 단계 확인 항목

1. delivery 모듈의 세탁 시작(LaundryStarted) 트리거가 `PaymentCompletedEvent`를 게이트로 쓰는지 코드로 확인하고, 사용 시 제거·재배선 방식 확정.
2. `PaymentController`가 노출 중인 엔드포인트 전수 조사 — 위젯 승인 외 유지 대상(인보이스 조회 등) 분리.
3. notification 모듈이 소비하는 결제 이벤트 목록과 새 알림(카드 재등록 안내) 템플릿 위치.
4. openapi 스펙 재생성 범위(프론트 후속 사이클 대비 계약 확정).

## 10. 의도적 제외 (재제안 금지 아님 — 후속 후보)

- 실 토스페이먼츠 연동(자동결제 별도 계약 필요, 구조적 보류 유지).
- 프론트 3앱 변경 일체(후속 사이클: 퍼널 등록 스텝, 위젯 결제 화면 제거, 상태 표시 분리, 재등록 안내).
- 미수금의 발생주의 원장(A/R 계정) — 이번엔 인보이스 OVERDUE 플래그로만 추적.
- 그룹 세탁(알뜰/팀) 관련 일체 — 단독(SOLO) 전용 결정 유지.
