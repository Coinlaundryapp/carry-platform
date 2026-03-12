package com.carry.serviceavailability.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.serviceavailability.domain.vo.AreaStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ServiceAreaNotFoundException(areaCode: String) :
    BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND, "서비스 권역을 찾을 수 없습니다: $areaCode")

class ServiceAreaNotFoundByIdException(id: Long) :
    BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND, "서비스 권역을 찾을 수 없습니다: id=$id")

class DuplicateServiceAreaException(areaCode: String) :
    BusinessException(ErrorCode.CONFLICT, "이미 존재하는 서비스 권역입니다: $areaCode")

class AreaNotActiveException(areaCode: String, status: AreaStatus) :
    BusinessException(ErrorCode.AREA_NOT_ACTIVE, "서비스 불가 지역입니다: $areaCode (상태: $status)")

class HolidayException(areaCode: String, date: LocalDate, reason: String) :
    BusinessException(ErrorCode.HOLIDAY_CLOSED, "휴무일입니다: $areaCode ($date - $reason)")

class OutsideOperatingHoursException : BusinessException {
    constructor(areaCode: String, dayOfWeek: DayOfWeek) :
        super(ErrorCode.OUTSIDE_OPERATING_HOURS, "해당 요일에 운영하지 않습니다: $areaCode ($dayOfWeek)")

    constructor(areaCode: String, dayOfWeek: DayOfWeek, requestedTime: LocalTime, openTime: LocalTime, closeTime: LocalTime) :
        super(ErrorCode.OUTSIDE_OPERATING_HOURS, "운영 시간 외입니다: $areaCode ($dayOfWeek $openTime~$closeTime, 요청: $requestedTime)")
}
