package com.carry.review.application.port.inbound

import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewStatistics

interface ReviewQueryUseCase {
    fun getReview(reviewId: Long): Review
    fun getReviewsByLaundromat(laundromatId: Long, cursor: Long?, size: Int): List<Review>
    fun getReviewsByCustomer(customerId: Long, cursor: Long?, size: Int): List<Review>
    fun getStatistics(laundromatId: Long): ReviewStatistics
}
