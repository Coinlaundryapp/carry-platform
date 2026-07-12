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
    fun `PICKED_UP에서 CANCELLED로 전이할 수 있다`() {
        assertThat(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.CANCELLED)).isTrue()
    }

    @Test
    fun `IN_PROGRESS에서 CANCELLED로 전이할 수 있다`() {
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.CANCELLED)).isTrue()
    }

    @Test
    fun `COMPLETED에서는 어떤 상태로도 전이할 수 없다`() {
        OrderStatus.entries.forEach { target ->
            assertThat(OrderStatus.COMPLETED.canTransitionTo(target)).isFalse()
        }
    }

    @Test
    fun `CANCELLED에서는 어떤 상태로도 전이할 수 없다`() {
        OrderStatus.entries.forEach { target ->
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse()
        }
    }

    @Test
    fun `전체 Happy Path 상태 전이가 유효하다`() {
        val happyPath = listOf(
            OrderStatus.CREATED to OrderStatus.DISPATCHED,
            OrderStatus.DISPATCHED to OrderStatus.PICKED_UP,
            OrderStatus.PICKED_UP to OrderStatus.IN_PROGRESS,
            OrderStatus.IN_PROGRESS to OrderStatus.COMPLETED,
        )
        happyPath.forEach { (from, to) ->
            assertThat(from.canTransitionTo(to))
                .withFailMessage("$from → $to should be valid")
                .isTrue()
        }
    }

    @Test
    fun `비종결 상태는 모두 CANCELLED로 전이할 수 있다`() {
        listOf(OrderStatus.CREATED, OrderStatus.DISPATCHED, OrderStatus.PICKED_UP, OrderStatus.IN_PROGRESS)
            .forEach { from ->
                assertThat(from.canTransitionTo(OrderStatus.CANCELLED))
                    .withFailMessage("$from → CANCELLED should be valid")
                    .isTrue()
            }
    }

    @Test
    fun `고객은 CREATED, DISPATCHED 상태에서만 취소할 수 있다`() {
        assertThat(OrderStatus.CREATED.isCancellableBy(CancelledBy.CUSTOMER)).isTrue()
        assertThat(OrderStatus.DISPATCHED.isCancellableBy(CancelledBy.CUSTOMER)).isTrue()
        assertThat(OrderStatus.PICKED_UP.isCancellableBy(CancelledBy.CUSTOMER)).isFalse()
        assertThat(OrderStatus.IN_PROGRESS.isCancellableBy(CancelledBy.CUSTOMER)).isFalse()
        assertThat(OrderStatus.COMPLETED.isCancellableBy(CancelledBy.CUSTOMER)).isFalse()
        assertThat(OrderStatus.CANCELLED.isCancellableBy(CancelledBy.CUSTOMER)).isFalse()
    }

    @Test
    fun `코디네이터 시스템은 완료 전까지 모든 진행 상태에서 취소할 수 있다`() {
        listOf(CancelledBy.COORDINATOR, CancelledBy.SYSTEM).forEach { by ->
            assertThat(OrderStatus.CREATED.isCancellableBy(by)).isTrue()
            assertThat(OrderStatus.DISPATCHED.isCancellableBy(by)).isTrue()
            assertThat(OrderStatus.PICKED_UP.isCancellableBy(by)).isTrue()
            assertThat(OrderStatus.IN_PROGRESS.isCancellableBy(by)).isTrue()
            assertThat(OrderStatus.COMPLETED.isCancellableBy(by)).isFalse()
            assertThat(OrderStatus.CANCELLED.isCancellableBy(by)).isFalse()
        }
    }

    @Test
    fun `COMPLETED, CANCELLED 만 forward 사가가 비활성이다`() {
        assertThat(OrderStatus.COMPLETED.isForwardActive()).isFalse()
        assertThat(OrderStatus.CANCELLED.isForwardActive()).isFalse()
    }

    @Test
    fun `진행 중 상태는 forward 사가가 활성이다`() {
        assertThat(OrderStatus.CREATED.isForwardActive()).isTrue()
        assertThat(OrderStatus.DISPATCHED.isForwardActive()).isTrue()
        assertThat(OrderStatus.PICKED_UP.isForwardActive()).isTrue()
        assertThat(OrderStatus.IN_PROGRESS.isForwardActive()).isTrue()
    }
}
