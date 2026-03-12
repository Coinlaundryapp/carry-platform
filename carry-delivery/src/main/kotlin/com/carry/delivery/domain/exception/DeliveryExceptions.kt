package com.carry.delivery.domain.exception

import com.carry.delivery.domain.vo.DeliveryStatus

class DeliveryNotFoundException(deliveryId: Long) :
    RuntimeException("배달을 찾을 수 없습니다: $deliveryId")

class DeliveryNotInExpectedStatusException(deliveryId: Long?, currentStatus: DeliveryStatus, expectedStatus: DeliveryStatus) :
    RuntimeException("배달 상태 전이가 유효하지 않습니다: deliveryId=$deliveryId, current=$currentStatus, target=$expectedStatus")

class DeliveryWeightRequiredException :
    RuntimeException("수거 무게는 0보다 커야 합니다")

class DeliveryPhotoRequiredException :
    RuntimeException("사진이 최소 1장 필요합니다")

class OrderNotPaidException(orderId: Long) :
    RuntimeException("주문이 결제되지 않았습니다: $orderId")
