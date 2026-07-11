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
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.service.PaymentCommandService
import com.carry.payment.domain.vo.LedgerAccountType
import com.carry.payment.domain.vo.PgProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

/**
 * 정산 원장(P3b) E2E — 실 결제·환불 flow 에서 균형 기입(Σ=0)·분배·역분개를 검증.
 * 분배 모델: 세탁비+배달비 → 캐리어(코인세탁소 현금 투입 변제 + 수고비), 수수료 → 플랫폼.
 */
@Import(SagaIntegrationTestConfig::class)
class SettlementLedgerIntegrationTest : IntegrationTestBase() {

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
    @Autowired lateinit var ledgerPort: LedgerPort
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

    /** 주문 → 배차 → 수거(5.00kg) → 인보이스 → 결제 완료 → 주문 PAID 까지 진행. */
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

        paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "pay-key-$orderId")
        )
        orderSagaHandler.onPaymentCompleted(
            outbox.readOutboxPayload<PaymentCompletedEvent>("Payment", "PaymentCompletedEvent", orderId.toString())
        )
        return orderId
    }

    private fun totalLedgerSum(): Long =
        jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_ledger_entries", Long::class.java)!!

    private fun ledgerRowCount(): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM payment_ledger_entries", Int::class.java)!!

    @Test
    fun `payment writes a balanced ledger group -- carrier reimbursed, platform keeps fee`() {
        progressToPaid()

        // 5.00kg: 세탁비 15000 + 배달비 3000 + 수수료 1500 = 총 19500
        assertThat(ledgerRowCount()).isEqualTo(4)
        assertThat(totalLedgerSum()).isZero()
        assertThat(ledgerPort.balance(LedgerAccountType.CUSTOMER, TestFixtures.CUSTOMER_ID)).isEqualTo(-19500L)
        assertThat(ledgerPort.balance(LedgerAccountType.CARRIER, TestFixtures.CARRIER_ID)).isEqualTo(18000L)
        assertThat(ledgerPort.balance(LedgerAccountType.PLATFORM, null)).isEqualTo(1500L)
    }

    @Test
    fun `refund appends a reversal group -- all balances return to zero`() {
        val orderId = progressToPaid()

        // PAID 취소 → REFUND_PENDING → 스위퍼 경로(executeRefund)로 PG 취소·REFUNDED
        orderCommandService.cancelOrder(orderId, "고객 변심", "CUSTOMER")
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        paymentSagaHandler.onOrderCancelled(cancelEvent)
        paymentCommandService.executeRefund(orderId)

        // 원행 불변 — 역분개 4행이 추가되어 총 8행, 계정별 잔액은 전부 0
        assertThat(ledgerRowCount()).isEqualTo(8)
        assertThat(totalLedgerSum()).isZero()
        assertThat(ledgerPort.balance(LedgerAccountType.CUSTOMER, TestFixtures.CUSTOMER_ID)).isZero()
        assertThat(ledgerPort.balance(LedgerAccountType.CARRIER, TestFixtures.CARRIER_ID)).isZero()
        assertThat(ledgerPort.balance(LedgerAccountType.PLATFORM, null)).isZero()
    }
}
