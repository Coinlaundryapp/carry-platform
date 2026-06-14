package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DispatchQueryServiceTest {

    private val dispatchPersistencePort = mockk<DispatchPersistencePort>()
    private val carrierAreaPersistencePort = mockk<CarrierAreaPersistencePort>()
    private val sut = DispatchQueryService(dispatchPersistencePort, carrierAreaPersistencePort)

    private val now: Instant = Instant.now()

    private fun aDispatch() = Dispatch.reconstitute(
        id = 1L, orderId = 1L, laundromatId = 10L, status = DispatchStatus.PENDING,
        carrierId = null, areaCode = "GANGNAM", desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
        assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `코디네이터 목록 조회는 상태·권역 필터를 그대로 위임한다`() {
        every {
            dispatchPersistencePort.findForCoordinator(DispatchStatus.PENDING, "GANGNAM", null, 20)
        } returns listOf(aDispatch())

        val result = sut.getDispatchesForCoordinator(DispatchStatus.PENDING, "GANGNAM", null, 20)

        assertThat(result).hasSize(1)
        verify { dispatchPersistencePort.findForCoordinator(DispatchStatus.PENDING, "GANGNAM", null, 20) }
    }

    @Test
    fun `필터가 없으면 null로 위임한다`() {
        every { dispatchPersistencePort.findForCoordinator(null, null, null, 20) } returns emptyList()

        val result = sut.getDispatchesForCoordinator(null, null, null, 20)

        assertThat(result).isEmpty()
    }
}
