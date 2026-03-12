# carry-payment 모듈 설계

## 역할
1. **청구서(Invoice) 발행**: 캐리어가 수거 후 계량한 무게를 기반으로 정확한 청구서를 생성
2. **결제(Payment) 처리**: PG사 연동을 통한 결제 요청/확인/취소
3. **환불(Refund) 처리**: 코디네이터를 통한 환불

---

## 도메인 모델

### Invoice (Aggregate Root)

```kotlin
class Invoice private constructor(
    val id: Long?,
    val orderId: Long,
    val customerId: Long,
    private var _status: InvoiceStatus,
    val lineItems: List<InvoiceLineItem>,
    val weight: BigDecimal,
    val totalAmount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status

    companion object {
        fun create(
            orderId: Long,
            customerId: Long,
            weight: BigDecimal,
            lineItems: List<InvoiceLineItem>,
        ): Invoice
        fun reconstitute(...): Invoice
    }

    fun markPaid()
    fun cancel()
    fun refund()
}
```

### Payment (Aggregate Root)

```kotlin
class Payment private constructor(
    val id: Long?,
    val invoiceId: Long,
    val orderId: Long,
    val customerId: Long,
    private var _status: PaymentStatus,
    val pgProvider: PgProvider,
    private var _pgTransactionId: String?,
    val amount: Long,
    private var _paidAt: Instant?,
    private var _failReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val pgTransactionId get() = _pgTransactionId
    val paidAt get() = _paidAt
    val failReason get() = _failReason

    companion object {
        fun create(
            invoiceId: Long,
            orderId: Long,
            customerId: Long,
            amount: Long,
            pgProvider: PgProvider,
        ): Payment
        fun reconstitute(...): Payment
    }

    fun markCompleted(pgTransactionId: String)
    fun markFailed(reason: String)
    fun markRefunded()
}
```

### Value Objects & Enums

```kotlin
enum class InvoiceStatus {
    ISSUED,     // 발행됨
    PAID,       // 결제됨
    CANCELLED,  // 취소됨
    REFUNDED;   // 환불됨
}

enum class PaymentStatus {
    PENDING,    // 결제 대기
    COMPLETED,  // 결제 완료
    FAILED,     // 결제 실패
    REFUNDED;   // 환불됨

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        PENDING   -> target in setOf(COMPLETED, FAILED)
        COMPLETED -> target == REFUNDED
        FAILED    -> target == PENDING  // 재시도
        REFUNDED  -> false
    }
}

enum class PgProvider {
    TOSS_PAYMENTS;
}

// 청구서 항목
data class InvoiceLineItem(
    val chargeType: ChargeType,
    val description: String,
    val amount: Long,
)

enum class ChargeType {
    LAUNDRY_PRICE,  // 세탁 비용
    DELIVERY_FEE,   // 배달비
    SERVICE_FEE,    // 플랫폼 수수료
}
```

### 도메인 예외

```kotlin
class InvoiceNotFoundException
class InvoiceAlreadyPaidException
class PaymentNotFoundException
class PaymentAlreadyCompletedException
class PaymentGatewayException(message: String, cause: Throwable?)
```

---

## PG 추상화 (레지스트리 패턴)

```kotlin
// 아웃바운드 포트 — PG사에 독립적인 결제 인터페이스
interface PaymentGatewayPort {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResult
    fun cancelPayment(pgTransactionId: String): PgCancelResult
}

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Long,
    val orderName: String,
    val customerName: String,
    val paymentKey: String,  // 프론트에서 전달받은 결제 키
)

data class PgPaymentResult(
    val success: Boolean,
    val pgTransactionId: String?,
    val failReason: String?,
)

data class PgCancelResult(
    val success: Boolean,
    val refundAmount: Long?,
    val failReason: String?,
)

// 레지스트리 — PG사 추가 시 어댑터만 등록하면 됨
@Component
class PgProviderRegistry(
    adapters: List<PgProviderAdapter>,
) {
    private val registry: Map<PgProvider, PaymentGatewayPort> =
        adapters.associateBy { it.provider }

    fun getAdapter(provider: PgProvider): PaymentGatewayPort =
        registry[provider]
            ?: throw IllegalArgumentException("지원하지 않는 PG사: $provider")
}

interface PgProviderAdapter : PaymentGatewayPort {
    val provider: PgProvider
}
```

### 토스페이먼츠 어댑터

```kotlin
@Component
class TossPaymentsAdapter(
    properties: TossPaymentsProperties,
) : PgProviderAdapter {

    override val provider = PgProvider.TOSS_PAYMENTS

    private val restClient = RestClient.builder()
        .baseUrl("https://api.tosspayments.com/v1")
        .defaultHeader("Authorization", "Basic ${encodeSecretKey(properties.secretKey)}")
        .build()

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResult {
        // POST /payments/confirm
        // paymentKey + orderId + amount 확인
    }

    override fun cancelPayment(pgTransactionId: String): PgCancelResult {
        // POST /payments/{paymentKey}/cancel
    }
}

@ConfigurationProperties(prefix = "carry.payment.toss")
data class TossPaymentsProperties(
    val secretKey: String,
    val clientKey: String,
)
```

---

## 애플리케이션 레이어

### 인바운드 포트

```kotlin
interface PaymentCommandUseCase {
    fun requestPayment(command: RequestPaymentCommand): Payment
    fun requestRefund(orderId: Long, reason: String): Payment
}

interface PaymentQueryUseCase {
    fun getPayment(paymentId: Long): Payment
    fun getPaymentByOrder(orderId: Long): Payment?
    fun isOrderPaid(orderId: Long): Boolean  // carry-delivery에서 sync 호출
}

interface InvoiceQueryUseCase {
    fun getInvoice(invoiceId: Long): Invoice
    fun getInvoiceByOrder(orderId: Long): Invoice?
}

// 이벤트 핸들러
interface PaymentSagaEventHandler {
    fun onPickupCompleted(event: PickupCompletedEvent)  // 청구서 발행
}
```

### 아웃바운드 포트

```kotlin
interface InvoicePersistencePort {
    fun save(invoice: Invoice): Invoice
    fun findById(id: Long): Invoice?
    fun findByOrderId(orderId: Long): Invoice?
}

interface PaymentPersistencePort {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
}

// carry-price 모듈 동기 조회
interface PriceCalculationPort {
    fun calculatePrice(
        condition: PriceCondition,
        selectedOptions: List<Pair<OptionType, SubOptionType>>,
        weight: BigDecimal,
    ): PriceCalculationResult
}

data class PriceCalculationResult(
    val lineItems: List<InvoiceLineItem>,
    val totalAmount: Long,
)
```

### RequestPaymentCommand

```kotlin
data class RequestPaymentCommand(
    val orderId: Long,
    val customerId: Long,
    val pgProvider: PgProvider,
    val paymentKey: String,  // 토스 결제 위젯에서 받은 키
)
```

### PaymentCommandService 핵심 로직

```kotlin
// 청구서 발행 (PickupCompletedEvent 수신 시)
@Transactional
fun createInvoice(event: PickupCompletedEvent) {
    // 1. 가격 계산 (carry-price sync port)
    val priceResult = priceCalculationPort.calculatePrice(
        condition = PriceCondition(event.orderUnitType, event.orderRequestType, event.laundryItemType),
        selectedOptions = event.selectedOptions,
        weight = event.actualWeight,
    )

    // 2. 청구서 생성
    val invoice = Invoice.create(
        orderId = event.orderId,
        customerId = event.customerId,
        weight = event.actualWeight,
        lineItems = priceResult.lineItems,
    )
    val saved = invoicePersistencePort.save(invoice)

    // 3. Outbox 이벤트 발행
    outboxEventPublisher.publish(
        aggregateType = "Payment",
        aggregateId = event.orderId.toString(),
        eventType = "InvoiceIssuedEvent",
        payload = InvoiceIssuedEvent(saved.id!!, event.orderId, saved.totalAmount, saved.lineItems)
    )
}

// 결제 요청 (고객이 앱에서 결제 시)
@Transactional
fun requestPayment(command: RequestPaymentCommand): Payment {
    // 1. 청구서 확인
    val invoice = invoicePersistencePort.findByOrderId(command.orderId)
        ?: throw InvoiceNotFoundException()

    // 2. Payment 생성
    val payment = Payment.create(
        invoiceId = invoice.id!!,
        orderId = command.orderId,
        customerId = command.customerId,
        amount = invoice.totalAmount,
        pgProvider = command.pgProvider,
    )

    // 3. PG 결제 확인 (토스 결제 승인 API)
    val adapter = pgProviderRegistry.getAdapter(command.pgProvider)
    val result = adapter.requestPayment(PgPaymentRequest(
        orderId = command.orderId,
        amount = invoice.totalAmount,
        paymentKey = command.paymentKey,
        ...
    ))

    // 4. 결과 처리
    if (result.success) {
        payment.markCompleted(result.pgTransactionId!!)
        invoice.markPaid()
        // Outbox: PaymentCompletedEvent
    } else {
        payment.markFailed(result.failReason ?: "알 수 없는 오류")
        // Outbox: PaymentFailedEvent
    }

    return paymentPersistencePort.save(payment)
}
```

---

## DB 스키마

```sql
-- 청구서
CREATE TABLE payment_invoices (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL UNIQUE,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    weight          NUMERIC(10,2) NOT NULL,
    total_amount    BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 청구서 항목
CREATE TABLE payment_invoice_line_items (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES payment_invoices(id),
    charge_type     VARCHAR(30) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 결제
CREATE TABLE payment_payments (
    id                BIGSERIAL PRIMARY KEY,
    invoice_id        BIGINT NOT NULL REFERENCES payment_invoices(id),
    order_id          BIGINT NOT NULL,
    customer_id       BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    pg_provider       VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(100),
    amount            BIGINT NOT NULL,
    paid_at           TIMESTAMPTZ,
    fail_reason       VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_invoices_order_id ON payment_invoices(order_id);
CREATE INDEX idx_payment_payments_order_id ON payment_payments(order_id);
```

---

## 테스트 전략

| 계층 | 테스트 | 도구 |
|------|--------|------|
| Domain | InvoiceStatus/PaymentStatus 상태 전이, Invoice 금액 계산 | JUnit 5, AssertJ |
| Application | 청구서 발행 로직, 결제 성공/실패 분기, 환불 로직 | MockK |
| Architecture | 헥사고날 의존성 규칙 | ArchUnit |
