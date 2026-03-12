package com.carry.app.saga

import com.carry.app.test.FakePgProviderAdapter
import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.OutboxEventAssertions
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.service.DeliveryCommandService
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.SelectedOptionDto
import com.carry.event.order.ShippingAddressDto
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.vo.OrderStatus
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.application.service.PaymentCommandService
import com.carry.payment.application.port.outbound.PgProviderAdapter
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
class OrderSagaIntegrationTest : IntegrationTestBase() {

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
        TestFixtures.insertCarrierArea(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    @Test
    fun `full order lifecycle from CREATED to COMPLETED`() {
        // 1. 주문 생성
        val order = orderCommandService.createOrder(
            CreateOrderCommand(
                customerId = TestFixtures.CUSTOMER_ID,
                shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                laundromatId = TestFixtures.LAUNDROMAT_ID,
                laundryItemType = "NORMAL",
                selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                desiredPickupAt = TestFixtures.desiredPickupAt(),
                desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
                areaCode = TestFixtures.AREA_CODE,
            )
        )
        val orderId = order.id!!
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        outbox.assertOutboxContains("Order", "OrderCreatedEvent", orderId.toString())

        // 2. DispatchSagaHandler: OrderCreatedEvent → Dispatch 생성 (PENDING)
        val orderCreatedEvent = outbox.readOutboxPayload<OrderCreatedEvent>("Order", "OrderCreatedEvent", orderId.toString())
        dispatchSagaHandler.onOrderCreated(orderCreatedEvent)

        val dispatch = dispatchPersistencePort.findByOrderId(orderId)!!
        assertThat(dispatch.status).isEqualTo(DispatchStatus.PENDING)

        // 3. 캐리어가 배차 수락 → ACCEPTED
        val acceptedDispatch = dispatchCommandService.claimDispatch(
            ClaimDispatchCommand(dispatch.id!!, TestFixtures.CARRIER_ID)
        )
        assertThat(acceptedDispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
        outbox.assertOutboxContains("Dispatch", "DispatchAcceptedEvent", orderId.toString())

        // 4. OrderSagaHandler: DispatchAcceptedEvent → Order DISPATCHED
        val dispatchAcceptedEvent = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())
        orderSagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        val dispatchedOrder = orderPersistencePort.findById(orderId)!!
        assertThat(dispatchedOrder.status).isEqualTo(OrderStatus.DISPATCHED)
        assertThat(dispatchedOrder.carrierId).isEqualTo(TestFixtures.CARRIER_ID)

        // 5. DeliverySagaHandler: DispatchAcceptedEvent → Delivery 생성 (PICKUP_PENDING)
        deliverySagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        val delivery = deliveryPersistencePort.findByOrderId(orderId)!!
        assertThat(delivery.status).isEqualTo(DeliveryStatus.PICKUP_PENDING)
        assertThat(delivery.steps).hasSize(5)

        // 6. 수거 완료
        val pickupWeight = BigDecimal("5.00")
        deliveryCommandService.completePickup(
            deliveryId = delivery.id!!,
            weight = pickupWeight,
            photoIds = listOf(1L),
            customerId = TestFixtures.CUSTOMER_ID,
            laundryItemType = "NORMAL",
            orderUnitType = "KG",
            orderRequestType = "STANDARD",
            selectedOptions = listOf(SelectedOptionSnapshot("WASH", "COLD")),
        )

        val pickedUpDelivery = deliveryPersistencePort.findById(delivery.id!!)!!
        assertThat(pickedUpDelivery.status).isEqualTo(DeliveryStatus.PICKED_UP)
        assertThat(pickedUpDelivery.actualWeight).isEqualByComparingTo(pickupWeight)

        // 7. OrderSagaHandler: PickupCompletedEvent → Order PICKED_UP
        val pickupEvent = outbox.readOutboxPayload<PickupCompletedEvent>("Delivery", "PickupCompletedEvent", delivery.id.toString())
        orderSagaHandler.onPickupCompleted(pickupEvent)

        val pickedUpOrder = orderPersistencePort.findById(orderId)!!
        assertThat(pickedUpOrder.status).isEqualTo(OrderStatus.PICKED_UP)

        // 8. PaymentSagaHandler: PickupCompletedEvent → Invoice 생성 + InvoiceIssuedEvent
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        outbox.assertOutboxContains("Payment", "InvoiceIssuedEvent", orderId.toString())

        // 9. OrderSagaHandler: InvoiceIssuedEvent → Order INVOICED
        val invoiceEvent = outbox.readOutboxPayload<InvoiceIssuedEvent>("Payment", "InvoiceIssuedEvent", orderId.toString())
        orderSagaHandler.onInvoiceIssued(invoiceEvent)

        val invoicedOrder = orderPersistencePort.findById(orderId)!!
        assertThat(invoicedOrder.status).isEqualTo(OrderStatus.INVOICED)
        assertThat(invoicedOrder.invoiceId).isEqualTo(invoiceEvent.invoiceId)
        assertThat(invoicedOrder.totalAmount).isEqualTo(invoiceEvent.totalAmount)

        // Invoice 금액 검증: 5kg * 3000원 = 15000 + 배달비 3000 + 수수료 1500 = 19500
        assertThat(invoiceEvent.totalAmount).isEqualTo(19500L)
        assertThat(invoiceEvent.lineItems).hasSize(3)

        // 10. 결제 요청 → PaymentCompletedEvent
        fakePg.shouldSucceed = true
        val payment = paymentCommandService.requestPayment(
            RequestPaymentCommand(
                orderId = orderId,
                customerId = TestFixtures.CUSTOMER_ID,
                pgProvider = PgProvider.TOSS_PAYMENTS,
                paymentKey = "test-payment-key",
            )
        )
        outbox.assertOutboxContains("Payment", "PaymentCompletedEvent", orderId.toString())

        // 11. OrderSagaHandler: PaymentCompletedEvent → Order PAID
        val paymentEvent = outbox.readOutboxPayload<PaymentCompletedEvent>("Payment", "PaymentCompletedEvent", orderId.toString())
        orderSagaHandler.onPaymentCompleted(paymentEvent)

        val paidOrder = orderPersistencePort.findById(orderId)!!
        assertThat(paidOrder.status).isEqualTo(OrderStatus.PAID)

        // 12. 세탁 시작
        deliveryCommandService.startWashing(delivery.id!!, listOf(2L))
        outbox.assertOutboxContains("Delivery", "LaundryStartedEvent", delivery.id.toString())

        // 13. OrderSagaHandler: LaundryStartedEvent → Order IN_PROGRESS
        val laundryEvent = outbox.readOutboxPayload<LaundryStartedEvent>("Delivery", "LaundryStartedEvent", delivery.id.toString())
        orderSagaHandler.onLaundryStarted(laundryEvent)

        val inProgressOrder = orderPersistencePort.findById(orderId)!!
        assertThat(inProgressOrder.status).isEqualTo(OrderStatus.IN_PROGRESS)

        // 14. 건조 완료
        deliveryCommandService.completeDrying(delivery.id!!, listOf(3L))

        // 15. 배달 출발
        deliveryCommandService.startDelivery(delivery.id!!)

        // 16. 배달 완료
        deliveryCommandService.completeDelivery(delivery.id!!, listOf(4L))
        outbox.assertOutboxContains("Delivery", "DeliveryCompletedEvent", delivery.id.toString())

        // 17. OrderSagaHandler: DeliveryCompletedEvent → Order COMPLETED
        val deliveryEvent = outbox.readOutboxPayload<DeliveryCompletedEvent>("Delivery", "DeliveryCompletedEvent", delivery.id.toString())
        orderSagaHandler.onDeliveryCompleted(deliveryEvent)

        val completedOrder = orderPersistencePort.findById(orderId)!!
        assertThat(completedOrder.status).isEqualTo(OrderStatus.COMPLETED)
        assertThat(completedOrder.completedAt).isNotNull()
    }
}
