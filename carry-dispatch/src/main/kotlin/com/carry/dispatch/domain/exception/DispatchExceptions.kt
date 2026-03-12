package com.carry.dispatch.domain.exception

class DispatchNotFoundException(dispatchId: Long) :
    RuntimeException("배차를 찾을 수 없습니다: $dispatchId")

class DispatchNotPendingException :
    RuntimeException("배차가 대기 상태가 아닙니다")

class DispatchAlreadyAcceptedException :
    RuntimeException("이미 수락된 배차입니다")

class CarrierNotInAreaException(carrierId: Long, areaCode: String) :
    RuntimeException("캐리어가 해당 구역에 등록되어 있지 않습니다: carrierId=$carrierId, areaCode=$areaCode")

class CarrierAreaNotFoundException(carrierId: Long, areaCode: String) :
    RuntimeException("캐리어 구역을 찾을 수 없습니다: carrierId=$carrierId, areaCode=$areaCode")
