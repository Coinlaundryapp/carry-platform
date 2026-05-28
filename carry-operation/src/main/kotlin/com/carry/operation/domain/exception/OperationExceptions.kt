package com.carry.operation.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class TermNotFoundException(termId: Long) : BusinessException(
    ErrorCode.TERM_NOT_FOUND,
    "약관을 찾을 수 없습니다: $termId",
)
