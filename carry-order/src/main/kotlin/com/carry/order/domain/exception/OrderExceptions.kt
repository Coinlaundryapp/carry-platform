package com.carry.order.domain.exception

import com.carry.order.domain.vo.OrderStatus

class OrderNotFoundException(orderId: Long) :
    RuntimeException("주문을 찾을 수 없습니다: $orderId")

class InvalidOrderStatusTransitionException(from: OrderStatus, to: OrderStatus) :
    RuntimeException("주문 상태 전이가 유효하지 않습니다: $from → $to")

class OrderNotCancellableException(orderId: Long?, currentStatus: OrderStatus) :
    RuntimeException("주문을 취소할 수 없는 상태입니다: orderId=$orderId, status=$currentStatus")
