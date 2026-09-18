# 17. 유비쿼터스 언어 사전 (Ubiquitous Language)

> 최종 수정일: 2026-09-08
> 상태: Draft
> 근거: 각 도메인 모듈의 `domain/model`, `domain/vo`, `application/port`, `carry-event`, `carry-infra-kafka`, [01-overview](01-overview.md), [05-cdc-outbox](05-cdc-outbox.md), [06-saga](06-saga.md), [16-context-map](16-context-map.md)

---

## 1. 원칙

- 정의는 코드(클래스명·열거값·메서드명·KDoc)와 설계 문서에서만 도출했다. 코드에 의미가 드러나지 않으면 "미확인"으로 표시한다.
- "소속 컨텍스트"는 그 용어의 의미를 결정하는 권한을 가진 모듈이다. 다른 모듈에서 같은 단어를 쓰더라도 의미는 소속 컨텍스트의 정의를 따른다.
- 같은 개념에 이름이 둘이거나 같은 이름에 뜻이 둘인 경우는 본문에 표기하고 3절 "불일치"에 모았다.

---

## 2. 용어 사전

### 2.1 행위자 (Actors)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 고객 | `UserRole.CUSTOMER`, `customerId` | 세탁물을 맡기고 대금을 지불하는 주문의 주체 | User | `carry-user/.../domain/vo/UserRole.kt`, `carry-order/.../domain/model/Order.kt` |
| 캐리어 | `UserRole.CARRIER`, `carrierId`, `LedgerAccountType.CARRIER` | 배차를 받아 수거·세탁·건조·배달 작업을 직접 수행하는 사람. 코인세탁소 기계에 현금을 투입하므로 세탁비의 수취 계정이기도 하다 | User (역할), Dispatch·Delivery (작업 주체), Payment (정산 계정) | `carry-user/.../domain/vo/UserRole.kt`, `carry-payment/.../domain/vo/PaymentEnums.kt` (`LedgerAccountType` KDoc) |
| 코디네이터 | `UserRole.COORDINATOR`, `CancelledBy.COORDINATOR`, `AssignedBy.COORDINATOR` | 운영 권한을 가진 내부 사용자. 배차를 캐리어에게 지정하고, 수거 후 주문을 취소할 수 있으며, 소유자 검증 없이 주문을 조회한다 | User (역할), Order·Dispatch (권한 분기) | `carry-order/.../domain/vo/OrderEnums.kt` (`isCancellableBy`), `carry-dispatch/.../domain/vo/DispatchEnums.kt`, `carry-order/.../application/port/inbound/OrderQueryUseCase.kt` |
| 관리자 | `UserRole.ADMIN` | 역할 열거값으로 존재. 코디네이터와의 권한 차이는 코드에서 미확인 | User | `carry-user/.../domain/vo/UserRole.kt` |
| 시스템 | `CancelledBy.SYSTEM` | 사람이 아닌 스위퍼·사가 핸들러가 행위자일 때의 표기 | Order | `carry-order/.../domain/vo/OrderEnums.kt` |
| 플랫폼 | `LedgerAccountType.PLATFORM` | 서비스 수수료를 수취하는 단일 정산 주체. accountId 가 null 이다 | Payment | `carry-payment/.../domain/vo/PaymentEnums.kt`, `domain/model/LedgerEntry.kt` |
| 세탁소 | `Laundromat`, `laundromatId`, `LaundromatOption` | 세탁이 이루어지는 코인세탁소. 이름·주소·위치·보유 장비(세탁기/건조기/운동화)와 사진을 가진다. 정산의 수취인이 아니다 | Laundromat | `carry-laundromat/.../domain/model/Laundromat.kt`, `domain/vo/LaundromatOption.kt`, `carry-payment/.../domain/vo/PaymentEnums.kt` (정산 제외 근거) |

### 2.2 주문 (Order)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 주문 | `Order`, `OrderStatus` | 고객이 특정 세탁소·세탁물 종류·옵션·배송지·희망 수거/배달 시각으로 만든 요청. 상태는 물리 세계의 사실(CREATED→DISPATCHED→PICKED_UP→IN_PROGRESS→COMPLETED / CANCELLED)만 기술하고 결제 상태를 포함하지 않는다 | Order | `carry-order/.../domain/model/Order.kt`, `domain/vo/OrderEnums.kt` (KDoc "물리 세계의 사실만 기술") |
| 주문 생성 전제조건 | `BillingQueryPort.hasActiveBillingKey`, `hasOverdueInvoice`, `ErrorCode.BILLING_KEY_REQUIRED`, `OVERDUE_INVOICE_EXISTS` | 활성 빌링키가 있고 연체 인보이스가 없어야 주문을 만들 수 있다는 규칙 | Order (규칙), Payment (판정) | `carry-order/.../application/port/outbound/BillingQueryPort.kt`, `carry-common/.../exception/ErrorCode.kt` |
| 세탁물 종류 | `LaundryItemType {REGULAR, BLANKET, SHOES, REGULAR_AND_BLANKET}` (price), `Order.laundryItemType: String` (order) | 주문 대상 세탁물의 분류 | Price (열거형 소유), Order (문자열로 보관) | `carry-price/.../domain/vo/PriceEnums.kt`, `carry-order/.../domain/model/Order.kt` |
| 옵션 | `SelectedOption(optionType, subOptionType)` (order), `OptionType {WASH, DRY, ADDITIONAL}` + `SubOptionType {STANDARD, HOT_WATER, LOW_HEAT, HIGH_HEAT, FOLD_LAUNDRY, ADD_SOFTENER}` (price), `OptionPrice` (price), `SelectedOptionDto` (event.order), `SelectedOptionSnapshot` (event.delivery) | 세탁·건조·부가 작업에 대한 고객 선택. 유형과 세부유형의 쌍이며 가격 정책의 단위다 | Price (어휘 소유), Order (선택 보관) | `carry-price/.../domain/vo/PriceEnums.kt`, `domain/vo/OptionPrice.kt`, `carry-order/.../domain/vo/SelectedOption.kt`, `carry-event/.../order/OrderEvents.kt`, `carry-event/.../delivery/DeliveryEvents.kt` |
| 세탁소 옵션 | `LaundromatOption {WASHING_MACHINE, DRYER, SNEAKERS}` | 세탁소가 보유한 장비 종류. 주문 옵션과 이름이 같지만 다른 개념이다 | Laundromat | `carry-laundromat/.../domain/vo/LaundromatOption.kt` |
| 가격 정책 | `PricePolicy`, `PriceCondition(orderUnitType, orderRequestType, laundryItemType)`, `calculateTotal` | 주문 조건 조합별로 옵션 가격을 정의하고 선택 옵션의 합계를 계산하는 정책 | Price | `carry-price/.../domain/model/PricePolicy.kt`, `domain/vo/PriceCondition.kt` |
| 주문 단위 / 요청 유형 | `OrderUnitType {SOLO}`, `OrderRequestType {NEW, REORDER}` | 가격 조건의 축. SOLO 외 값은 없고, REORDER 의 의미(재주문 정의)는 코드에서 미확인 | Price | `carry-price/.../domain/vo/PriceEnums.kt` |
| 배송지 | `ShippingAddress` (user), `OrderShippingAddress` (order), `ShippingAddressDto` (event) | 수거·배달이 이루어지는 주소·좌표·수령인·출입 정보·권역 코드. 주문 생성 시 User 의 배송지를 Order 가 스냅샷으로 복사한다 | User (원본), Order (스냅샷) | `carry-user/.../domain/model/ShippingAddress.kt`, `carry-order/.../domain/vo/OrderShippingAddress.kt`, `carry-app/.../adapter/UserQueryPortAdapter.kt` |
| 취소 | `Order.cancel`, `OrderCancellation(reason, by, at)`, `CancelledBy`, `OrderCancelledEvent` | 사유·주체·시각이 항상 함께 정해지는 하나의 사실. 고객은 수거 전(CREATED/DISPATCHED)만, 코디네이터·시스템은 완료 전까지 취소할 수 있다 | Order | `carry-order/.../domain/vo/OrderCancellation.kt`, `domain/vo/OrderEnums.kt` |
| 실측 무게 | `Order.actualWeight`, `Delivery.completePickup(weight)`, `PickupCompletedEvent.actualWeight` | 수거 시 캐리어가 계량한 세탁물 무게. 인보이스 금액의 기준이 된다 | Delivery (확정), Order·Payment (소비) | `carry-delivery/.../domain/model/Delivery.kt`, `carry-payment/.../application/service/InvoiceService.kt` |

### 2.3 배차 (Dispatch)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 배차 | `Dispatch`, `DispatchStatus {PENDING, ASSIGNED, ACCEPTED, CANCELLED, TIMEOUT}` | 주문 하나에 캐리어 한 명을 연결하는 과정과 그 결과. `OrderCreatedEvent` 로 PENDING 상태로 생성된다 | Dispatch | `carry-dispatch/.../domain/model/Dispatch.kt`, `domain/vo/DispatchEnums.kt`, `application/service/DispatchSagaHandler.kt` |
| 선점 (클레임) | `Dispatch.claimByCarrier`, `DispatchCommandUseCase.claimDispatch`, `AssignedBy.CARRIER` | 캐리어가 PENDING 배차를 스스로 잡아 즉시 ACCEPTED 로 만드는 행위. 멱등 | Dispatch | `carry-dispatch/.../domain/model/Dispatch.kt` (KDoc "캐리어 본인이 PENDING 배차를 직접 선점") |
| 지정 (배정) | `Dispatch.assignByCoordinator`, `DispatchCommandUseCase.assignDispatch`, `AssignedBy.COORDINATOR`, `DispatchStatus.ASSIGNED`, `AuditAction.DISPATCH_ASSIGN` | 코디네이터가 PENDING 배차를 특정 캐리어에게 지정해 ASSIGNED 로 만드는 행위. 캐리어의 수락이 뒤따라야 한다 | Dispatch | `carry-dispatch/.../domain/model/Dispatch.kt`, `carry-audit/.../domain/AuditAction.kt` |
| 수락 | `Dispatch.acceptAssignment`, `DispatchAcceptedEvent` | ASSIGNED 배차를 캐리어가 받아들여 ACCEPTED 로 전이. 이 이벤트로 Order 가 DISPATCHED 가 되고 Delivery 가 생성된다 | Dispatch | `carry-dispatch/.../domain/model/Dispatch.kt`, `carry-event/.../dispatch/DispatchEvents.kt` |
| 거절 | `Dispatch.rejectAssignment` | ASSIGNED 배차를 캐리어가 거부해 PENDING 으로 되돌리는 행위. 페널티 기록이 생성된다 | Dispatch | `carry-dispatch/.../domain/model/Dispatch.kt` |
| 페널티 | `PenaltyRecord`, `PenaltyReason {REJECTED_FORCED_ASSIGNMENT}`, `AuditAction.DISPATCH_REJECT_PENALTY` | 코디네이터 지정 배차를 거절한 캐리어에게 남기는 기록. 현재 사유는 한 가지뿐이며 페널티가 이후 배차에 미치는 효과는 코드에서 미확인 | Dispatch | `carry-dispatch/.../domain/model/PenaltyRecord.kt`, `domain/vo/DispatchEnums.kt` |
| 타임아웃 | `Dispatch.timeout`, `DispatchStatus.TIMEOUT`, `DispatchTimeoutEvent`, `DispatchTimeoutSweeper` | 수거 시한이 임박하도록 아무도 잡지 않은 PENDING 배차를 스위퍼가 종결하는 것. Order 사가가 후속을 잇는다 | Dispatch | `carry-dispatch/.../application/service/DispatchTimeoutSweeper.kt` |
| 캐리어 권역 | `CarrierArea(carrierId, areaCode, areaName, active)` | 캐리어가 배차를 받을 수 있는 권역 등록. `areaCode` 는 ServiceAvailability 의 코드를 따른다 | Dispatch | `carry-dispatch/.../domain/model/CarrierArea.kt` |

### 2.4 배달 작업 (Delivery)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 배달 (작업) | `Delivery`, `DeliveryStatus {PICKUP_PENDING, PICKED_UP, IN_LAUNDRY, LAUNDRY_COMPLETE, DELIVERY_PENDING, DELIVERED, CANCELLED}` | 배차 수락 후 캐리어가 수행하는 물리 작업 전체의 진행 기록. 주문 하나에 하나 | Delivery | `carry-delivery/.../domain/model/Delivery.kt`, `domain/vo/DeliveryEnums.kt` |
| 작업 단계 | `DeliveryStep`, `DeliveryStepType {PICKUP, WEIGHING, WASHING, DRYING, DELIVERY}`, `StepStatus {PENDING, COMPLETED}` | 배달 작업을 구성하는 다섯 단계. 각 단계는 사진(mediaIds)과 완료 시각을 남긴다 | Delivery | `carry-delivery/.../domain/model/DeliveryStep.kt` |
| 수거 | `Delivery.completePickup`, `DeliveryStepType.PICKUP`, `PickupCompletedEvent`, `OrderStatus.PICKED_UP`, `desiredPickupAt` | 캐리어가 고객 배송지에서 세탁물을 받아 무게를 확정하는 단계. 이 이벤트로 인보이스가 발행된다 | Delivery | `carry-delivery/.../domain/model/Delivery.kt`, `carry-event/.../delivery/DeliveryEvents.kt` |
| 세탁 시작 | `Delivery.startWashing`, `LaundryStartedEvent`, `OrderStatus.IN_PROGRESS` | 캐리어가 세탁소에서 세탁을 시작했음을 기록. Order 는 IN_PROGRESS 로 전이 | Delivery | `carry-delivery/.../domain/model/Delivery.kt`, `carry-order/.../application/service/OrderSagaHandler.kt` |
| 반납 (배달 완료) | `Delivery.completeDelivery`, `DeliveryStatus.DELIVERED`, `DeliveryStepType.DELIVERY`, `DeliveryCompletedEvent`, `OrderStatus.COMPLETED`, `desiredDeliveryAt` | 세탁·건조가 끝난 세탁물을 고객에게 돌려주는 마지막 단계. 문서는 "반납", 코드는 "delivery/배달"로 부른다 | Delivery | `carry-delivery/.../domain/model/Delivery.kt`, [06-saga](06-saga.md) (“세탁 → 건조 → 반납”) |

### 2.5 결제 (Payment)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 인보이스 (청구서) | `Invoice`, `InvoiceStatus {ISSUED, PAID, OVERDUE, CANCELLED, REFUNDED}`, `InvoiceLineItem`, `ChargeType {LAUNDRY_PRICE, DELIVERY_FEE, SERVICE_FEE}`, `InvoiceIssuedEvent` | 수거 완료 시 실측 무게로 계산한 청구 내역. 세탁비·배달비·서비스 수수료 세 항목으로 구성 | Payment | `carry-payment/.../domain/model/Invoice.kt`, `domain/vo/InvoiceLineItem.kt`, `application/service/InvoiceService.kt` |
| 결제 | `Payment`, `PaymentStatus {PENDING, COMPLETED, FAILED, REFUND_PENDING, REFUNDED}`, `PaymentCompletedEvent`, `PaymentFailedEvent` | 인보이스 하나에 대한 PG 과금 시도와 그 결과. 실패 시 백오프 재시도를 예약한다 | Payment | `carry-payment/.../domain/model/Payment.kt` |
| 빌링키 | `BillingKey`, `BillingKeyStatus {ACTIVE, INVALID}`, `PaymentGatewayPort.issueBillingKey`, `AuditAction.BILLING_KEY_REGISTERED` | 고객의 자동결제 수단. 고객당 활성 키 1개, 재등록 시 기존 키는 INVALID. 영속화 시 암호화 | Payment | `carry-payment/.../domain/model/BillingKey.kt`, `domain/vo/PaymentEnums.kt` |
| 자동과금 | `AutoChargeService.chargeInvoice`, `retryCharge`, `PaymentGatewayPort.chargeBilling` | 사용자 액션 없이 서버가 빌링키로 인보이스 금액을 과금하는 것. `InvoiceIssuedEvent` 자체 소비로 시작한다 | Payment | `carry-payment/.../application/service/AutoChargeService.kt` |
| 재시도 (백오프) | `Payment.scheduleRetry`, `markRetrying`, `ChargeRetrySweeper` | 과금 실패 후 1h→4h→12h→24h→24h 고정 간격으로 재과금하는 절차. 매 시도마다 그 시점의 활성 빌링키를 다시 조회 | Payment | `carry-payment/.../domain/model/Payment.kt`, `application/service/ChargeRetrySweeper.kt` |
| 연체 | `InvoiceStatus.OVERDUE`, `Invoice.markOverdue`, `OverdueSweeper`, `ErrorCode.OVERDUE_INVOICE_EXISTS` | 발행 후 임계(기본 72h)가 지나도 결제되지 않은 인보이스를 미수금으로 확정한 상태. 효과는 해당 고객의 신규 주문 차단뿐이며 재과금 성공 시 PAID 로 회복된다 | Payment | `carry-payment/.../application/service/OverdueSweeper.kt`, `domain/vo/PaymentEnums.kt` |
| 환불 | `Payment.markRefundPending`, `markRefunded`, `PaymentGatewayPort.cancelPayment`, `RefundRetrySweeper`, `RefundCompletedEvent`, `AuditAction.PAYMENT_REFUND` | 완료된 결제를 PG 에서 취소하는 것. 의도 표시(REFUND_PENDING) 후 PG 취소 성공 시 REFUNDED 로 두 단계 | Payment | `carry-payment/.../domain/model/Payment.kt`, `application/service/RefundRetrySweeper.kt` |
| 정산 원장 | `LedgerEntry`, `LedgerPort.record/balance`, `LedgerEntryType {PAYMENT, REFUND}` | 돈이 움직인 사실을 부호 있는 금액으로 남기는 append-only 기록. 거래 그룹 내 금액 합은 0 | Payment | `carry-payment/.../domain/model/LedgerEntry.kt`, `application/port/outbound/LedgerPort.kt` |
| 계정 | `LedgerAccountType {CUSTOMER, CARRIER, PLATFORM}` | 원장의 세 주체. CUSTOMER 는 총액 차변, LAUNDRY_PRICE·DELIVERY_FEE 는 CARRIER, SERVICE_FEE 는 PLATFORM. 세탁소 계정은 없다 | Payment | `carry-payment/.../domain/vo/PaymentEnums.kt`, `domain/model/LedgerEntry.kt` |
| 역분개 | `LedgerEntries.forRefund`, `LedgerEntryType.REFUND` | 환불 시 PAYMENT 그룹과 부호만 반대인 행들을 추가로 기입하는 것. 원행은 수정하지 않는다 | Payment | `carry-payment/.../domain/model/LedgerEntry.kt` |
| 정산 | (전용 식별자 없음) `정산 원장`, `정산 계정` 표현만 존재 | 캐리어·플랫폼에게 실제로 지급하는 행위(Settlement)는 코드에 없다. 원장 잔액(`LedgerPort.balance`)까지만 구현. 미확인 | Payment | `carry-payment/.../domain/vo/PaymentEnums.kt` KDoc |
| 대사 | `PgReconciliationJob`, `ReconciliationMismatch`, `ReconciliationMismatchType {ORPHAN_PG_CHARGE, MISSING_IN_PG, AMOUNT_MISMATCH, MISSING_PG_CANCEL, PG_CANCEL_NOT_MARKED}`, `PaymentGatewayPort.listTransactions` | PG 측 거래 목록과 로컬 결제를 주기적으로 양방향 비교해 불일치를 비파괴로 기록하는 안전망. 자동 보정은 없다 | Payment | `carry-payment/.../application/service/PgReconciliationJob.kt`, `domain/model/ReconciliationMismatch.kt` |
| PG | `PgProvider {TOSS_PAYMENTS}`, `PaymentGatewayPort`, `PgProviderAdapter`, `StubPgProviderAdapter` | 외부 결제 대행사. 현재 실 어댑터는 없고 local 프로파일 스텁만 있다 | Payment | `carry-payment/.../application/port/outbound/PaymentGatewayPort.kt`, `adapter/outbound/stub/StubPgProviderAdapter.kt` |

### 2.6 서비스 가용성 (ServiceAvailability)

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 서비스 권역 | `ServiceArea(areaCode, name, status, schedules, holidays)`, `AreaStatus {ACTIVE, INACTIVE, SUSPENDED}` | 서비스를 제공하는 지역 단위. 권역 코드로 식별되며 상태·영업 스케줄·휴무일을 가진다 | ServiceAvailability | `carry-service-availability/.../domain/model/ServiceArea.kt`, `domain/vo/AreaStatus.kt` |
| 권역 코드 | `areaCode: String` (service-availability, user, order, event, dispatch) | 서비스 권역의 식별 문자열. 소유는 ServiceAvailability 이나 네 컨텍스트가 같은 문자열을 그대로 쓴다 | ServiceAvailability | [16-context-map](16-context-map.md) 5.4 |
| 영업 스케줄 | `OperatingSchedule(dayOfWeek, openTime, closeTime)`, `TimeSlot` | 권역의 요일별 운영 시간대. 수거·배달 희망 시각이 이 안에 들어야 한다 | ServiceAvailability | `carry-service-availability/.../domain/model/OperatingSchedule.kt`, `domain/vo/TimeSlot.kt` |
| 휴무일 | `HolidayOverride(date, reason)` | 스케줄과 무관하게 서비스하지 않는 날짜 | ServiceAvailability | `carry-service-availability/.../domain/model/HolidayOverride.kt` |
| 가용성 검사 | `ServiceArea.checkAvailability(requestedAt)`, `ServiceAvailabilityQueryPort.checkAvailability(areaCode, pickupAt, deliveryAt)` | 권역 상태·휴무·스케줄을 종합해 요청 시각에 서비스 가능한지 판정. 실패 시 예외 | ServiceAvailability | `carry-service-availability/.../domain/model/ServiceArea.kt`, `carry-order/.../application/port/outbound/ServiceAvailabilityQueryPort.kt` |

### 2.7 운영·리뷰·알림·미디어·위치

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 운영 이벤트 | `OperationEvent(eventType, aggregateType, aggregateId, summary)`, `OperationSagaHandler` | 운영자 타임라인용으로 주요 도메인 이벤트를 요약해 남긴 기록. 상류 이벤트명을 문자열로 보관 | Operation | `carry-operation/.../domain/model/OperationEvent.kt`, `application/service/OperationSagaHandler.kt` |
| 운영 요약 | `OperationSummary(totalOrdersToday, pendingDispatches, activeDeliveries, completedToday, cancelledToday)` | 운영 대시보드용 당일 집계 | Operation | `carry-operation/.../domain/model/OperationSummary.kt` |
| 약관 | `Term`, `TermType {SERVICE, PRIVACY, MARKETING, LOCATION}` | 버전과 필수 여부를 가진 이용약관 문서 | Operation | `carry-operation/.../domain/model/Term.kt`, `domain/vo/TermType.kt` |
| 리뷰 | `Review(laundromatId, customerId, rating, comment, mediaUrls)`, `ReviewRating {ONE..FIVE}`, `ReviewCreatedEvent` | 고객이 세탁소에 남기는 별점·코멘트·사진 | Review | `carry-review/.../domain/model/Review.kt`, `domain/vo/ReviewRating.kt` |
| 알림 | `Notification`, `NotificationType`, `NotificationChannel {KAKAO_ALARMTALK, SMS, PUSH}`, `NotificationStatus {PENDING, SENT, FAILED}`, `NotificationReference(type, id)` | 도메인 이벤트에 반응해 사용자에게 보내는 메시지. 유형은 상류 이벤트명과 1:1 | Notification | `carry-notification/.../domain/vo/NotificationEnums.kt`, `domain/model/Notification.kt` |
| 디바이스 토큰 | `DeviceToken`, `DevicePlatform` | 푸시 발송을 위한 단말 식별자 | Notification | `carry-notification/.../domain/model/DeviceToken.kt` |
| 미디어 리소스 | `MediaResource` (media), `MediaStatus {UPLOADING, COMPLETED, FAILED}`, `FileStoragePort` | 업로드된 파일과 그 상태. 세탁소 사진과 배달 단계 사진이 여기서 관리된다 | Media | `carry-media/.../domain/model/MediaResource.kt`, `domain/vo/MediaStatus.kt` |
| 지오코딩 / 역지오코딩 | `GeocodingPort.geocode`, `ReverseGeocodingPort.reverseGeocode`, `GeocodingResult`, `Coordinate` | 주소→좌표, 좌표→주소 변환. Naver API 를 어댑터로 감싼다 | Geo | `carry-geo/.../application/port/outbound/GeocodingPort.kt`, `ReverseGeocodingPort.kt` |

### 2.8 사가·메시징 인프라

| 한국어 용어 | 코드 식별자 | 정의 | 소속 컨텍스트 | 근거 파일 |
|---|---|---|---|---|
| 사가 | `*SagaHandler`, `*SagaEventHandler`, `SagaLogContext` | 중앙 오케스트레이터 없이 각 모듈이 이벤트를 발행·반응해 주문 프로세스를 진행하는 코레오그래피. 물리 흐름(Order lane)과 결제 흐름(Payment lane) 두 사가가 병렬이다 | 교차 (Order 가 물리 사가의 종결 지점, Payment 가 결제 사가 소유) | [06-saga](06-saga.md), `carry-order/.../application/service/OrderSagaHandler.kt`, `carry-payment/.../application/service/PaymentSagaHandler.kt`, `carry-common/.../logging/SagaLogContext.kt` |
| 보상 | `OrderCancelledEvent` 캐스케이드, `PaymentSagaHandler.onOrderCancelled`, `DispatchSagaHandler.onOrderCancelled`, `DeliverySagaHandler.onOrderCancelled` | 해피 패스를 벗어났을 때 각 모듈이 독립적으로 자기 상태를 되돌리는 것. 취소는 배차·배달 취소와 환불/인보이스 취소를 함께 트리거하고, 과금 실패는 결제 모듈 안에서만 보상된다 | 교차 | [06-saga](06-saga.md) "보상 트랜잭션", 각 `*SagaHandler.kt` |
| 정체 사가 | `StuckSagaDetector`, 메트릭 `carry.saga.stuck` | 비종결 중간 상태(CREATED/DISPATCHED/PICKED_UP/IN_PROGRESS)에 24h 이상 머문 주문. 감지만 하고 자동 조치는 없다 | Order | `carry-order/.../application/service/StuckSagaDetector.kt` |
| 스위퍼 | `DispatchTimeoutSweeper`, `ChargeRetrySweeper`, `OverdueSweeper`, `RefundRetrySweeper`, `StuckSagaDetector` | `@Scheduled` + `@SchedulerLock` 으로 주기 실행되는 안전망 작업. 도메인 상태 가드로 멱등 | 각 소유 모듈 | 각 파일 |
| 아웃박스 | `OutboxEvent`, `OutboxEventPublisher`, `EventPublisherPort`, 테이블 `*_outbox` | 도메인 트랜잭션과 같은 트랜잭션에 이벤트를 저장하고 Debezium CDC 가 Kafka 로 옮기는 패턴. 직접 Kafka 발행은 금지 | 인프라 (`carry-infra-kafka`, `carry-event`) | `carry-infra-kafka/.../outbox/OutboxEvent.kt`, `carry-event/.../port/EventPublisherPort.kt`, [05-cdc-outbox](05-cdc-outbox.md) |
| 이벤트 봉투 | `OutboxEventEnvelope(id, aggregateType, aggregateId, eventType, payload, traceId)` | 소비자가 Kafka 레코드에서 읽는 공통 헤더 구조. `payload` 안에 `carry-event` 의 data class 가 JSON 으로 들어 있다 | 인프라 | `carry-infra-kafka/.../consumer/OutboxEventEnvelope.kt` |
| ProcessedEvent (처리 완료 마커) | `ProcessedEvent(consumerGroup, eventId)`, `ProcessedEventRepository.claim`, `EventConsumerSupport.processIfNotDuplicate` | 소비자 그룹별 멱등 마커. `INSERT ... ON CONFLICT DO NOTHING` 으로 한 그룹이 한 이벤트를 정확히 한 번 처리하게 한다 | 인프라 | `carry-infra-kafka/.../consumer/ProcessedEvent.kt`, `ProcessedEventRepository.kt` |
| DLQ | `<원본토픽>.DLQ`, `DlqRedriveService`, `DlqPurgeService`, `DlqTopics`, `AuditAction.DLQ_REDRIVE/DLQ_PURGE` | 재시도 소진 또는 비재시도 예외로 처리 실패한 레코드가 가는 토픽. 운영자가 수동으로 재처리(redrive)하거나 폐기(purge)한다 | 인프라 | `carry-infra-kafka/.../KafkaConfig.kt`, `dlq/DlqRedriveService.kt`, `dlq/DlqPurgeService.kt` |
| 보류 (parked) | `DlqRedriveResult.parked`, `MAX_REDRIVES` | 재발행 한도에 도달해 DLQ 에 남겨 둔 poison 메시지 | 인프라 | `carry-infra-kafka/.../dlq/DlqRedriveService.kt` |
| 감사 | `AuditPort`, `AuditAction`, `AuditLog` | 민감 운영 작업(취소·환불·빌링키 등록·배차 지정·거절 페널티·DLQ 조작)의 기록 | 공유 (`carry-audit`) | `carry-audit/.../domain/AuditAction.kt` |

---

## 3. 불일치

같은 개념에 이름이 둘이거나, 같은 이름이 두 뜻으로 쓰이거나, 문서와 코드가 다른 경우다.

| # | 유형 | 내용 | 근거 |
|---|---|---|---|
| 1 | 같은 이름, 다른 뜻 | **클레임(claim)**. Dispatch 에서는 캐리어가 배차를 선점하는 도메인 행위(`Dispatch.claimByCarrier`, `claimDispatch`), 인프라에서는 소비자 그룹이 이벤트 처리권을 원자적으로 잡는 기술 행위(`ProcessedEventRepository.claim`) | `carry-dispatch/.../domain/model/Dispatch.kt`, `carry-infra-kafka/.../consumer/ProcessedEventRepository.kt` |
| 2 | 같은 이름, 다른 뜻 | **옵션**. Order/Price 의 옵션은 고객이 고르는 세탁·건조·부가 선택(`SelectedOption`, `OptionType`), Laundromat 의 옵션은 세탁소 보유 장비(`LaundromatOption {WASHING_MACHINE, DRYER, SNEAKERS}`) | `carry-order/.../domain/vo/SelectedOption.kt`, `carry-laundromat/.../domain/vo/LaundromatOption.kt` |
| 3 | 같은 이름, 다른 뜻 | **MediaResource**. `carry-media` 의 `MediaResource` 는 업로드 상태를 가진 애그리거트, `carry-laundromat` 의 `MediaResource` 는 url·extension 만 가진 VO | `carry-media/.../domain/model/MediaResource.kt`, `carry-laundromat/.../domain/vo/MediaResource.kt` |
| 4 | 같은 이름, 다른 뜻 | **Delivery / 배달**. `carry-delivery` 의 `Delivery` 는 수거부터 반납까지 전체 작업(PICKUP_PENDING→...→DELIVERED), `DeliveryStepType.DELIVERY` 와 `DeliveryStatus.DELIVERY_PENDING/DELIVERED` 는 그중 마지막 단계만 가리킨다. `ChargeType.DELIVERY_FEE` 는 전체 작업의 배달비 | `carry-delivery/.../domain/vo/DeliveryEnums.kt`, `carry-payment/.../domain/vo/PaymentEnums.kt` |
| 5 | 같은 개념, 다른 이름 | **반납 / 배달 완료 / 배송**. 문서([06-saga](06-saga.md))는 "반납", 코드는 `completeDelivery`/`DELIVERED`/"배달", [01-overview](01-overview.md) 는 "배송"이라 부른다 | 각 파일 |
| 6 | 같은 개념, 다른 이름 | **배정 / 지정**. `DispatchStatus.ASSIGNED`, `assignDispatch`, `AuditAction.DISPATCH_ASSIGN` 은 코드상 한 개념이지만 KDoc 은 "지정", `DeliveryExceptions`·`OrderStateQueryPort` KDoc 은 "배정된 캐리어" 로 쓴다. 또 `AssignedBy.CARRIER` 는 캐리어 자기 선점(claim)인데 필드명은 assigned 다 | `carry-dispatch/.../domain/model/Dispatch.kt`, `carry-dispatch/.../domain/vo/DispatchEnums.kt`, `carry-payment/.../application/port/outbound/OrderStateQueryPort.kt` |
| 7 | 같은 개념, 다른 이름 | **캐리어 / 라이더**. [01-overview](01-overview.md) 의 배차 설명은 "라이더 매칭"이라 하고 코드와 나머지 문서는 캐리어(`UserRole.CARRIER`)만 쓴다 | `docs/01-overview.md` 39행 |
| 8 | 같은 개념, 다른 이름 | **선택 옵션 DTO 3종**. `SelectedOption`(order 도메인), `SelectedOptionDto`(event.order), `SelectedOptionSnapshot`(event.delivery) 이 동일한 `(optionType, subOptionType)` 쌍이다. Delivery 는 이 중 이벤트용 `SelectedOptionSnapshot` 을 자기 인바운드 포트와 REST DTO 에도 쓴다 | `carry-order/.../domain/vo/SelectedOption.kt`, `carry-event/.../order/OrderEvents.kt`, `carry-event/.../delivery/DeliveryEvents.kt`, `carry-delivery/.../application/port/inbound/DeliveryCommandUseCase.kt` |
| 9 | 같은 개념, 다른 타입 | **세탁물 종류·옵션 유형**. Price 는 열거형(`LaundryItemType`, `OptionType`, `SubOptionType`), Order·Event·Delivery 는 `String`. 유효값 검증이 Price 밖에서는 이루어지지 않는다 | `carry-price/.../domain/vo/PriceEnums.kt`, `carry-order/.../domain/model/Order.kt` |
| 10 | 같은 개념, 다른 이름 | **좌표**. `Coordinates(latitude, longitude)`(user), `Coordinate`(geo), `Location`(laundromat) | `carry-user/.../domain/vo/Coordinates.kt`, `carry-geo/.../domain/vo/Coordinate.kt`, `carry-laundromat/.../domain/vo/Location.kt` |
| 11 | 같은 개념, 다른 이름 | **인보이스 / 청구서**. 코드는 `Invoice`, [06-saga](06-saga.md) 는 "청구서 발행(ISSUED)" 과 "인보이스" 를 혼용 | `docs/06-saga.md` |
| 12 | 문서와 코드 불일치 | **주문 흐름 순서**. [01-overview](01-overview.md) 는 "주문 → 결제 → 배차 → 수거 → 세탁 → 배송" 이라 하지만 현재 코드는 결제를 물리 흐름에서 분리해 수거 완료 후 인보이스가 발행된다([06-saga](06-saga.md)) | `docs/01-overview.md` 19행, `carry-payment/.../application/service/InvoiceService.kt` |
| 13 | 문서와 코드 불일치 | **PaymentQueryPort**. ADR-0003·ADR-0007 이 언급하는 `PaymentQueryPort`(carry-delivery) 는 존재하지 않는다. Payment 의 동기 결합은 `BillingQueryPort` 와 `OrderStateQueryPort` 다 | `carry-delivery/.../application/port/outbound/`(DeliveryPersistencePort 만 존재), `carry-app/.../adapter/` |
| 14 | 정의되었으나 미사용 | `NotificationType.NEW_DISPATCH_AVAILABLE`, `DISPATCH_ASSIGNED` 는 열거값만 있고 발송 경로가 없다. `UserRole.ADMIN` 의 권한 차이도 코드에 없다 | `carry-notification/.../domain/vo/NotificationEnums.kt`, `carry-user/.../domain/vo/UserRole.kt` |
| 15 | 정의되었으나 미사용 | `OrderUnitType`/`OrderRequestType` 은 Price 의 가격 조건 축이지만 Order 는 이 값을 갖지 않는다. 대신 캐리어가 수거 완료 REST 요청에 문자열로 넣고(`DeliveryWebDto`), `PickupCompletedEvent` 에 실려 나가지만 Payment 는 이 값을 읽지 않는다 | `carry-delivery/.../adapter/inbound/rest/dto/DeliveryWebDto.kt`, `carry-event/.../delivery/DeliveryEvents.kt`, `carry-payment/.../application/service/InvoiceService.kt` |
| 16 | 용어 부재 | **정산(Settlement)**. "정산 원장"·"정산 계정" 이라는 표현은 있으나 실제 지급 행위·주기·상태를 나타내는 식별자는 없다. 원장은 잔액 조회까지만 지원한다 | `carry-payment/.../application/port/outbound/LedgerPort.kt` |
| 17 | 용어 부재 | **세탁소의 정산 관계**. 서비스 설명(01-overview)은 "세탁소에서 세탁 후 배송" 이라 하지만, 정산 모델은 캐리어가 코인세탁소 기계에 현금을 투입하는 구조라 세탁소는 수취인이 아니다. 세탁소 컨텍스트에는 이 관계를 표현하는 용어가 없다 | `carry-payment/.../domain/vo/PaymentEnums.kt` (`LedgerAccountType` KDoc), `docs/01-overview.md` |
