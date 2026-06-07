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
import com.carry.common.metrics.MetricsPort
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.port.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DispatchCommandService(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val carrierAreaPersistencePort: CarrierAreaPersistencePort,
    private val penaltyRecordPersistencePort: PenaltyRecordPersistencePort,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
) : DispatchCommandUseCase {

    @Transactional
    override fun claimDispatch(command: ClaimDispatchCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)

        val carrierArea = carrierAreaPersistencePort.findByCarrierIdAndAreaCode(command.carrierId, dispatch.areaCode)
        if (carrierArea == null || !carrierArea.active) {
            throw CarrierNotInAreaException(command.carrierId, dispatch.areaCode)
        }

        dispatch.claimByCarrier(command.carrierId)
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
        dispatch.assignByCoordinator(command.carrierId)
        return dispatchPersistencePort.save(dispatch)
    }

    @Transactional
    override fun acceptAssignment(command: AcceptAssignmentCommand): Dispatch {
        val dispatch = findDispatch(command.dispatchId)
        if (dispatch.carrierId != command.carrierId) {
            throw DispatchNotOwnedException(command.dispatchId, command.carrierId)
        }
        dispatch.acceptAssignment()
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
        val penaltyRecord = dispatch.rejectAssignment()
        val saved = dispatchPersistencePort.save(dispatch)
        penaltyRecordPersistencePort.save(penaltyRecord)
        metrics.incrementCounter("carry.dispatch.rejected")
        return saved
    }

    @Transactional
    override fun cancelDispatch(command: CancelDispatchCommand) {
        val dispatch = findDispatch(command.dispatchId)
        dispatch.cancel(command.reason)
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

    private fun findDispatch(dispatchId: Long): Dispatch {
        return dispatchPersistencePort.findById(dispatchId) ?: throw DispatchNotFoundException(dispatchId)
    }
}
