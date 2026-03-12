package com.carry.order.application.service

import com.carry.infra.kafka.outbox.OutboxEventPublisher
import com.carry.infra.observability.metrics.BusinessMetrics
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.exception.OrderNotCancellableException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderCommandServiceTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val userQueryPort = mockk<UserQueryPort>()
    private val laundromatQueryPort = mockk<LaundromatQueryPort>()
    private val serviceAvailabilityQueryPort = mockk<ServiceAvailabilityQueryPort>(relaxed = true)
    private val outboxEventPublisher = mockk<OutboxEventPublisher>(relaxed = true)
    private val businessMetrics = mockk<BusinessMetrics>(relaxed = true)

    private val sut = OrderCommandService(
        orderPersistencePort, userQueryPort, laundromatQueryPort, serviceAvailabilityQueryPort, outboxEventPublisher, businessMetrics,
    )

    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", "06230",
        37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )

    private val now = Instant.now()

    private fun aCommand() = CreateOrderCommand(
        customerId = 1L,
        shippingAddressId = 10L,
        laundromatId = 100L,
        laundryItemType = "REGULAR",
        selectedOptions = listOf(SelectedOptionCommand("WASH", "STANDARD")),
        desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
        desiredDeliveryAt = now.plus(6, ChronoUnit.HOURS),
    )

    @Nested
    inner class CreateOrder {

        @Test
        fun `주문을 생성하고 Outbox 이벤트를 발행한다`() {
            every { userQueryPort.getShippingAddress(1L, 10L) } returns address
            every { laundromatQueryPort.existsById(100L) } returns true
            val saved = slot<Order>()
            every { orderPersistencePort.save(capture(saved)) } answers {
                Order.reconstitute(
                    id = 42L, customerId = saved.captured.customerId,
                    status = saved.captured.status, laundromatId = saved.captured.laundromatId,
                    laundryItemType = saved.captured.laundryItemType,
                    selectedOptions = saved.captured.selectedOptions,
                    shippingAddress = saved.captured.shippingAddress,
                    desiredPickupAt = saved.captured.desiredPickupAt,
                    desiredDeliveryAt = saved.captured.desiredDeliveryAt,
                    carrierId = null, invoiceId = null, totalAmount = null, actualWeight = null,
                    cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.createOrder(aCommand())

            assertThat(result.id).isEqualTo(42L)
            assertThat(result.status).isEqualTo(OrderStatus.CREATED)
            verify { outboxEventPublisher.publish("Order", "42", "OrderCreatedEvent", any(), any()) }
        }

        @Test
        fun `존재하지 않는 세탁소로 주문하면 예외가 발생한다`() {
            every { userQueryPort.getShippingAddress(1L, 10L) } returns address
            every { laundromatQueryPort.existsById(100L) } returns false

            assertThatThrownBy { sut.createOrder(aCommand()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("세탁소")
        }
    }

    @Nested
    inner class CancelOrder {

        @Test
        fun `CREATED 상태의 주문을 취소하고 이벤트를 발행한다`() {
            val order = Order.reconstitute(
                id = 1L, customerId = 1L, status = OrderStatus.CREATED,
                laundromatId = 10L, laundryItemType = "REGULAR",
                selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
                shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
                carrierId = null, invoiceId = null, totalAmount = null, actualWeight = null,
                cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
                createdAt = now, updatedAt = now,
            )
            every { orderPersistencePort.findById(1L) } returns order

            sut.cancelOrder(1L, "고객 변심", "CUSTOMER")

            verify { orderPersistencePort.save(any()) }
            verify { outboxEventPublisher.publish("Order", "1", "OrderCancelledEvent", any(), any()) }
        }

        @Test
        fun `PICKED_UP 상태의 주문 취소 시 예외가 발생한다`() {
            val order = Order.reconstitute(
                id = 1L, customerId = 1L, status = OrderStatus.PICKED_UP,
                laundromatId = 10L, laundryItemType = "REGULAR",
                selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
                shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
                carrierId = 100L, invoiceId = null, totalAmount = null, actualWeight = java.math.BigDecimal("3.0"),
                cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
                createdAt = now, updatedAt = now,
            )
            every { orderPersistencePort.findById(1L) } returns order

            assertThatThrownBy { sut.cancelOrder(1L, "취소 시도", "CUSTOMER") }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }
    }
}
