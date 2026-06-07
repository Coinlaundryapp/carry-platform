package com.carry.order.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.order.domain.vo.OrderStatus

class OrderNotFoundException(orderId: Long) : BusinessException(
    ErrorCode.ORDER_NOT_FOUND,
    "주문을 찾을 수 없습니다: $orderId",
)

class InvalidOrderStatusTransitionException(from: OrderStatus, to: OrderStatus) : BusinessException(
    ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
    "주문 상태 전이가 유효하지 않습니다: $from → $to",
)

class OrderNotCancellableException(orderId: Long?, currentStatus: OrderStatus) : BusinessException(
    ErrorCode.ORDER_NOT_CANCELLABLE,
    "주문을 취소할 수 없는 상태입니다: orderId=$orderId, status=$currentStatus",
)

class OrderNotOwnedException(orderId: Long, requestingUserId: Long) : BusinessException(
    ErrorCode.ORDER_NOT_OWNED,
    "주문에 대한 권한이 없습니다: orderId=$orderId, userId=$requestingUserId",
)
