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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class DispatchTimeoutSweeperTest {

    private val dispatchPersistencePort = mockk<DispatchPersistencePort>(relaxed = true)
    private val dispatchCommandUseCase = mockk<DispatchCommandUseCase>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = DispatchTimeoutSweeper(
        dispatchPersistencePort, dispatchCommandUseCase, clock, leadMinutes = 30,
    )

    private fun pendingWithPickupAt(id: Long, pickupAt: Instant) = Dispatch.reconstitute(
        id = id, orderId = id * 10, laundromatId = 100L, status = DispatchStatus.PENDING,
        carrierId = null, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
        assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    private fun expiredPending(id: Long) = pendingWithPickupAt(id, now.plus(10, ChronoUnit.MINUTES))

    @Test
    fun `만료된 PENDING 배차들을 각각 타임아웃 처리한다`() {
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns
            listOf(expiredPending(1L), expiredPending(2L))

        sut.sweepExpiredDispatches()

        verify { dispatchCommandUseCase.timeoutDispatch(1L) }
        verify { dispatchCommandUseCase.timeoutDispatch(2L) }
    }

    @Test
    fun `만료된 배차가 없으면 아무 것도 하지 않는다`() {
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns emptyList()

        sut.sweepExpiredDispatches()

        verify(exactly = 0) { dispatchCommandUseCase.timeoutDispatch(any()) }
    }

    @Test
    fun `한 배차 처리가 실패해도 나머지 배차 처리를 계속한다`() {
        // 멀티 인스턴스 레이스: 1번은 다른 인스턴스가 이미 처리해 예외, 2번은 계속 진행돼야 한다.
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns
            listOf(expiredPending(1L), expiredPending(2L))
        every { dispatchCommandUseCase.timeoutDispatch(1L) } throws
            DispatchTimeoutNotAllowedException(DispatchStatus.ACCEPTED)

        sut.sweepExpiredDispatches()

        verify { dispatchCommandUseCase.timeoutDispatch(2L) }
    }

    @Test
    fun `조회 임계 시각은 주입된 Clock 과 설정 리드타임으로 계산된다`() {
        // DB 의 CURRENT_TIMESTAMP 가 아니라 주입된 Clock 을 쓴다 — 시계가 테스트에서 제어된다.
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns emptyList()

        sut.sweepExpiredDispatches()

        verify { dispatchPersistencePort.findExpiredPendingDispatches(now.plus(30, ChronoUnit.MINUTES)) }
    }

    @Test
    fun `리드타임 설정을 늘리면 임계 시각이 그만큼 뒤로 밀린다`() {
        val longLead = DispatchTimeoutSweeper(
            dispatchPersistencePort, dispatchCommandUseCase, clock, leadMinutes = 90,
        )
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns emptyList()

        longLead.sweepExpiredDispatches()

        verify { dispatchPersistencePort.findExpiredPendingDispatches(now.plus(90, ChronoUnit.MINUTES)) }
    }

    @Test
    fun `프리필터가 넘긴 비-만료 배차는 도메인 판정에서 걸러진다`() {
        // 조회는 인덱스용 프리필터일 뿐이고 최종 판정은 Dispatch.isExpired 가 한다.
        // 프리필터가 느슨해지거나 경계가 어긋나도 만료 아닌 배차를 종결하지 않는다.
        every { dispatchPersistencePort.findExpiredPendingDispatches(any()) } returns
            listOf(expiredPending(1L), pendingWithPickupAt(2L, now.plus(5, ChronoUnit.HOURS)))

        sut.sweepExpiredDispatches()

        verify { dispatchCommandUseCase.timeoutDispatch(1L) }
        verify(exactly = 0) { dispatchCommandUseCase.timeoutDispatch(2L) }
    }
}
