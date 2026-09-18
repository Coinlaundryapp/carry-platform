package com.carry.app.saga

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.OutboxEventAssertions
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.dispatch.application.port.inbound.CancelDispatchCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.vo.OrderStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(SagaIntegrationTestConfig::class)
class OrderCancellationSagaIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var orderSagaHandler: OrderSagaEventHandler
    @Autowired lateinit var orderPersistencePort: OrderPersistencePort

    @Autowired lateinit var dispatchCommandService: DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: DispatchPersistencePort

    @Autowired lateinit var deliverySagaHandler: DeliverySagaEventHandler
    @Autowired lateinit var deliveryPersistencePort: DeliveryPersistencePort

    @Autowired lateinit var paymentSagaHandler: com.carry.payment.application.port.inbound.PaymentSagaEventHandler
    @Autowired lateinit var invoicePersistencePort: com.carry.payment.application.port.outbound.InvoicePersistencePort
    @Autowired lateinit var billingKeyUseCase: com.carry.payment.application.port.inbound.BillingKeyUseCase

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var outbox: OutboxEventAssertions

    @BeforeEach
    fun setUp() {
        outbox = OutboxEventAssertions(jdbc, objectMapper)
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertCarrier(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertCarrierArea(jdbc)
        TestFixtures.insertServiceArea(jdbc)
        TestFixtures.insertBillingKey(billingKeyUseCase)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    private fun createOrderAndDispatch(): Pair<Long, Long> {
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

        val event = outbox.readOutboxPayload<OrderCreatedEvent>("Order", "OrderCreatedEvent", orderId.toString())
        dispatchSagaHandler.onOrderCreated(event)
        val dispatch = dispatchPersistencePort.findByOrderId(orderId)!!

        return orderId to dispatch.id!!
    }

    @Test
    fun `cancel order before dispatch -- cascades to dispatch`() {
        val (orderId, _) = createOrderAndDispatch()

        // 주문 취소
        orderCommandService.cancelOrder(orderId, "고객 변심", "CUSTOMER")
        outbox.assertOutboxContains("Order", "OrderCancelledEvent", orderId.toString())

        val cancelledOrder = orderPersistencePort.findById(orderId)!!
        assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)

        // 배차 취소 전파
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        dispatchSagaHandler.onOrderCancelled(cancelEvent)

        val dispatch = dispatchPersistencePort.findByOrderId(orderId)!!
        assertThat(dispatch.status).isEqualTo(DispatchStatus.CANCELLED)
        outbox.assertOutboxContains("Dispatch", "DispatchCancelledEvent", orderId.toString())
    }

    @Test
    fun `cancel order after dispatch accepted -- cascades to dispatch and delivery`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        // 배차 수락
        val accepted = dispatchCommandService.claimDispatch(
            ClaimDispatchCommand(dispatchId, TestFixtures.CARRIER_ID)
        )
        val dispatchAcceptedEvent = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())

        orderSagaHandler.onDispatchAccepted(dispatchAcceptedEvent)
        deliverySagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        // 주문 취소
        orderCommandService.cancelOrder(orderId, "세탁소 사정", "COORDINATOR")

        val cancelledOrder = orderPersistencePort.findById(orderId)!!
        assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)

        // 배차 취소 전파
        val cancelEvent = outbox.readOutboxPayload<OrderCancelledEvent>("Order", "OrderCancelledEvent", orderId.toString())
        dispatchSagaHandler.onOrderCancelled(cancelEvent)

        val cancelledDispatch = dispatchPersistencePort.findByOrderId(orderId)!!
        assertThat(cancelledDispatch.status).isEqualTo(DispatchStatus.CANCELLED)

        // 배달 취소 전파
        deliverySagaHandler.onOrderCancelled(cancelEvent)

        val cancelledDelivery = deliveryPersistencePort.findByOrderId(orderId)!!
        assertThat(cancelledDelivery.status).isEqualTo(DeliveryStatus.CANCELLED)
    }

    @Test
    fun `dispatch cancelled by coordinator -- cascades to order and delivery`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        // 배차 수락 + 배달 생성
        dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, TestFixtures.CARRIER_ID))
        val dispatchAcceptedEvent = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())
        orderSagaHandler.onDispatchAccepted(dispatchAcceptedEvent)
        deliverySagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        // 코디네이터가 배차 취소
        dispatchCommandService.cancelDispatch(CancelDispatchCommand(dispatchId, "캐리어 연락두절"))
        outbox.assertOutboxContains("Dispatch", "DispatchCancelledEvent", orderId.toString())

        // 주문 취소 전파
        val dispatchCancelledEvent = outbox.readOutboxPayload<DispatchCancelledEvent>("Dispatch", "DispatchCancelledEvent", orderId.toString())
        orderSagaHandler.onDispatchCancelled(dispatchCancelledEvent)

        val cancelledOrder = orderPersistencePort.findById(orderId)!!
        assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)

        // 배달 취소 전파
        deliverySagaHandler.onDispatchCancelled(dispatchCancelledEvent)

        val cancelledDelivery = deliveryPersistencePort.findByOrderId(orderId)!!
        assertThat(cancelledDelivery.status).isEqualTo(DeliveryStatus.CANCELLED)
    }

    @Test
    fun `cancel committed before pickup event -- forward event ignored and no ghost invoice`() {
        // cross-aggregate race: 취소(Order 행)가 선커밋된 뒤 비동기 PickupCompletedEvent 가 도착하는 경우.
        // 기존엔 order saga 가 CANCELLED 에서 markPickedUp throw → DLQ poison,
        // payment saga 는 취소된 주문에 유령 인보이스를 발행했다.
        val (orderId, dispatchId) = createOrderAndDispatch()

        dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, TestFixtures.CARRIER_ID))
        val dispatchAcceptedEvent = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())
        orderSagaHandler.onDispatchAccepted(dispatchAcceptedEvent)
        deliverySagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        // 취소 선커밋 (DISPATCHED 는 취소 가능 윈도우)
        orderCommandService.cancelOrder(orderId, "고객 변심", "CUSTOMER")
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)

        // 라이더는 이미 수거를 마친 상태 — 늦은 PickupCompletedEvent 도착
        val delivery = deliveryPersistencePort.findByOrderId(orderId)!!
        val pickupEvent = com.carry.event.delivery.PickupCompletedEvent(
            deliveryId = delivery.id!!,
            orderId = orderId,
            carrierId = TestFixtures.CARRIER_ID,
            customerId = TestFixtures.CUSTOMER_ID,
            actualWeight = java.math.BigDecimal("3.00"),
            laundryItemType = "NORMAL",
            orderUnitType = "KG",
            orderRequestType = "STANDARD",
            selectedOptions = emptyList(),
        )

        // order saga: throw 없이 멱등 no-op — 주문은 CANCELLED 유지
        orderSagaHandler.onPickupCompleted(pickupEvent)
        assertThat(orderPersistencePort.findById(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)

        // payment saga: 유령 인보이스 미발행
        paymentSagaHandler.onPickupCompleted(pickupEvent)
        assertThat(invoicePersistencePort.findByOrderId(orderId)).isNull()
    }

    @Test
    fun `cancel order after pickup -- should fail because not cancellable`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        // 배차 수락 → 배달 생성 → 수거 완료까지 진행
        dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, TestFixtures.CARRIER_ID))
        val dispatchAcceptedEvent = outbox.readOutboxPayload<DispatchAcceptedEvent>("Dispatch", "DispatchAcceptedEvent", orderId.toString())

        orderSagaHandler.onDispatchAccepted(dispatchAcceptedEvent)
        deliverySagaHandler.onDispatchAccepted(dispatchAcceptedEvent)

        // PICKED_UP으로 전환 (수거 완료 이벤트 시뮬레이션)
        val delivery = deliveryPersistencePort.findByOrderId(orderId)!!
        val pickupEvent = com.carry.event.delivery.PickupCompletedEvent(
            deliveryId = delivery.id!!,
            orderId = orderId,
            carrierId = TestFixtures.CARRIER_ID,
            customerId = TestFixtures.CUSTOMER_ID,
            actualWeight = java.math.BigDecimal("3.00"),
            laundryItemType = "NORMAL",
            orderUnitType = "KG",
            orderRequestType = "STANDARD",
            selectedOptions = emptyList(),
        )
        orderSagaHandler.onPickupCompleted(pickupEvent)

        val pickedUpOrder = orderPersistencePort.findById(orderId)!!
        assertThat(pickedUpOrder.status).isEqualTo(OrderStatus.PICKED_UP)

        // 취소 시도 → 실패
        assertThatThrownBy {
            orderCommandService.cancelOrder(orderId, "취소하고 싶어요", "CUSTOMER")
        }.isInstanceOf(Exception::class.java)
    }
}
