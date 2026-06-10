package com.carry.review.application.port.inbound

import com.carry.review.domain.model.Review

data class CreateReviewCommand(
    val laundromatId: Long,
    val customerId: Long,
    val comment: String?,
    val rating: Int,
    val mediaUrls: List<String> = emptyList(),
    val idempotencyKey: String? = null,
)

data class UpdateReviewCommand(
    val reviewId: Long,
    val customerId: Long,
    val comment: String?,
    val rating: Int,
)

interface ReviewCommandUseCase {
    fun createReview(command: CreateReviewCommand): Review
    fun updateReview(command: UpdateReviewCommand): Review
    fun deleteReview(reviewId: Long, customerId: Long)
}
