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
import com.carry.order.application.service.PaymentRetryDeadlineSweeper
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderStatus
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.service.PaymentCommandService
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
import java.time.Instant
import java.time.temporal.ChronoUnit

@Import(SagaIntegrationTestConfig::class)
class PaymentSagaIntegrationTest : IntegrationTestBase() {

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
    @Autowired lateinit var paymentRetryDeadlineSweeper: PaymentRetryDeadlineSweeper
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

    /**
     * 주문 → 배차 → 수거 완료까지 진행하고 orderId, deliveryId 반환
     */
    private fun progressToPickupCompleted(): Triple<Long, Long, PickupCompletedEvent> {
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

        return Triple(orderId, delivery.id!!, pickupEvent)
    }

    /**
     * INVOICED 까지 진행한 뒤 결제 실패 시켜 PAYMENT_FAILED 로 만들고 orderId 반환.
     */
    private fun progressToPaymentFailed(): Long {
        val (orderId, _, pickupEvent) = progressToPickupCompleted()

        paymentSagaHandler.onPickupCompleted(pickupEvent)
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)

        fakePg.shouldSucceed = false
        fakePg.failReason = "잔액 부족"
        paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "fail-key")
        )
        orderSagaHandler.onPaymentFailed(
            outbox.readOutboxPayload("Payment", "PaymentFailedEvent", orderId.toString())
        )
        return orderId
    }

    @Test
    fun `payment failure transitions order to PAYMENT_FAILED`() {
        val orderId = progressToPaymentFailed()

        outbox.assertOutboxContains("Payment", "PaymentFailedEvent", orderId.toString())
        // 결제 실패 시 INVOICED 에 방치하지 않고 PAYMENT_FAILED 로 전이 — 재결제 창 제공
        val order = orderPersistencePort.findById(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }

    @Test
    fun `re-payment after failure transitions order to PAID`() {
        val orderId = progressToPaymentFailed()

        // 재결제 성공 — requestPayment 가 새 Payment 행을 만들어 주문당 FAILED + COMPLETED 다중 행이 된다
        fakePg.reset()
        fakePg.shouldSucceed = true
        paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "retry-key")
        )
        orderSagaHandler.onPaymentCompleted(
            outbox.readOutboxPayload("Payment", "PaymentCompletedEvent", orderId.toString())
        )

        val order = orderPersistencePort.findById(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun `sweeper cancels PAYMENT_FAILED order past the re-payment deadline`() {
        val orderId = progressToPaymentFailed()

        // updated_at 을 시한(24h) 이전으로 백데이트 → 스위퍼가 시한 초과로 인식
        jdbc.update(
            "UPDATE orders SET updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)),
            orderId,
        )

        paymentRetryDeadlineSweeper.sweepExpiredPaymentFailedOrders()

        val order = orderPersistencePort.findById(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.cancellation?.by).isEqualTo(CancelledBy.SYSTEM)
        outbox.assertOutboxContains("Order", "OrderCancelledEvent", orderId.toString())

        // 실패한 결제만 있으므로 자동 환불은 일어나지 않는다(throw 없이 skip)
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        paymentSagaHandler.onOrderCancelled(cancelEvent)
        outbox.assertOutboxDoesNotContain("Payment", "RefundCompletedEvent", orderId.toString())
    }

    @Test
    fun `cancelling a PAID order runs the full refund compensation and cascades`() {
        val (orderId, _, pickupEvent) = progressToPickupCompleted()

        // Invoice + 결제 완료 → PAID
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)
        fakePg.shouldSucceed = true
        paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "pay-key")
        )
        orderSagaHandler.onPaymentCompleted(
            outbox.readOutboxPayload("Payment", "PaymentCompletedEvent", orderId.toString())
        )
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.PAID)

        // 코디네이터 취소 → 결제 완료 후이므로 REFUND_PENDING 으로 전이 + OrderCancelledEvent 발행
        orderCommandService.cancelOrder(orderId, "세탁소 사정으로 취소", "COORDINATOR")
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.REFUND_PENDING)

        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())

        // 결제 모듈: 환불 대기 표시(PG 미호출, DLQ 위험 제거) — 아직 RefundCompletedEvent 없음
        paymentSagaHandler.onOrderCancelled(cancelEvent)
        outbox.assertOutboxDoesNotContain("Payment", "RefundCompletedEvent", orderId.toString())

        // RefundRetrySweeper 가 하는 일(executeRefund)을 직접 실행 → PG 취소 + RefundCompletedEvent
        paymentCommandService.executeRefund(orderId)
        outbox.assertOutboxContains("Payment", "RefundCompletedEvent", orderId.toString())

        // 주문 모듈: 환불 완료 수신 → REFUNDED
        orderSagaHandler.onRefundCompleted(
            outbox.readOutboxPayload("Payment", "RefundCompletedEvent", orderId.toString())
        )
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.REFUNDED)

        // 배차·배달 캐스케이드 취소
        dispatchSagaHandler.onOrderCancelled(cancelEvent)
        deliverySagaHandler.onOrderCancelled(cancelEvent)
        assertThat(dispatchPersistencePort.findByOrderId(orderId)!!.status)
            .isEqualTo(com.carry.dispatch.domain.vo.DispatchStatus.CANCELLED)
        assertThat(deliveryPersistencePort.findByOrderId(orderId)!!.status)
            .isEqualTo(com.carry.delivery.domain.vo.DeliveryStatus.CANCELLED)
    }

    @Test
    fun `invoice calculation is correct based on weight`() {
        val (orderId, _, pickupEvent) = progressToPickupCompleted()

        paymentSagaHandler.onPickupCompleted(pickupEvent)

        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())

        // 5kg * 3000원/kg = 15000 (세탁비)
        // 배달비 = 3000
        // 수수료 = 15000 * 0.1 = 1500
        // 합계 = 19500
        assertThat(invoiceEvent.totalAmount).isEqualTo(19500L)
        assertThat(invoiceEvent.lineItems).hasSize(3)

        val laundryItem = invoiceEvent.lineItems.first { it.chargeType == "LAUNDRY_PRICE" }
        assertThat(laundryItem.amount).isEqualTo(15000L)

        val deliveryFee = invoiceEvent.lineItems.first { it.chargeType == "DELIVERY_FEE" }
        assertThat(deliveryFee.amount).isEqualTo(3000L)

        val serviceFee = invoiceEvent.lineItems.first { it.chargeType == "SERVICE_FEE" }
        assertThat(serviceFee.amount).isEqualTo(1500L)
    }

    @Test
    fun `payment success followed by refund`() {
        val (orderId, _, pickupEvent) = progressToPickupCompleted()

        // Invoice + 결제 완료
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)

        fakePg.shouldSucceed = true
        paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "pay-key")
        )
        val paymentEvent = outbox.readOutboxPayload<PaymentCompletedEvent>("Payment", "PaymentCompletedEvent", orderId.toString())
        orderSagaHandler.onPaymentCompleted(paymentEvent)

        val paidOrder = orderPersistencePort.findById(orderId)!!
        assertThat(paidOrder.status).isEqualTo(OrderStatus.PAID)

        // 환불: 대기 표시(COMPLETED→REFUND_PENDING) 후 스위퍼 경로(executeRefund)로 PG 환불
        paymentCommandService.markRefundPending(orderId)
        paymentCommandService.executeRefund(orderId)
        outbox.assertOutboxContains("Payment", "RefundCompletedEvent", orderId.toString())
    }
}
