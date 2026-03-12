package com.carry.laundromat.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class LaundromatNotFoundException(laundromatId: Long) :
    BusinessException(ErrorCode.NOT_FOUND, "세탁소를 찾을 수 없습니다: $laundromatId")

class MediaResourceNotFoundException(mediaResourceId: Long) :
    BusinessException(ErrorCode.NOT_FOUND, "미디어 리소스를 찾을 수 없습니다: $mediaResourceId")
