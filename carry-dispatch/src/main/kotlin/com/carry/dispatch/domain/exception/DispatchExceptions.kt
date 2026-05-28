package com.carry.dispatch.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.dispatch.domain.vo.DispatchStatus

class DispatchNotFoundException(dispatchId: Long) : BusinessException(
    ErrorCode.DISPATCH_NOT_FOUND,
    "배차를 찾을 수 없습니다: $dispatchId",
)

class DispatchNotCancellableException(currentStatus: DispatchStatus) : BusinessException(
    ErrorCode.DISPATCH_NOT_CANCELLABLE,
    "배차를 취소할 수 없는 상태입니다: $currentStatus",
)

class DispatchTimeoutNotAllowedException(currentStatus: DispatchStatus) : BusinessException(
    ErrorCode.DISPATCH_TIMEOUT_NOT_ALLOWED,
    "배차 타임아웃은 PENDING 상태에서만 가능합니다: $currentStatus",
)

class DispatchNotPendingException : BusinessException(
    ErrorCode.DISPATCH_NOT_PENDING,
    "배차가 대기 상태가 아닙니다",
)

class DispatchAlreadyAcceptedException : BusinessException(
    ErrorCode.DISPATCH_ALREADY_ACCEPTED,
    "이미 수락된 배차입니다",
)

class CarrierNotInAreaException(carrierId: Long, areaCode: String) : BusinessException(
    ErrorCode.CARRIER_NOT_IN_AREA,
    "캐리어가 해당 구역에 등록되어 있지 않습니다: carrierId=$carrierId, areaCode=$areaCode",
)

class CarrierAreaNotFoundException(carrierId: Long, areaCode: String) : BusinessException(
    ErrorCode.CARRIER_AREA_NOT_FOUND,
    "캐리어 구역을 찾을 수 없습니다: carrierId=$carrierId, areaCode=$areaCode",
)
