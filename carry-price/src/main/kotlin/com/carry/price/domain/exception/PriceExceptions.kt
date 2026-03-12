package com.carry.price.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.price.domain.vo.PriceCondition

class PricePolicyNotFoundException(policyId: Long) :
    BusinessException(ErrorCode.NOT_FOUND, "가격 정책을 찾을 수 없습니다: $policyId")

class PricePolicyNotFoundForConditionException(condition: PriceCondition) :
    BusinessException(
        ErrorCode.NOT_FOUND,
        "해당 조건의 가격 정책을 찾을 수 없습니다: ${condition.orderUnitType}/${condition.orderRequestType}/${condition.laundryItemType}",
    )

class DuplicatePricePolicyException(condition: PriceCondition) :
    BusinessException(
        ErrorCode.INVALID_INPUT,
        "이미 존재하는 가격 정책입니다: ${condition.orderUnitType}/${condition.orderRequestType}/${condition.laundryItemType}",
    )
