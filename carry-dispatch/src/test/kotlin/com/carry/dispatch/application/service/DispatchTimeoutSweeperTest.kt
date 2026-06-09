package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.exception.DispatchTimeoutNotAllowedException
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DispatchTimeoutSweeperTest {

    private val dispatchPersistencePort = mockk<DispatchPersistencePort>(relaxed = true)
    private val dispatchCommandUseCase = mockk<DispatchCommandUseCase>(relaxed = true)

    private val sut = DispatchTimeoutSweeper(dispatchPersistencePort, dispatchCommandUseCase)

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    private fun expiredPending(id: Long) = Dispatch.reconstitute(
        id = id, orderId = id * 10, laundromatId = 100L, status = DispatchStatus.PENDING,
        carrierId = null, areaCode = "GANGNAM", desiredPickupAt = now.plus(10, ChronoUnit.MINUTES),
        assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `만료된 PENDING 배차들을 각각 타임아웃 처리한다`() {
        every { dispatchPersistencePort.findExpiredPendingDispatches() } returns
            listOf(expiredPending(1L), expiredPending(2L))

        sut.sweepExpiredDispatches()

        verify { dispatchCommandUseCase.timeoutDispatch(1L) }
        verify { dispatchCommandUseCase.timeoutDispatch(2L) }
    }

    @Test
    fun `만료된 배차가 없으면 아무 것도 하지 않는다`() {
        every { dispatchPersistencePort.findExpiredPendingDispatches() } returns emptyList()

        sut.sweepExpiredDispatches()

        verify(exactly = 0) { dispatchCommandUseCase.timeoutDispatch(any()) }
    }

    @Test
    fun `한 배차 처리가 실패해도 나머지 배차 처리를 계속한다`() {
        // 멀티 인스턴스 레이스: 1번은 다른 인스턴스가 이미 처리해 예외, 2번은 계속 진행돼야 한다.
        every { dispatchPersistencePort.findExpiredPendingDispatches() } returns
            listOf(expiredPending(1L), expiredPending(2L))
        every { dispatchCommandUseCase.timeoutDispatch(1L) } throws
            DispatchTimeoutNotAllowedException(DispatchStatus.ACCEPTED)

        sut.sweepExpiredDispatches()

        verify { dispatchCommandUseCase.timeoutDispatch(2L) }
    }
}
