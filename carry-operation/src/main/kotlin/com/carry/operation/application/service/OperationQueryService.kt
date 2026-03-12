package com.carry.operation.application.service

import com.carry.operation.application.port.inbound.OperationQueryUseCase
import com.carry.operation.application.port.outbound.OperationEventPersistencePort
import com.carry.operation.domain.model.OperationEvent
import com.carry.operation.domain.model.OperationSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class OperationQueryService(
    private val operationEventPersistencePort: OperationEventPersistencePort,
) : OperationQueryUseCase {

    override fun getSummary(): OperationSummary {
        val startOfToday = LocalDate.now(ZoneId.of("Asia/Seoul"))
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toInstant()

        return OperationSummary(
            totalOrdersToday = operationEventPersistencePort
                .countByEventTypeAndCreatedAtAfter("OrderCreatedEvent", startOfToday),
            pendingDispatches = operationEventPersistencePort
                .countByEventTypeAndCreatedAtAfter("OrderCreatedEvent", startOfToday) -
                operationEventPersistencePort
                    .countByEventTypeAndCreatedAtAfter("DispatchAcceptedEvent", startOfToday) -
                operationEventPersistencePort
                    .countByEventTypeAndCreatedAtAfter("OrderCancelledEvent", startOfToday),
            activeDeliveries = operationEventPersistencePort
                .countByEventTypeAndCreatedAtAfter("DispatchAcceptedEvent", startOfToday) -
                operationEventPersistencePort
                    .countByEventTypeAndCreatedAtAfter("DeliveryCompletedEvent", startOfToday),
            completedToday = operationEventPersistencePort
                .countByEventTypeAndCreatedAtAfter("DeliveryCompletedEvent", startOfToday),
            cancelledToday = operationEventPersistencePort
                .countByEventTypeAndCreatedAtAfter("OrderCancelledEvent", startOfToday),
        )
    }

    override fun getRecentEvents(limit: Int): List<OperationEvent> {
        return operationEventPersistencePort.findRecent(limit)
    }
}
