package com.carry.user.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.user.domain.model.ShippingAddress

class UserNotFoundException(userId: Long) :
    BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

class InactiveUserException :
    BusinessException(ErrorCode.FORBIDDEN, "비활성 상태인 계정입니다")

class EmailAlreadyExistsException :
    BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다")

class ShippingAddressNotFoundException(addressId: Long) :
    BusinessException(ErrorCode.NOT_FOUND, "배송지를 찾을 수 없습니다: $addressId")

class ShippingAddressNotOwnedException :
    BusinessException(ErrorCode.FORBIDDEN, "본인의 배송지만 관리할 수 있습니다")

class ShippingAddressLimitExceededException :
    BusinessException(
        ErrorCode.INVALID_INPUT,
        "배송지는 최대 ${ShippingAddress.MAX_ADDRESSES_PER_USER}개까지 등록할 수 있습니다",
    )

class DefaultAddressDeletionException :
    BusinessException(ErrorCode.INVALID_INPUT, "기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 설정한 후 삭제해주세요")
