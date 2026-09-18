package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.AcceptAssignmentCommand
import com.carry.dispatch.application.port.inbound.AssignDispatchCommand
import com.carry.dispatch.application.port.inbound.CancelDispatchCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.RejectAssignmentCommand
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.application.port.outbound.PenaltyRecordPersistencePort
import com.carry.dispatch.domain.exception.CarrierNotInAreaException
import com.carry.dispatch.domain.exception.DispatchNotFoundException
import com.carry.dispatch.domain.exception.DispatchNotOwnedException
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.metrics.MetricsPort
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.port.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class DispatchCommandService(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val carrierAreaPersistencePort: CarrierAreaPersistencePort,
    private val penaltyRecordPersistencePort: PenaltyRecordPersistencePort,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
    private val auditPort: AuditPort,
    private val clock: Clock,
) : DispatchCommandUseCase {

    @Transactional
    override fun claimDispatch(command: ClaimDispatchCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)

        // 구역 검증은 **실제 선점(PENDING)** 경로에서만 — 이미 소유한 캐리어의 멱등 재시도가
        // 구역 비활성으로 막히지 않게 한다.
        if (dispatch.status == DispatchStatus.PENDING) {
            val carrierArea = carrierAreaPersistencePort.findByCarrierIdAndAreaCode(command.carrierId, dispatch.areaCode)
            if (carrierArea == null || !carrierArea.active) {
                throw CarrierNotInAreaException(command.carrierId, dispatch.areaCode)
            }
        }

        val transitioned = dispatch.claimByCarrier(command.carrierId, clock.instant())
        if (!transitioned) return dispatch // 멱등 재시도 → 현재 상태 반환, 발행·메트릭 억제.

        val saved = dispatchPersistencePort.save(dispatch)

        eventPublisher.publish(
            aggregateType = "Dispatch",
            aggregateId = saved.orderId.toString(),
            eventType = "DispatchAcceptedEvent",
            payload = DispatchAcceptedEvent(
                dispatchId = saved.id!!,
                orderId = saved.orderId,
                carrierId = command.carrierId,
                laundromatId = saved.laundromatId,
            ),
        )

        // `via=claim`: 캐리어 본인이 PENDING 배차를 직접 잡은 경로.
        metrics.incrementCounter("carry.dispatch.accepted", "via" to "claim")
        return saved
    }

    @Transactional
    override fun assignDispatch(command: AssignDispatchCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)
        val beforeCarrier = dispatch.carrierId
        val beforeStatus = dispatch.status
        val transitioned = dispatch.assignByCoordinator(command.carrierId, clock.instant())
        if (!transitioned) return dispatch // 멱등 재시도 → 현재 상태 반환, audit 억제.
        val saved = dispatchPersistencePort.save(dispatch)
        auditPort.record(
            action = AuditAction.DISPATCH_ASSIGN,
            targetType = "DISPATCH",
            targetId = command.dispatchId.toString(),
            before = mapOf("carrierId" to beforeCarrier, "status" to beforeStatus.name),
            after = mapOf("carrierId" to saved.carrierId, "status" to saved.status.name),
        )
        return saved
    }

    @Transactional
    override fun acceptAssignment(command: AcceptAssignmentCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)
        if (dispatch.carrierId != command.carrierId) {
            throw DispatchNotOwnedException(command.dispatchId, command.carrierId)
        }
        val transitioned = dispatch.acceptAssignment(clock.instant())
        if (!transitioned) return dispatch // 멱등 재시도 → 현재 상태 반환, 발행·메트릭 억제.
        val saved = dispatchPersistencePort.save(dispatch)

        eventPublisher.publish(
            aggregateType = "Dispatch",
            aggregateId = saved.orderId.toString(),
            eventType = "DispatchAcceptedEvent",
            payload = DispatchAcceptedEvent(
                dispatchId = saved.id!!,
                orderId = saved.orderId,
                carrierId = saved.carrierId!!,
                laundromatId = saved.laundromatId,
            ),
        )

        // `via=assignment`: 코디네이터가 ASSIGNED로 지정한 배차를 캐리어가 수락한 경로.
        metrics.incrementCounter("carry.dispatch.accepted", "via" to "assignment")
        return saved
    }

    @Transactional
    override fun rejectAssignment(command: RejectAssignmentCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)
        if (dispatch.carrierId != command.carrierId) {
            throw DispatchNotOwnedException(command.dispatchId, command.carrierId)
        }
        val beforeStatus = dispatch.status
        val penaltyRecord = dispatch.rejectAssignment(clock.instant())
        val saved = dispatchPersistencePort.save(dispatch)
        penaltyRecordPersistencePort.save(penaltyRecord)
        metrics.incrementCounter("carry.dispatch.rejected")
        auditPort.record(
            action = AuditAction.DISPATCH_REJECT_PENALTY,
            targetType = "DISPATCH",
            targetId = command.dispatchId.toString(),
            before = mapOf("carrierId" to command.carrierId, "status" to beforeStatus.name),
            after = mapOf("status" to saved.status.name, "penaltyReason" to penaltyRecord.reason.name),
        )
        return saved
    }

    @Transactional
    override fun cancelDispatch(command: CancelDispatchCommand) {
        val dispatch = findDispatch(command.dispatchId)
        val transitioned = dispatch.cancel(command.reason)
        if (!transitioned) return // 이미 취소됨 → 멱등 no-op, 발행 억제.
        dispatchPersistencePort.save(dispatch)

        eventPublisher.publish(
            aggregateType = "Dispatch",
            aggregateId = dispatch.orderId.toString(),
            eventType = "DispatchCancelledEvent",
            payload = DispatchCancelledEvent(
                dispatchId = dispatch.id!!,
                orderId = dispatch.orderId,
                reason = command.reason,
            ),
        )
    }

    @Transactional
    override fun timeoutDispatch(dispatchId: Long): Dispatch {
        val dispatch = findDispatch(dispatchId)
        // PENDING 이 아니면(ACCEPTED 등) 도메인이 DispatchTimeoutNotAllowedException 을 던진다 →
        // 멀티 인스턴스 레이스로 이미 종결된 배차는 호출 측(스위퍼)이 건너뛴다.
        // 이미 TIMEOUT이면 멱등 no-op(false) → 현재 상태 반환, 발행·메트릭 억제.
        val transitioned = dispatch.timeout()
        if (!transitioned) return dispatch
        val saved = dispatchPersistencePort.save(dispatch)

        eventPublisher.publish(
            aggregateType = "Dispatch",
            aggregateId = saved.orderId.toString(),
            eventType = "DispatchTimeoutEvent",
            payload = DispatchTimeoutEvent(
                dispatchId = saved.id!!,
                orderId = saved.orderId,
            ),
        )

        metrics.incrementCounter("carry.dispatch.timeout")
        return saved
    }

    private fun findDispatch(dispatchId: Long): Dispatch {
        return dispatchPersistencePort.findById(dispatchId) ?: throw DispatchNotFoundException(dispatchId)
    }
}
