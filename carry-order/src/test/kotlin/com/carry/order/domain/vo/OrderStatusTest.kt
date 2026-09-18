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
    fun `취소 가능한 상태는 모두 CANCELLED 로 전이 가능하다 - 두 표가 갈라지면 깨진다`() {
        // Order.cancel 은 canTransitionTo 를 거치지 않고 isCancellableBy 만 본다(행위자별 규칙이라 의도된 설계).
        // 그래서 두 표가 갈라지면 "취소는 되는데 전이표는 금지" 같은 모순이 조용히 생긴다.
        // 여기서 한쪽 방향(취소 허용 ⇒ 전이 허용)을 전 상태·전 행위자에 대해 못박는다.
        OrderStatus.entries.forEach { status ->
            CancelledBy.entries.forEach { by ->
                if (status.isCancellableBy(by)) {
                    assertThat(status.canTransitionTo(OrderStatus.CANCELLED))
                        .withFailMessage("$by 가 $status 를 취소할 수 있는데 전이표는 CANCELLED 를 막는다")
                        .isTrue()
                }
            }
        }
    }

    @Test
    fun `전이표가 CANCELLED 를 허용하는 상태 집합은 코디네이터가 취소 가능한 집합과 같다`() {
        // 반대 방향. 코디/시스템은 완료 전 모든 상태에서 취소할 수 있으므로 두 집합이 정확히 일치해야 한다.
        val transitionAllows = OrderStatus.entries.filter { it.canTransitionTo(OrderStatus.CANCELLED) }.toSet()
        val coordinatorCanCancel = OrderStatus.entries.filter { it.isCancellableBy(CancelledBy.COORDINATOR) }.toSet()

        assertThat(transitionAllows).isEqualTo(coordinatorCanCancel)
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
