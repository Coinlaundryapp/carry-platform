package com.carry.order.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.port.outbound.IdempotencyPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.port.outbound.contract.FakeLaundromatQueryPort
import com.carry.order.application.port.outbound.contract.FakeServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.contract.FakeUserQueryPort
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class OrderCommandServiceTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val userQueryPort = FakeUserQueryPort()
    private val laundromatQueryPort = FakeLaundromatQueryPort()
    private val serviceAvailabilityQueryPort = FakeServiceAvailabilityQueryPort()
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val auditPort = mockk<AuditPort>(relaxed = true)
    private val idempotencyPort = mockk<IdempotencyPort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = OrderCommandService(
        orderPersistencePort, userQueryPort, laundromatQueryPort, serviceAvailabilityQueryPort,
        eventPublisher, metrics, auditPort, idempotencyPort, clock,
    )

    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", "06230",
        37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )

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
            userQueryPort.put(1L, 10L, address)
            laundromatQueryPort.add(100L)
            serviceAvailabilityQueryPort.markAvailable("GANGNAM")
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
                    carrierId = null, actualWeight = null,
                    cancellation = null, completedAt = null,
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
            userQueryPort.put(1L, 10L, address)
            // laundromatQueryPort에 100L을 add하지 않아 existsById가 false를 반환

            assertThatThrownBy { sut.createOrder(aCommand()) }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("세탁소")
                .extracting("errorCode").isEqualTo(ErrorCode.LAUNDROMAT_NOT_FOUND)
        }
    }

    @Nested
    inner class Idempotency {

        private fun keyedCommand(key: String) = aCommand().copy(idempotencyKey = key)

        private fun existingOrder(id: Long) = Order.reconstitute(
            id = id, customerId = 1L, status = OrderStatus.CREATED, laundromatId = 100L,
            laundryItemType = "REGULAR", selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
            shippingAddress = address, desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
            desiredDeliveryAt = now.plus(6, ChronoUnit.HOURS),
            carrierId = null, actualWeight = null,
            cancellation = null, completedAt = null,
            createdAt = now, updatedAt = now,
        )

        @Test
        fun `완료된 키로 재요청하면 새로 만들지 않고 기존 주문을 재생한다`() {
            every { idempotencyPort.findCompletedOrderId("k1") } returns 42L
            every { orderPersistencePort.findById(42L) } returns existingOrder(42L)

            val result = sut.createOrder(keyedCommand("k1"))

            assertThat(result.id).isEqualTo(42L)
            verify(exactly = 0) { orderPersistencePort.save(any()) }
            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { idempotencyPort.reserve(any()) }
        }

        @Test
        fun `진행 중인 키(선점 실패)는 409 IDEMPOTENT_REQUEST_IN_PROGRESS 를 던진다`() {
            every { idempotencyPort.findCompletedOrderId("k2") } returns null
            every { idempotencyPort.reserve("k2") } returns false

            assertThatThrownBy { sut.createOrder(keyedCommand("k2")) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS)

            verify(exactly = 0) { orderPersistencePort.save(any()) }
        }

        @Test
        fun `신규 키는 선점 후 주문을 생성하고 결과를 complete 로 저장한다`() {
            userQueryPort.put(1L, 10L, address)
            laundromatQueryPort.add(100L)
            serviceAvailabilityQueryPort.markAvailable("GANGNAM")
            every { idempotencyPort.findCompletedOrderId("k3") } returns null
            every { idempotencyPort.reserve("k3") } returns true
            val saved = slot<Order>()
            every { orderPersistencePort.save(capture(saved)) } answers {
                Order.reconstitute(
                    id = 42L, customerId = saved.captured.customerId, status = saved.captured.status,
                    laundromatId = saved.captured.laundromatId, laundryItemType = saved.captured.laundryItemType,
                    selectedOptions = saved.captured.selectedOptions, shippingAddress = saved.captured.shippingAddress,
                    desiredPickupAt = saved.captured.desiredPickupAt, desiredDeliveryAt = saved.captured.desiredDeliveryAt,
                    carrierId = null, actualWeight = null,
                    cancellation = null, completedAt = null,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.createOrder(keyedCommand("k3"))

            assertThat(result.id).isEqualTo(42L)
            verify { idempotencyPort.complete("k3", 42L) }
        }
    }

    @Nested
    inner class CancelOrder {

        private fun orderAt(status: OrderStatus, carrierId: Long? = null, actualWeight: java.math.BigDecimal? = null) = Order.reconstitute(
            id = 1L, customerId = 1L, status = status,
            laundromatId = 10L, laundryItemType = "REGULAR",
            selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
            shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
            carrierId = carrierId, actualWeight = actualWeight,
            cancellation = null, completedAt = null,
            createdAt = now, updatedAt = now,
        )

        @Test
        fun `CREATED 상태의 주문을 취소하고 이벤트를 발행한다`() {
            val order = orderAt(OrderStatus.CREATED)
            every { orderPersistencePort.findById(1L) } returns order

            sut.cancelOrderByCustomer(1L, 1L, "고객 변심")

            verify { orderPersistencePort.save(any()) }
            verify { eventPublisher.publish("Order", "1", "OrderCancelledEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.order.cancelled", "by" to "CUSTOMER") }
        }

        @Test
        fun `PICKED_UP 상태의 주문을 고객이 취소하면 예외가 발생한다`() {
            val order = orderAt(OrderStatus.PICKED_UP, carrierId = 100L, actualWeight = java.math.BigDecimal("3.0"))
            every { orderPersistencePort.findById(1L) } returns order

            assertThatThrownBy { sut.cancelOrderByCustomer(1L, 1L, "취소 시도") }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }

        @Test
        fun `코디네이터가 PICKED_UP 주문 취소 시 CANCELLED로 전이하고 이벤트를 발행한다`() {
            val order = orderAt(OrderStatus.PICKED_UP, carrierId = 100L, actualWeight = java.math.BigDecimal("5.0"))
            every { orderPersistencePort.findById(1L) } returns order
            val saved = slot<Order>()
            every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.cancelOrder(1L, "세탁소 사정으로 취소", "COORDINATOR")

            // 수거 후 취소도 즉시 CANCELLED — 환불/과금중단은 OrderCancelledEvent 를 구독하는 결제 모듈 소관
            assertThat(saved.captured.status).isEqualTo(OrderStatus.CANCELLED)
            verify { eventPublisher.publish("Order", "1", "OrderCancelledEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.order.cancelled", "by" to "COORDINATOR") }
            verify {
                auditPort.record(
                    AuditAction.ORDER_CANCEL, "ORDER", "1",
                    mapOf("status" to "PICKED_UP"),
                    mapOf("status" to "CANCELLED", "reason" to "세탁소 사정으로 취소", "cancelledBy" to "COORDINATOR"),
                )
            }
        }

        @Test
        fun `고객 셀프취소도 감사로그를 기록한다`() {
            // 코디 취소(cancelOrder)만 감사가 남고 셀프취소는 메트릭만 남던 액터별 비대칭 제거
            val order = orderAt(OrderStatus.CREATED)
            every { orderPersistencePort.findById(1L) } returns order

            sut.cancelOrderByCustomer(1L, 1L, "고객 변심")

            verify {
                auditPort.record(
                    AuditAction.ORDER_CANCEL, "ORDER", "1",
                    mapOf("status" to "CREATED"),
                    mapOf("status" to "CANCELLED", "reason" to "고객 변심", "cancelledBy" to "CUSTOMER"),
                )
            }
        }

        @Test
        fun `유효하지 않은 취소 주체 문자열은 INVALID_INPUT 예외로 매핑된다`() {
            // raw IllegalArgumentException 은 catch-all 에 걸려 500 — 클라이언트 잘못이므로 400 이어야 한다
            val order = orderAt(OrderStatus.CREATED)
            every { orderPersistencePort.findById(1L) } returns order

            assertThatThrownBy { sut.cancelOrder(1L, "취소", "HACKER") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT)
        }

        @Test
        fun `주문 소유자가 아니면 OrderNotOwnedException 이 발생한다`() {
            val order = orderAt(OrderStatus.CREATED)
            every { orderPersistencePort.findById(1L) } returns order

            assertThatThrownBy { sut.cancelOrderByCustomer(1L, 999L, "남의 주문 취소 시도") }
                .isInstanceOf(OrderNotOwnedException::class.java)
        }
    }
}
