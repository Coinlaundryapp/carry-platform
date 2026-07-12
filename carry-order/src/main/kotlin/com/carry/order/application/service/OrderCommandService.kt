package com.carry.order.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.metrics.MetricsPort
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.SelectedOptionDto
import com.carry.event.order.ShippingAddressDto
import com.carry.event.port.EventPublisherPort
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.outbound.BillingQueryPort
import com.carry.order.application.port.outbound.IdempotencyPort
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.exception.OrderNotOwnedException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class OrderCommandService(
    private val orderPersistencePort: OrderPersistencePort,
    private val userQueryPort: UserQueryPort,
    private val laundromatQueryPort: LaundromatQueryPort,
    private val serviceAvailabilityQueryPort: ServiceAvailabilityQueryPort,
    private val billingQueryPort: BillingQueryPort,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
    private val auditPort: AuditPort,
    private val idempotencyPort: IdempotencyPort,
    private val clock: Clock,
) : OrderCommandUseCase {

    @Transactional
    override fun createOrder(command: CreateOrderCommand): Order {
        val key = command.idempotencyKey
        if (key != null) {
            // 이미 완료된 동일 키 → 새로 만들지 않고 기존 주문을 재생.
            idempotencyPort.findCompletedOrderId(key)?.let { return findOrder(it) }
            // 선점 실패 = 같은 키가 진행 중(또는 동시 요청 레이스의 패자) → 409.
            if (!idempotencyPort.reserve(key)) {
                throw BusinessException(
                    ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS,
                    "동일한 Idempotency-Key 요청이 이미 진행 중입니다: $key",
                )
            }
        }

        // 주문 생성 전제조건: 존재하는 주문은 결제 때문에 멈추지 않는다 — 그 대가로 생성 시점에 지불수단을 확보한다.
        if (!billingQueryPort.hasActiveBillingKey(command.customerId)) {
            throw BusinessException(ErrorCode.BILLING_KEY_REQUIRED, "customerId=${command.customerId}")
        }
        if (billingQueryPort.hasOverdueInvoice(command.customerId)) {
            throw BusinessException(ErrorCode.OVERDUE_INVOICE_EXISTS, "customerId=${command.customerId}")
        }

        val address = userQueryPort.getShippingAddress(command.customerId, command.shippingAddressId)
        if (!laundromatQueryPort.existsById(command.laundromatId)) {
            throw BusinessException(ErrorCode.LAUNDROMAT_NOT_FOUND, "세탁소를 찾을 수 없습니다: ${command.laundromatId}")
        }
        serviceAvailabilityQueryPort.checkAvailability(address.areaCode, command.desiredPickupAt, command.desiredDeliveryAt)

        val order = Order.create(
            customerId = command.customerId,
            laundromatId = command.laundromatId,
            laundryItemType = command.laundryItemType,
            selectedOptions = command.selectedOptions.map { SelectedOption(it.optionType, it.subOptionType) },
            shippingAddress = address,
            desiredPickupAt = command.desiredPickupAt,
            desiredDeliveryAt = command.desiredDeliveryAt,
            now = clock.instant(),
        )

        val saved = orderPersistencePort.save(order)

        eventPublisher.publish(
            aggregateType = "Order",
            aggregateId = saved.id.toString(),
            eventType = "OrderCreatedEvent",
            payload = OrderCreatedEvent(
                orderId = saved.id!!,
                customerId = saved.customerId,
                laundromatId = saved.laundromatId,
                laundryItemType = saved.laundryItemType,
                selectedOptions = saved.selectedOptions.map { SelectedOptionDto(it.optionType, it.subOptionType) },
                shippingAddress = ShippingAddressDto(
                    roadAddress = address.roadAddress,
                    detailAddress = address.detailAddress,
                    latitude = address.latitude,
                    longitude = address.longitude,
                    recipientName = address.recipientName,
                    recipientPhone = address.recipientPhone,
                ),
                desiredPickupAt = saved.desiredPickupAt,
                desiredDeliveryAt = saved.desiredDeliveryAt,
                areaCode = address.areaCode,
            ),
        )

        // 결과를 긴 TTL로 저장 → 이후 동일 키 요청은 위 findCompletedOrderId 분기로 재생된다.
        if (key != null) {
            idempotencyPort.complete(key, saved.id!!)
        }

        metrics.incrementCounter("carry.order.created")
        return saved
    }

    private fun findOrder(orderId: Long): Order =
        orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)

    // 내부/코디네이터/시스템 등 다중 액터용 (cancelledBy 명시).
    // 수거 후 취소도 이 경로를 그대로 탄다 — 결제 환불/과금중단은 OrderCancelledEvent 를 구독하는
    // carry-payment 모듈의 소관이며, 주문 도메인은 isCancellableBy 가드로 행위자별 취소 가능 여부만 판단한다.
    @Transactional
    override fun cancelOrder(orderId: Long, reason: String, cancelledBy: String) {
        val order = orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
        val by = parseCancelledBy(cancelledBy)
        val beforeStatus = order.status
        doCancel(order, reason, by)
        recordCancelAudit(orderId, beforeStatus, order, reason, by)
    }

    // 고객 본인 취소: 소유권 검증 후 CUSTOMER 로 취소.
    @Transactional
    override fun cancelOrderByCustomer(orderId: Long, requestingUserId: Long, reason: String) {
        val order = orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
        if (order.customerId != requestingUserId) {
            throw OrderNotOwnedException(orderId, requestingUserId)
        }
        val beforeStatus = order.status
        doCancel(order, reason, CancelledBy.CUSTOMER)
        recordCancelAudit(orderId, beforeStatus, order, reason, CancelledBy.CUSTOMER)
    }

    private fun doCancel(order: Order, reason: String, by: CancelledBy) {
        order.cancel(reason, by, clock.instant())
        orderPersistencePort.save(order)
        publishOrderCancelled(order, reason, by)
    }

    // raw valueOf 실패는 catch-all 에 걸려 500 — 클라이언트 잘못이므로 400 으로 매핑.
    private fun parseCancelledBy(value: String): CancelledBy =
        runCatching { CancelledBy.valueOf(value) }.getOrElse {
            throw BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 취소 주체입니다: $value")
        }

    // 민감 작업 감사. actor는 어댑터가 ambient 수집(코디=userId, 고객=userId, saga=SYSTEM).
    private fun recordCancelAudit(orderId: Long, beforeStatus: OrderStatus, order: Order, reason: String, by: CancelledBy) {
        auditPort.record(
            action = AuditAction.ORDER_CANCEL,
            targetType = "ORDER",
            targetId = orderId.toString(),
            before = mapOf("status" to beforeStatus.name),
            after = mapOf("status" to order.status.name, "reason" to reason, "cancelledBy" to by.name),
        )
    }

    private fun publishOrderCancelled(order: Order, reason: String, by: CancelledBy) {
        eventPublisher.publish(
            aggregateType = "Order",
            aggregateId = order.id.toString(),
            eventType = "OrderCancelledEvent",
            payload = OrderCancelledEvent(order.id!!, reason, by.name),
        )

        // `reason`은 자유 텍스트라 태그로 부적합(고카디널리티). 취소 주체(by)만 enum값으로 태깅.
        metrics.incrementCounter("carry.order.cancelled", "by" to by.name)
    }
}
