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
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.carry.order.application.port.outbound.OrderPersistencePort
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
        )

        val pickupEvent = outbox.readOutboxPayload<PickupCompletedEvent>("Delivery", "PickupCompletedEvent", delivery.id.toString())
        orderSagaHandler.onPickupCompleted(pickupEvent)

        return Triple(orderId, delivery.id!!, pickupEvent)
    }

    @Test
    fun `payment failure does not change order status`() {
        val (orderId, _, pickupEvent) = progressToPickupCompleted()

        // Invoice 생성
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)

        val invoicedOrder = orderPersistencePort.findById(orderId)!!
        assertThat(invoicedOrder.status).isEqualTo(OrderStatus.INVOICED)

        // 결제 실패
        fakePg.shouldSucceed = false
        fakePg.failReason = "잔액 부족"

        val payment = paymentCommandService.requestPayment(
            RequestPaymentCommand(orderId, TestFixtures.CUSTOMER_ID, PgProvider.TOSS_PAYMENTS, "fail-key")
        )
        outbox.assertOutboxContains("Payment", "PaymentFailedEvent", orderId.toString())

        // Order는 INVOICED 상태 유지
        orderSagaHandler.onPaymentFailed(
            outbox.readOutboxPayload("Payment", "PaymentFailedEvent", orderId.toString())
        )
        val stillInvoiced = orderPersistencePort.findById(orderId)!!
        assertThat(stillInvoiced.status).isEqualTo(OrderStatus.INVOICED)
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

        // 환불 요청
        paymentCommandService.requestRefund(orderId, "고객 요청 환불")
        outbox.assertOutboxContains("Payment", "RefundCompletedEvent", orderId.toString())
    }
}
