package com.carry.order.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.exception.OrderNotCancellableException
import com.carry.order.domain.exception.OrderNotOwnedException
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
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val auditPort = mockk<AuditPort>(relaxed = true)

    private val sut = OrderCommandService(
        orderPersistencePort, userQueryPort, laundromatQueryPort, serviceAvailabilityQueryPort,
        eventPublisher, metrics, auditPort,
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
            verify { eventPublisher.publish("Order", "42", "OrderCreatedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.order.created") }
        }

        @Test
        fun `존재하지 않는 세탁소로 주문하면 예외가 발생한다`() {
            every { userQueryPort.getShippingAddress(1L, 10L) } returns address
            every { laundromatQueryPort.existsById(100L) } returns false

            assertThatThrownBy { sut.createOrder(aCommand()) }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("세탁소")
                .extracting("errorCode").isEqualTo(ErrorCode.LAUNDROMAT_NOT_FOUND)
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

            sut.cancelOrderByCustomer(1L, 1L, "고객 변심")

            verify { orderPersistencePort.save(any()) }
            verify { eventPublisher.publish("Order", "1", "OrderCancelledEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.order.cancelled", "by" to "CUSTOMER") }
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

            assertThatThrownBy { sut.cancelOrderByCustomer(1L, 1L, "취소 시도") }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }

        private fun paidOrder() = Order.reconstitute(
            id = 1L, customerId = 1L, status = OrderStatus.PAID,
            laundromatId = 10L, laundryItemType = "REGULAR",
            selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
            shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
            carrierId = 100L, invoiceId = 200L, totalAmount = 18000L, actualWeight = java.math.BigDecimal("5.0"),
            cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
            createdAt = now, updatedAt = now,
        )

        @Test
        fun `PAID 주문을 코디네이터가 취소하면 REFUND_PENDING으로 전이하고 OrderCancelledEvent를 발행한다`() {
            val order = paidOrder()
            every { orderPersistencePort.findById(1L) } returns order
            val saved = slot<Order>()
            every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.cancelOrder(1L, "세탁소 사정으로 취소", "COORDINATOR")

            // 결제 완료 후 취소 = 즉시 CANCELLED 가 아니라 환불 보상 대기 상태로 전이
            assertThat(saved.captured.status).isEqualTo(OrderStatus.REFUND_PENDING)
            // 캐스케이드(dispatch/delivery 취소 + 환불) 트리거용 이벤트는 그대로 발행
            verify { eventPublisher.publish("Order", "1", "OrderCancelledEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.order.cancelled", "by" to "COORDINATOR") }
            // 민감 작업 감사: 변이 전(PAID)→후(REFUND_PENDING) 기록
            verify {
                auditPort.record(
                    AuditAction.ORDER_CANCEL, "ORDER", "1",
                    mapOf("status" to "PAID"),
                    mapOf("status" to "REFUND_PENDING", "reason" to "세탁소 사정으로 취소", "cancelledBy" to "COORDINATOR"),
                )
            }
        }

        @Test
        fun `고객이 PAID 주문을 직접 취소하면 차단된다`() {
            // 픽업 후 고객 self-cancel 차단 정책 유지 — 환불 분기는 코디ㆍ시스템 전용
            val order = paidOrder()
            every { orderPersistencePort.findById(1L) } returns order

            assertThatThrownBy { sut.cancelOrderByCustomer(1L, 1L, "고객 변심") }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }

        @Test
        fun `주문 소유자가 아니면 OrderNotOwnedException 이 발생한다`() {
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

            assertThatThrownBy { sut.cancelOrderByCustomer(1L, 999L, "남의 주문 취소 시도") }
                .isInstanceOf(OrderNotOwnedException::class.java)
        }
    }
}
