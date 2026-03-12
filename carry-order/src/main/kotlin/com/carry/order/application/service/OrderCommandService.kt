package com.carry.order.application.service

import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.SelectedOptionDto
import com.carry.event.order.ShippingAddressDto
import com.carry.infra.kafka.outbox.OutboxEventPublisher
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.SelectedOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderCommandService(
    private val orderPersistencePort: OrderPersistencePort,
    private val userQueryPort: UserQueryPort,
    private val laundromatQueryPort: LaundromatQueryPort,
    private val outboxEventPublisher: OutboxEventPublisher,
) : OrderCommandUseCase {

    @Transactional
    override fun createOrder(command: CreateOrderCommand): Order {
        val address = userQueryPort.getShippingAddress(command.customerId, command.shippingAddressId)
        check(laundromatQueryPort.existsById(command.laundromatId)) { "세탁소를 찾을 수 없습니다: ${command.laundromatId}" }

        val order = Order.create(
            customerId = command.customerId,
            laundromatId = command.laundromatId,
            laundryItemType = command.laundryItemType,
            selectedOptions = command.selectedOptions.map { SelectedOption(it.optionType, it.subOptionType) },
            shippingAddress = address,
            desiredPickupAt = command.desiredPickupAt,
            desiredDeliveryAt = command.desiredDeliveryAt,
        )

        val saved = orderPersistencePort.save(order)

        outboxEventPublisher.publish(
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
                areaCode = command.areaCode,
            ),
        )

        return saved
    }

    @Transactional
    override fun cancelOrder(orderId: Long, reason: String, cancelledBy: String) {
        val order = orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
        val by = CancelledBy.valueOf(cancelledBy)
        order.cancel(reason, by)
        orderPersistencePort.save(order)

        outboxEventPublisher.publish(
            aggregateType = "Order",
            aggregateId = orderId.toString(),
            eventType = "OrderCancelledEvent",
            payload = OrderCancelledEvent(orderId, reason, cancelledBy),
        )
    }
}
