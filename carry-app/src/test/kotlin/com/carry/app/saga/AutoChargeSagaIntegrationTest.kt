package com.carry.app.saga

import com.carry.app.test.FakePgProviderAdapter
import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.OutboxEventAssertions
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.application.service.DeliveryCommandService
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.service.OrderCommandService
import com.carry.order.domain.vo.OrderStatus
import com.carry.payment.application.port.inbound.BillingKeyUseCase
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.service.ChargeRetrySweeper
import com.carry.payment.application.service.OverdueSweeper
import com.carry.payment.application.service.PaymentCommandService
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.PaymentStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * 빌링키 자동과금 사가 E2E — 해피 패스, 실패→연체→회복, 수거 후 취소 3계열 시나리오.
 *
 * 새 결제 경로는 이 모듈이 자신의 InvoiceIssuedEvent 를 자체 소비한다(카프카 없이 outbox 를 직접
 * 읽어 [PaymentSagaHandler.onInvoiceIssued] 를 hand-feed 하는 기존 통합 테스트 패턴을 그대로 따른다).
 * 물리 흐름(주문·배송)은 결제 완료를 기다리지 않으므로, 성공·실패 어느 경로에서도 주문은 물리
 * 이벤트만으로 COMPLETED 에 도달해야 한다 — 이 불변식이 해피 패스·실패 시나리오 모두의 핵심 단언이다.
 */
@Import(SagaIntegrationTestConfig::class)
class AutoChargeSagaIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var orderSagaHandler: OrderSagaEventHandler
    @Autowired lateinit var orderPersistencePort: OrderPersistencePort

    @Autowired lateinit var dispatchCommandService: DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: DispatchPersistencePort

    @Autowired lateinit var deliveryCommandService: DeliveryCommandService
    @Autowired lateinit var deliverySagaHandler: DeliverySagaEventHandler
    @Autowired lateinit var deliveryPersistencePort: DeliveryPersistencePort

    @Autowired lateinit var paymentSagaHandler: PaymentSagaEventHandler
    @Autowired lateinit var paymentCommandService: PaymentCommandService
    @Autowired lateinit var paymentPersistencePort: PaymentPersistencePort
    @Autowired lateinit var billingKeyUseCase: BillingKeyUseCase
    @Autowired lateinit var chargeRetrySweeper: ChargeRetrySweeper
    @Autowired lateinit var overdueSweeper: OverdueSweeper

    @Autowired lateinit var pgProviderAdapter: PgProviderAdapter
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var outbox: OutboxEventAssertions
    private val fakePg get() = pgProviderAdapter as FakePgProviderAdapter

    @BeforeEach
    fun setUp() {
        outbox = OutboxEventAssertions(jdbc, objectMapper)
        fakePg.reset()

        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertCarrier(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertCarrierArea(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    // --- 흐름 조립 헬퍼 (기존 통합 테스트의 hand-feed 패턴 재사용) ---

    private fun createOrder(): Long {
        val order = orderCommandService.createOrder(
            CreateOrderCommand(
                customerId = TestFixtures.CUSTOMER_ID,
                shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                laundromatId = TestFixtures.LAUNDROMAT_ID,
                laundryItemType = "NORMAL",
                selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                desiredPickupAt = TestFixtures.desiredPickupAt(),
                desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
            )
        )
        return order.id!!
    }

    /** 배차 수락 후 수거(5.00kg) 완료까지 진행하고 deliveryId 를 반환한다. */
    private fun dispatchAndPickup(orderId: Long): Long {
        val orderCreatedEvent = outbox.readOutboxPayload<OrderCreatedEvent>("Order", "OrderCreatedEvent", orderId.toString())
        dispatchSagaHandler.onOrderCreated(orderCreatedEvent)
        val dispatch = dispatchPersistencePort.findByOrderId(orderId)!!
        dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatch.id!!, TestFixtures.CARRIER_ID))

        val dispatchAccepted = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())
        orderSagaHandler.onDispatchAccepted(dispatchAccepted)
        deliverySagaHandler.onDispatchAccepted(dispatchAccepted)

        val delivery = deliveryPersistencePort.findByOrderId(orderId)!!
        deliveryCommandService.completePickup(
            deliveryId = delivery.id!!,
            weight = BigDecimal("5.00"),
            photoIds = listOf(1L),
            customerId = TestFixtures.CUSTOMER_ID,
            laundryItemType = "NORMAL",
            orderUnitType = "KG",
            orderRequestType = "STANDARD",
            selectedOptions = listOf(SelectedOptionSnapshot("WASH", "COLD")),
            requestingCarrierId = TestFixtures.CARRIER_ID,
        )
        return delivery.id!!
    }

    /** OrderSagaHandler 에 픽업 완료를 흘려 주문을 PICKED_UP 으로, PaymentSagaHandler 로 인보이스를 발행시킨다. */
    private fun issueInvoice(orderId: Long, deliveryId: Long): InvoiceIssuedEvent {
        val pickupEvent = outbox.readOutboxPayload<PickupCompletedEvent>("Delivery", "PickupCompletedEvent", deliveryId.toString())
        orderSagaHandler.onPickupCompleted(pickupEvent)
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        return outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
    }

    /** 세탁 시작 → 건조 완료 → 배달 출발 → 배달 완료까지, 결제 상태와 무관하게 물리 흐름만 진행한다. */
    private fun completePhysicalFlow(deliveryId: Long) {
        deliveryCommandService.startWashing(deliveryId, listOf(2L), TestFixtures.CARRIER_ID)
        val laundryEvent = outbox.readOutboxPayload<LaundryStartedEvent>("Delivery", "LaundryStartedEvent", deliveryId.toString())
        orderSagaHandler.onLaundryStarted(laundryEvent)

        deliveryCommandService.completeDrying(deliveryId, listOf(3L), TestFixtures.CARRIER_ID)
        deliveryCommandService.startDelivery(deliveryId, TestFixtures.CARRIER_ID)
        deliveryCommandService.completeDelivery(deliveryId, listOf(4L), TestFixtures.CARRIER_ID)

        val deliveryEvent = outbox.readOutboxPayload<DeliveryCompletedEvent>("Delivery", "DeliveryCompletedEvent", deliveryId.toString())
        orderSagaHandler.onDeliveryCompleted(deliveryEvent)
    }

    private fun ledgerRowCount(): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM payment_ledger_entries", Int::class.java)!!

    private fun totalLedgerSum(): Long =
        jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_ledger_entries", Long::class.java)!!

    // InvoiceJpaEntity.lineItems 는 LAZY 컬렉션이라 InvoicePersistencePort.toDomain() 을 트랜잭션
    // 밖(테스트 스레드)에서 호출하면 LazyInitializationException 이 난다 — 인보이스 id/status 조회는
    // 원장 조회와 마찬가지로 JdbcTemplate 로 직접 읽는다.
    private fun invoiceIdFor(orderId: Long): Long =
        jdbc.queryForObject("SELECT id FROM payment_invoices WHERE order_id = ?", Long::class.java, orderId)!!

    private fun invoiceStatus(invoiceId: Long): InvoiceStatus =
        InvoiceStatus.valueOf(
            jdbc.queryForObject("SELECT status FROM payment_invoices WHERE id = ?", String::class.java, invoiceId)!!,
        )

    // --- 빌링키 픽스처 ---

    @Test
    fun `빌링키 재등록 왕복 -- Hibernate 컨버터·부분 유니크 인덱스를 태우고 활성 키는 항상 1개`() {
        TestFixtures.insertBillingKey(billingKeyUseCase)
        val first = billingKeyUseCase.getActive(TestFixtures.CUSTOMER_ID)

        // 재등록: 기존 키 무효화 + 새 키 발급이 한 트랜잭션에서 saveAndFlush 로 순서 보장됨을 검증
        TestFixtures.insertBillingKey(billingKeyUseCase)
        val second = billingKeyUseCase.getActive(TestFixtures.CUSTOMER_ID)

        val activeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_billing_keys WHERE customer_id = ? AND status = 'ACTIVE'",
            Long::class.java, TestFixtures.CUSTOMER_ID,
        )
        val invalidCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_billing_keys WHERE customer_id = ? AND status = 'INVALID'",
            Long::class.java, TestFixtures.CUSTOMER_ID,
        )
        assertThat(activeCount).isEqualTo(1L)
        assertThat(invalidCount).isEqualTo(1L)
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(second.customerKey).isEqualTo(first.customerKey) // customerKey 는 재등록에도 재사용된다
    }

    // --- 시나리오 ⓐ: 해피 패스 ---

    @Test
    fun `해피 패스 -- 자동과금 성공, 원장 균형, 주문은 결제 이벤트 소비 없이 물리 흐름만으로 COMPLETED 도달`() {
        TestFixtures.insertBillingKey(billingKeyUseCase)

        val orderId = createOrder()
        val deliveryId = dispatchAndPickup(orderId)
        val invoiceEvent = issueInvoice(orderId, deliveryId)

        paymentSagaHandler.onInvoiceIssued(invoiceEvent)

        val payment = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(invoiceStatus(invoiceIdFor(orderId))).isEqualTo(InvoiceStatus.PAID)

        // 5.00kg: 세탁비 15000 + 배달비 3000 + 수수료 1500 = 총 19500 — 4행 균형(Σ=0)
        assertThat(ledgerRowCount()).isEqualTo(4)
        assertThat(totalLedgerSum()).isZero()
        outbox.assertOutboxContains("Payment", "PaymentCompletedEvent", orderId.toString())

        // 결제 완료 이벤트를 order 사가에 흘리지 않는다 — 물리 흐름만으로 전이가 이어져야 한다.
        completePhysicalFlow(deliveryId)

        val completedOrder = orderPersistencePort.findById(orderId)!!
        assertThat(completedOrder.status).isEqualTo(OrderStatus.COMPLETED)
        assertThat(completedOrder.completedAt).isNotNull()
    }

    // --- 시나리오 ⓑ: 과금 실패 → 연체 확정 → 회복 ---

    @Test
    fun `과금 실패 후 연체 확정 -- 신규 주문 차단, 재과금 성공 시 PAID 회복 및 신규 주문 재개`() {
        TestFixtures.insertBillingKey(billingKeyUseCase)
        fakePg.shouldSucceed = false

        val orderId = createOrder()
        val deliveryId = dispatchAndPickup(orderId)
        val invoiceEvent = issueInvoice(orderId, deliveryId)

        paymentSagaHandler.onInvoiceIssued(invoiceEvent)

        val failedPayment = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(failedPayment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(failedPayment.nextRetryAt).isNotNull()
        assertThat(outbox.outboxCountFor("Payment", "PaymentFailedEvent")).isEqualTo(1L)

        // 결제 실패와 무관하게 주문은 물리 흐름만으로 계속 진행되어 COMPLETED 에 도달한다.
        completePhysicalFlow(deliveryId)
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.COMPLETED)

        // 연체 확정: 인보이스 발행 시각을 임계(기본 72h) 이전으로 백데이트 후 OverdueSweeper 실행.
        val invoiceId = invoiceIdFor(orderId)
        jdbc.update(
            "UPDATE payment_invoices SET created_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minus(Duration.ofHours(73))), invoiceId,
        )
        overdueSweeper.markOverdueInvoices()
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.OVERDUE)

        // 연체 고객의 신규 주문 생성은 차단된다(409).
        assertThatThrownBy { createOrder() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.OVERDUE_INVOICE_EXISTS)

        // PG 를 성공 상태로 전환하고 백오프 시각을 도래시켜 ChargeRetrySweeper 로 재과금.
        fakePg.shouldSucceed = true
        val paymentId = paymentPersistencePort.findByOrderId(orderId)!!.id!!
        jdbc.update(
            "UPDATE payment_payments SET next_retry_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)), paymentId,
        )
        chargeRetrySweeper.retryFailedCharges()

        val recoveredPayment = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(recoveredPayment.status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.PAID)

        // 회복 후에는 신규 주문 생성이 다시 가능하다.
        val newOrderId = createOrder()
        assertThat(orderPersistencePort.findById(newOrderId)).isNotNull()
    }

    // --- 시나리오 ⓒ: 수거 후 취소 ---

    @Test
    fun `수거 후 취소(i) -- 과금 완료 후 코디 취소는 환불 원장 역분개로 수렴한다`() {
        TestFixtures.insertBillingKey(billingKeyUseCase)

        val orderId = createOrder()
        val deliveryId = dispatchAndPickup(orderId)
        val invoiceEvent = issueInvoice(orderId, deliveryId)
        paymentSagaHandler.onInvoiceIssued(invoiceEvent)
        assertThat(paymentPersistencePort.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.COMPLETED)

        // PICKED_UP 상태 — 고객은 취소 불가하지만 코디네이터는 가능하다(isCancellableBy).
        orderCommandService.cancelOrder(orderId, "세탁소 사정", "COORDINATOR")
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)

        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        paymentSagaHandler.onOrderCancelled(cancelEvent)
        assertThat(paymentPersistencePort.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.REFUND_PENDING)

        paymentCommandService.executeRefund(orderId)

        val refunded = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(refunded.status).isEqualTo(PaymentStatus.REFUNDED)
        outbox.assertOutboxContains("Payment", "RefundCompletedEvent", orderId.toString())

        // 원 4행 불변 + 역분개 4행 추가 = 8행, 전 계정 잔액 0으로 수렴.
        assertThat(ledgerRowCount()).isEqualTo(8)
        assertThat(totalLedgerSum()).isZero()
    }

    @Test
    fun `수거 후 취소(ii) -- 과금 실패 상태에서 코디 취소는 인보이스를 취소하고 재시도 대상에서 제외한다`() {
        TestFixtures.insertBillingKey(billingKeyUseCase)
        fakePg.shouldSucceed = false

        val orderId = createOrder()
        val deliveryId = dispatchAndPickup(orderId)
        val invoiceEvent = issueInvoice(orderId, deliveryId)
        paymentSagaHandler.onInvoiceIssued(invoiceEvent)
        assertThat(paymentPersistencePort.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.FAILED)

        orderCommandService.cancelOrder(orderId, "세탁소 사정", "COORDINATOR")
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        paymentSagaHandler.onOrderCancelled(cancelEvent)

        val invoiceId = invoiceIdFor(orderId)
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.CANCELLED)

        // 인보이스 상태 가드 검증: PG 를 성공 상태로 바꾸고 백오프를 도래시켜도 CANCELLED 인보이스는
        // 재과금되지 않아야 한다 — 만약 가드가 깨졌다면 이 재시도가 성공해버려 아래 단언이 실패한다.
        fakePg.shouldSucceed = true
        val paymentId = paymentPersistencePort.findByOrderId(orderId)!!.id!!
        jdbc.update(
            "UPDATE payment_payments SET next_retry_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)), paymentId,
        )
        chargeRetrySweeper.retryFailedCharges()

        assertThat(paymentPersistencePort.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(fakePg.recordedTransactions).isEmpty()
    }
}
