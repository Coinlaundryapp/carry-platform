package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.AcceptAssignmentCommand
import com.carry.dispatch.application.port.inbound.AssignDispatchCommand
import com.carry.dispatch.application.port.inbound.CancelDispatchCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.RejectAssignmentCommand
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.application.port.outbound.PenaltyRecordPersistencePort
import com.carry.dispatch.domain.exception.CarrierNotInAreaException
import com.carry.dispatch.domain.exception.DispatchNotOwnedException
import com.carry.dispatch.domain.exception.DispatchTimeoutNotAllowedException
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.dispatch.domain.model.CarrierArea
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
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

class DispatchCommandServiceTest {

    private val dispatchPersistencePort = mockk<DispatchPersistencePort>(relaxed = true)
    private val carrierAreaPersistencePort = mockk<CarrierAreaPersistencePort>()
    private val penaltyRecordPersistencePort = mockk<PenaltyRecordPersistencePort>(relaxed = true)
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val auditPort = mockk<com.carry.audit.port.AuditPort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = DispatchCommandService(
        dispatchPersistencePort, carrierAreaPersistencePort, penaltyRecordPersistencePort, eventPublisher, metrics, auditPort, clock,
    )

    private val pickupAt = now.plus(2, ChronoUnit.HOURS)

    private fun pendingDispatch() = Dispatch.reconstitute(
        id = 1L, orderId = 10L, laundromatId = 100L, status = DispatchStatus.PENDING,
        carrierId = null, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
        assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    private fun assignedDispatch() = Dispatch.reconstitute(
        id = 1L, orderId = 10L, laundromatId = 100L, status = DispatchStatus.ASSIGNED,
        carrierId = 200L, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
        assignedBy = AssignedBy.COORDINATOR, assignedAt = now, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    private fun activeCarrierArea() = CarrierArea.reconstitute(
        id = 1L, carrierId = 200L, areaCode = "GANGNAM", areaName = "강남구",
        active = true, createdAt = now, updatedAt = now,
    )

    /** save가 전달받은 dispatch를 그대로 반향(echo)하도록 스텁한다(상태 보존). */
    private fun stubSaveEcho() {
        val saved = slot<Dispatch>()
        every { dispatchPersistencePort.save(capture(saved)) } answers {
            val d = saved.captured
            Dispatch.reconstitute(
                id = d.id ?: 1L, orderId = d.orderId, laundromatId = d.laundromatId,
                status = d.status, carrierId = d.carrierId, areaCode = d.areaCode,
                desiredPickupAt = d.desiredPickupAt, assignedBy = d.assignedBy,
                assignedAt = d.assignedAt, acceptedAt = d.acceptedAt, cancelReason = d.cancelReason,
                createdAt = now, updatedAt = now,
            )
        }
    }

    @Nested
    inner class ClaimDispatch {

        @Test
        fun `캐리어가 구역에 등록되어 있으면 배차를 클레임하고 이벤트를 발행한다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            every { carrierAreaPersistencePort.findByCarrierIdAndAreaCode(200L, "GANGNAM") } returns activeCarrierArea()
            val saved = slot<Dispatch>()
            every { dispatchPersistencePort.save(capture(saved)) } answers {
                Dispatch.reconstitute(
                    id = 1L, orderId = saved.captured.orderId, laundromatId = saved.captured.laundromatId,
                    status = saved.captured.status, carrierId = saved.captured.carrierId,
                    areaCode = saved.captured.areaCode, desiredPickupAt = saved.captured.desiredPickupAt,
                    assignedBy = saved.captured.assignedBy, assignedAt = saved.captured.assignedAt,
                    acceptedAt = saved.captured.acceptedAt, cancelReason = saved.captured.cancelReason,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.claimDispatch(ClaimDispatchCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(result.carrierId).isEqualTo(200L)
            verify { eventPublisher.publish("Dispatch", "10", "DispatchAcceptedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.dispatch.accepted", "via" to "claim") }
        }

        @Test
        fun `캐리어가 구역에 등록되어 있지 않으면 예외가 발생한다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            every { carrierAreaPersistencePort.findByCarrierIdAndAreaCode(200L, "GANGNAM") } returns null

            assertThatThrownBy { sut.claimDispatch(ClaimDispatchCommand(1L, 200L)) }
                .isInstanceOf(CarrierNotInAreaException::class.java)
        }

        @Test
        fun `같은 캐리어가 다시 클레임하면 이벤트를 재발행하지 않고 현재 상태를 반환한다`() {
            // findById가 같은 인스턴스를 반환 → 첫 호출이 ACCEPTED로 변이시켜 재호출은 멱등 no-op이 된다.
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            every { carrierAreaPersistencePort.findByCarrierIdAndAreaCode(200L, "GANGNAM") } returns activeCarrierArea()
            stubSaveEcho()

            sut.claimDispatch(ClaimDispatchCommand(1L, 200L))
            val result = sut.claimDispatch(ClaimDispatchCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(result.carrierId).isEqualTo(200L)
            verify(exactly = 1) {
                eventPublisher.publish("Dispatch", "10", "DispatchAcceptedEvent", any(), any())
            }
        }

        @Test
        fun `이미 클레임한 캐리어의 재시도는 구역을 재검사하지 않고 성공한다`() {
            // 실제 선점(PENDING)에서만 구역을 검증하므로, 이미 소유한 재시도는 구역이 비활성이어도 막히지 않는다.
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            every { carrierAreaPersistencePort.findByCarrierIdAndAreaCode(200L, "GANGNAM") } returns activeCarrierArea()

            sut.claimDispatch(ClaimDispatchCommand(1L, 200L))
            val result = sut.claimDispatch(ClaimDispatchCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ACCEPTED)
            verify(exactly = 1) { carrierAreaPersistencePort.findByCarrierIdAndAreaCode(200L, "GANGNAM") }
        }
    }

    @Nested
    inner class AssignDispatch {

        @Test
        fun `코디네이터가 배차를 지정하면 ASSIGNED 상태가 되고 이벤트는 발행하지 않는다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            val saved = slot<Dispatch>()
            every { dispatchPersistencePort.save(capture(saved)) } answers {
                Dispatch.reconstitute(
                    id = 1L, orderId = saved.captured.orderId, laundromatId = saved.captured.laundromatId,
                    status = saved.captured.status, carrierId = saved.captured.carrierId,
                    areaCode = saved.captured.areaCode, desiredPickupAt = saved.captured.desiredPickupAt,
                    assignedBy = saved.captured.assignedBy, assignedAt = saved.captured.assignedAt,
                    acceptedAt = saved.captured.acceptedAt, cancelReason = saved.captured.cancelReason,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.assignDispatch(AssignDispatchCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ASSIGNED)
            assertThat(result.carrierId).isEqualTo(200L)
            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class AcceptAssignment {

        @Test
        fun `캐리어가 지정된 배차를 수락하면 ACCEPTED 상태가 되고 이벤트를 발행한다`() {
            val dispatch = assignedDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            val saved = slot<Dispatch>()
            every { dispatchPersistencePort.save(capture(saved)) } answers {
                Dispatch.reconstitute(
                    id = 1L, orderId = saved.captured.orderId, laundromatId = saved.captured.laundromatId,
                    status = saved.captured.status, carrierId = saved.captured.carrierId,
                    areaCode = saved.captured.areaCode, desiredPickupAt = saved.captured.desiredPickupAt,
                    assignedBy = saved.captured.assignedBy, assignedAt = saved.captured.assignedAt,
                    acceptedAt = saved.captured.acceptedAt, cancelReason = saved.captured.cancelReason,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.acceptAssignment(AcceptAssignmentCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ACCEPTED)
            verify { eventPublisher.publish("Dispatch", "10", "DispatchAcceptedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.dispatch.accepted", "via" to "assignment") }
        }

        @Test
        fun `배정받지 않은 캐리어가 수락하면 DispatchNotOwnedException 이 발생한다`() {
            every { dispatchPersistencePort.findById(1L) } returns assignedDispatch() // carrierId=200L

            assertThatThrownBy { sut.acceptAssignment(AcceptAssignmentCommand(1L, 999L)) }
                .isInstanceOf(DispatchNotOwnedException::class.java)
        }

        @Test
        fun `같은 캐리어가 다시 수락하면 이벤트를 재발행하지 않고 현재 상태를 반환한다`() {
            val dispatch = assignedDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            stubSaveEcho()

            sut.acceptAssignment(AcceptAssignmentCommand(1L, 200L))
            val result = sut.acceptAssignment(AcceptAssignmentCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.ACCEPTED)
            verify(exactly = 1) {
                eventPublisher.publish("Dispatch", "10", "DispatchAcceptedEvent", any(), any())
            }
        }
    }

    @Nested
    inner class CancelDispatch {

        @Test
        fun `취소하면 CANCELLED로 전이하고 DispatchCancelledEvent를 발행한다`() {
            every { dispatchPersistencePort.findById(1L) } returns pendingDispatch()

            sut.cancelDispatch(CancelDispatchCommand(1L, "주문 취소"))

            verify {
                eventPublisher.publish(
                    "Dispatch", "10", "DispatchCancelledEvent",
                    DispatchCancelledEvent(dispatchId = 1L, orderId = 10L, reason = "주문 취소"), any(),
                )
            }
        }

        @Test
        fun `이미 취소된 배차를 다시 취소하면 이벤트를 재발행하지 않는다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch

            sut.cancelDispatch(CancelDispatchCommand(1L, "주문 취소"))
            sut.cancelDispatch(CancelDispatchCommand(1L, "재취소"))

            verify(exactly = 1) {
                eventPublisher.publish("Dispatch", "10", "DispatchCancelledEvent", any(), any())
            }
        }
    }

    @Nested
    inner class RejectAssignment {

        @Test
        fun `캐리어가 지정된 배차를 거절하면 PENDING으로 돌아가고 패널티가 기록된다`() {
            val dispatch = assignedDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            val saved = slot<Dispatch>()
            every { dispatchPersistencePort.save(capture(saved)) } answers {
                Dispatch.reconstitute(
                    id = 1L, orderId = saved.captured.orderId, laundromatId = saved.captured.laundromatId,
                    status = saved.captured.status, carrierId = saved.captured.carrierId,
                    areaCode = saved.captured.areaCode, desiredPickupAt = saved.captured.desiredPickupAt,
                    assignedBy = saved.captured.assignedBy, assignedAt = saved.captured.assignedAt,
                    acceptedAt = saved.captured.acceptedAt, cancelReason = saved.captured.cancelReason,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.rejectAssignment(RejectAssignmentCommand(1L, 200L))

            assertThat(result.status).isEqualTo(DispatchStatus.PENDING)
            assertThat(result.carrierId).isNull()
            verify { penaltyRecordPersistencePort.save(any()) }
            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
            verify { metrics.incrementCounter("carry.dispatch.rejected") }
        }
    }

    @Nested
    inner class TimeoutDispatch {

        @Test
        fun `PENDING 배차를 타임아웃하면 TIMEOUT으로 전이하고 DispatchTimeoutEvent를 발행하며 타임아웃 카운터를 증가시킨다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            val saved = slot<Dispatch>()
            every { dispatchPersistencePort.save(capture(saved)) } answers {
                Dispatch.reconstitute(
                    id = 1L, orderId = saved.captured.orderId, laundromatId = saved.captured.laundromatId,
                    status = saved.captured.status, carrierId = saved.captured.carrierId,
                    areaCode = saved.captured.areaCode, desiredPickupAt = saved.captured.desiredPickupAt,
                    assignedBy = saved.captured.assignedBy, assignedAt = saved.captured.assignedAt,
                    acceptedAt = saved.captured.acceptedAt, cancelReason = saved.captured.cancelReason,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.timeoutDispatch(1L)

            assertThat(result.status).isEqualTo(DispatchStatus.TIMEOUT)
            // 같은 키(orderId) 파티셔닝 + Order 사가가 onDispatchTimeout 으로 소비.
            verify {
                eventPublisher.publish(
                    "Dispatch", "10", "DispatchTimeoutEvent",
                    DispatchTimeoutEvent(dispatchId = 1L, orderId = 10L), any(),
                )
            }
            verify { metrics.incrementCounter("carry.dispatch.timeout") }
        }

        @Test
        fun `PENDING이 아닌 배차 타임아웃 시도는 예외를 던지고 이벤트도 카운터도 발생하지 않는다`() {
            // 멀티 인스턴스 레이스로 이미 ACCEPTED 된 배차에 늦게 도착한 타임아웃 — 멱등하게 거부.
            every { dispatchPersistencePort.findById(1L) } returns assignedDispatch().also {
                it.acceptAssignment(now)
            }

            assertThatThrownBy { sut.timeoutDispatch(1L) }
                .isInstanceOf(DispatchTimeoutNotAllowedException::class.java)

            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { metrics.incrementCounter("carry.dispatch.timeout") }
        }

        @Test
        fun `이미 TIMEOUT인 배차를 다시 타임아웃하면 이벤트도 카운터도 재발생하지 않는다`() {
            val dispatch = pendingDispatch()
            every { dispatchPersistencePort.findById(1L) } returns dispatch
            stubSaveEcho()

            sut.timeoutDispatch(1L)
            val result = sut.timeoutDispatch(1L)

            assertThat(result.status).isEqualTo(DispatchStatus.TIMEOUT)
            verify(exactly = 1) {
                eventPublisher.publish("Dispatch", "10", "DispatchTimeoutEvent", any(), any())
            }
            verify(exactly = 1) { metrics.incrementCounter("carry.dispatch.timeout") }
        }
    }
}
