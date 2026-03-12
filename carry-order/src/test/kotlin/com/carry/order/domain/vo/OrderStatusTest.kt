package com.carry.order.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OrderStatusTest {

    @Test
    fun `CREATED에서 DISPATCHED로 전이할 수 있다`() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.DISPATCHED)).isTrue()
    }

    @Test
    fun `CREATED에서 CANCELLED로 전이할 수 있다`() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue()
    }

    @Test
    fun `CREATED에서 PICKED_UP으로 직접 전이할 수 없다`() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PICKED_UP)).isFalse()
    }

    @Test
    fun `DISPATCHED에서 CANCELLED로 전이할 수 있다`() {
        assertThat(OrderStatus.DISPATCHED.canTransitionTo(OrderStatus.CANCELLED)).isTrue()
    }

    @Test
    fun `PICKED_UP에서 CANCELLED로 전이할 수 없다`() {
        assertThat(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.CANCELLED)).isFalse()
    }

    @Test
    fun `COMPLETED에서는 어떤 상태로도 전이할 수 없다`() {
        OrderStatus.entries.forEach { target ->
            assertThat(OrderStatus.COMPLETED.canTransitionTo(target)).isFalse()
        }
    }

    @Test
    fun `CREATED와 DISPATCHED만 취소 가능하다`() {
        assertThat(OrderStatus.CREATED.isCancellable()).isTrue()
        assertThat(OrderStatus.DISPATCHED.isCancellable()).isTrue()
        assertThat(OrderStatus.PICKED_UP.isCancellable()).isFalse()
        assertThat(OrderStatus.INVOICED.isCancellable()).isFalse()
        assertThat(OrderStatus.PAID.isCancellable()).isFalse()
    }

    @Test
    fun `전체 Happy Path 상태 전이가 유효하다`() {
        val happyPath = listOf(
            OrderStatus.CREATED to OrderStatus.DISPATCHED,
            OrderStatus.DISPATCHED to OrderStatus.PICKED_UP,
            OrderStatus.PICKED_UP to OrderStatus.INVOICED,
            OrderStatus.INVOICED to OrderStatus.PAID,
            OrderStatus.PAID to OrderStatus.IN_PROGRESS,
            OrderStatus.IN_PROGRESS to OrderStatus.COMPLETED,
        )
        happyPath.forEach { (from, to) ->
            assertThat(from.canTransitionTo(to))
                .withFailMessage("$from → $to should be valid")
                .isTrue()
        }
    }
}
