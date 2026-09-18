package com.carry.order.application.service

import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.exception.OrderNotOwnedException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderQueryServiceTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>()
    private val sut = OrderQueryService(orderPersistencePort)

    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", "06230",
        37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )
    private val now = Instant.now()

    private fun anOrder(customerId: Long) = Order.reconstitute(
        id = 1L, customerId = customerId, status = OrderStatus.CREATED,
        laundromatId = 10L, laundryItemType = "REGULAR",
        selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = null, actualWeight = null,
        cancellation = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `소유자가 주문을 조회하면 반환한다`() {
        every { orderPersistencePort.findById(1L) } returns anOrder(customerId = 7L)

        val order = sut.getOrder(1L, requestingUserId = 7L)

        assertThat(order.id).isEqualTo(1L)
        assertThat(order.customerId).isEqualTo(7L)
    }

    @Test
    fun `다른 사용자가 조회하면 OrderNotOwnedException 이 발생한다`() {
        every { orderPersistencePort.findById(1L) } returns anOrder(customerId = 7L)

        assertThatThrownBy { sut.getOrder(1L, requestingUserId = 999L) }
            .isInstanceOf(OrderNotOwnedException::class.java)
    }

    @Test
    fun `존재하지 않는 주문이면 OrderNotFoundException 이 발생한다`() {
        every { orderPersistencePort.findById(1L) } returns null

        assertThatThrownBy { sut.getOrder(1L, requestingUserId = 7L) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }

    @Test
    fun `코디네이터 목록 조회는 상태 필터를 그대로 위임한다`() {
        every { orderPersistencePort.findForCoordinator(OrderStatus.IN_PROGRESS, null, 20) } returns listOf(anOrder(7L))

        val orders = sut.getOrdersForCoordinator(OrderStatus.IN_PROGRESS, null, 20)

        assertThat(orders).hasSize(1)
    }

    @Test
    fun `코디네이터 단건 조회는 소유자 검증 없이 반환한다`() {
        every { orderPersistencePort.findById(1L) } returns anOrder(customerId = 7L)

        val order = sut.getOrderForCoordinator(1L)

        assertThat(order.id).isEqualTo(1L)
    }

    @Test
    fun `코디네이터 단건 조회도 없으면 OrderNotFoundException 이 발생한다`() {
        every { orderPersistencePort.findById(1L) } returns null

        assertThatThrownBy { sut.getOrderForCoordinator(1L) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }
}
