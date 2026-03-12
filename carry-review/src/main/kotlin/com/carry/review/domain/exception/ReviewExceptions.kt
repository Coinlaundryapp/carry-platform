package com.carry.review.domain.exception

class ReviewNotFoundException(reviewId: Long) :
    RuntimeException("리뷰를 찾을 수 없습니다: $reviewId")

class ReviewNotOwnedException(reviewId: Long, customerId: Long) :
    RuntimeException("리뷰에 대한 권한이 없습니다: reviewId=$reviewId, customerId=$customerId")
