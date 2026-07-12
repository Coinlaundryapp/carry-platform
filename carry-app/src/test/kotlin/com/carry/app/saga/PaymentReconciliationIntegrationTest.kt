package com.carry.app.saga

import com.carry.app.test.FakePgProviderAdapter
import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.OutboxEventAssertions
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.application.service.DeliveryCommandService
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.port.outbound.PgTransactionRecord
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.application.service.PaymentCommandService
import com.carry.payment.application.service.PgReconciliationJob
import com.carry.payment.domain.vo.PaymentStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant

/**
 * PG 대사 잡 E2E — 실 결제 flow(Fake PG 원장 자동 기록) 위에서 불일치 감지·멱등 적재를 검증.
 * grace 를 0 으로 낮춰 방금 만든 테스트 데이터가 대사 윈도에 들어오게 한다.
 */
@Disabled("Task 14에서 자동과금 경로로 재작성")
@Import(SagaIntegrationTestConfig::class)
@TestPropertySource(properties = ["carry.payment.reconcile-grace-ms=0"])
class PaymentReconciliationIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var orderSagaHandler: OrderSagaEventHandler
    @Autowired lateinit var dispatchCommandService: DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: DispatchPersistencePort
    @Autowired lateinit var deliveryCommandService: DeliveryCommandService
    @Autowired lateinit var deliverySagaHandler: DeliverySagaEventHandler
    @Autowired lateinit var deliveryPersistencePort: DeliveryPersistencePort
    @Autowired lateinit var paymentSagaHandler: PaymentSagaEventHandler
    @Autowired lateinit var paymentCommandService: PaymentCommandService
    @Autowired lateinit var paymentPersistencePort: PaymentPersistencePort
    @Autowired lateinit var pgReconciliationJob: PgReconciliationJob
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

    /** 주문 → 배차 → 수거 → 인보이스 → 결제 완료(COMPLETED)까지 진행하고 orderId 반환. */
    private fun progressToPaid(): Long {
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
        val orderId = order.id!!

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
        val pickupEvent = outbox.readOutboxPayload<PickupCompletedEvent>("Delivery", "PickupCompletedEvent", delivery.id.toString())
        orderSagaHandler.onPickupCompleted(pickupEvent)
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)

        // TODO(Task 14): requestPayment 제거됨 — AutoChargeService 경로로 재작성 예정. 현재 @Disabled.
        return orderId
    }

    private fun mismatchRows(): List<Map<String, Any>> =
        jdbc.queryForList("SELECT mismatch_type, dedup_key, detail FROM payment_reconciliation_mismatches")

    @Test
    fun `clean state -- reconciliation records nothing`() {
        progressToPaid()

        pgReconciliationJob.reconcile()

        assertThat(mismatchRows()).isEmpty()
    }

    @Test
    fun `orphan PG charge is detected once and dedup suppresses re-detection`() {
        progressToPaid()
        // 로컬에 없는 PG 과금 주입 — 고객 돈이 나갔는데 우리 시스템엔 흔적이 없는 케이스
        fakePg.extraTransactions += PgTransactionRecord(
            "orphan-tx-1", PgTransactionType.CHARGE, 9900L, Instant.now().minusSeconds(3600),
        )

        pgReconciliationJob.reconcile()

        val rows = mismatchRows()
        assertThat(rows).hasSize(1)
        assertThat(rows.single()["mismatch_type"]).isEqualTo("ORPHAN_PG_CHARGE")
        assertThat(rows.single()["dedup_key"]).isEqualTo("orphan-tx-1")

        // 윈도 중첩 재실행 — 중복 적재 없음
        pgReconciliationJob.reconcile()
        assertThat(mismatchRows()).hasSize(1)
    }

    @Test
    fun `refund reconciliation -- PG cancel done but local REFUND_PENDING converges to REFUNDED`() {
        // "PG 성공·로컬 markRefunded 직전 실패" 윈도 재현 — 스위퍼의 PG 재호출을 기다리지 않고
        // 대사가 PG 원장에서 취소 확정을 확인해 로컬만 REFUNDED 로 수렴한다(P2b 환불 화해).
        val orderId = progressToPaid()
        orderSagaHandler.onPaymentCompleted(
            outbox.readOutboxPayload<PaymentCompletedEvent>("Payment", "PaymentCompletedEvent", orderId.toString())
        )

        // PAID 주문 취소 → 환불 보상 시작(payment REFUND_PENDING, PG 호출 없음)
        orderCommandService.cancelOrder(orderId, "고객 변심", "CUSTOMER")
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        paymentSagaHandler.onOrderCancelled(cancelEvent)

        val pending = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(pending.status).isEqualTo(PaymentStatus.REFUND_PENDING)

        // PG 원장에는 취소가 이미 존재(로컬 마킹만 실패한 상태 시뮬레이션)
        fakePg.extraTransactions += PgTransactionRecord(
            pending.pgTransactionId!!, PgTransactionType.CANCEL, pending.amount, Instant.now(),
        )

        pgReconciliationJob.reconcile()

        val refunded = paymentPersistencePort.findByOrderId(orderId)!!
        assertThat(refunded.status).isEqualTo(PaymentStatus.REFUNDED)
        outbox.assertOutboxContains("Payment", "RefundCompletedEvent", orderId.toString())
        // 수렴됐으므로 불일치 원장은 비어 있어야 한다
        assertThat(mismatchRows()).isEmpty()
    }

    @Test
    fun `local COMPLETED without PG record is detected as MISSING_IN_PG`() {
        progressToPaid()
        // PG 측 원장 유실 시뮬레이션 — 로컬은 COMPLETED 인데 PG 목록에 과금이 없다
        fakePg.recordedTransactions.clear()

        pgReconciliationJob.reconcile()

        val rows = mismatchRows()
        assertThat(rows).hasSize(1)
        assertThat(rows.single()["mismatch_type"]).isEqualTo("MISSING_IN_PG")
    }
}
