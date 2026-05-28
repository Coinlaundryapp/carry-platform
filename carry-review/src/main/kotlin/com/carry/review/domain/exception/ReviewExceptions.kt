package com.carry.review.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class ReviewNotFoundException(reviewId: Long) : BusinessException(
    ErrorCode.REVIEW_NOT_FOUND,
    "리뷰를 찾을 수 없습니다: $reviewId",
)

class ReviewNotOwnedException(reviewId: Long, customerId: Long) : BusinessException(
    ErrorCode.REVIEW_NOT_OWNED,
    "리뷰에 대한 권한이 없습니다: reviewId=$reviewId, customerId=$customerId",
)
