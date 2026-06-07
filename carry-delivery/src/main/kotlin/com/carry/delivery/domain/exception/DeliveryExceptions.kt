package com.carry.delivery.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.delivery.domain.vo.DeliveryStatus

class DeliveryNotFoundException(deliveryId: Long) : BusinessException(
    ErrorCode.DELIVERY_NOT_FOUND,
    "배달을 찾을 수 없습니다: $deliveryId",
)

class DeliveryNotInExpectedStatusException(deliveryId: Long?, currentStatus: DeliveryStatus, expectedStatus: DeliveryStatus) : BusinessException(
    ErrorCode.DELIVERY_INVALID_STATUS,
    "배달 상태 전이가 유효하지 않습니다: deliveryId=$deliveryId, current=$currentStatus, target=$expectedStatus",
)

class DeliveryWeightRequiredException : BusinessException(
    ErrorCode.DELIVERY_WEIGHT_REQUIRED,
    "수거 무게는 0보다 커야 합니다",
)

class DeliveryPhotoRequiredException : BusinessException(
    ErrorCode.DELIVERY_PHOTO_REQUIRED,
    "사진이 최소 1장 필요합니다",
)

class OrderNotPaidException(orderId: Long) : BusinessException(
    ErrorCode.ORDER_NOT_PAID,
    "주문이 결제되지 않았습니다: $orderId",
)

class DeliveryNotOwnedException(deliveryId: Long, requestingCarrierId: Long) : BusinessException(
    ErrorCode.DELIVERY_NOT_OWNED,
    "본인에게 배정된 배달이 아닙니다: deliveryId=$deliveryId, carrierId=$requestingCarrierId",
)
