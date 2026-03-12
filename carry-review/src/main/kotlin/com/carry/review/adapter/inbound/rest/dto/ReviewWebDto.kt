package com.carry.review.adapter.inbound.rest.dto

import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewStatistics
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

data class CreateReviewRequest(
    val laundromatId: Long,
    val comment: String?,
    @field:Min(1) @field:Max(5) val rating: Int,
    val mediaUrls: List<String> = emptyList(),
)

data class UpdateReviewRequest(
    val comment: String?,
    @field:Min(1) @field:Max(5) val rating: Int,
)

data class ReviewResponse(
    val id: Long,
    val laundromatId: Long,
    val customerId: Long,
    val comment: String?,
    val rating: Int,
    val mediaUrls: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(review: Review) = ReviewResponse(
            id = review.id!!,
            laundromatId = review.laundromatId,
            customerId = review.customerId,
            comment = review.comment,
            rating = review.rating.value,
            mediaUrls = review.mediaUrls,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
        )
    }
}

data class ReviewStatisticsResponse(
    val laundromatId: Long,
    val totalCount: Long,
    val averageRating: Double,
) {
    companion object {
        fun from(statistics: ReviewStatistics) = ReviewStatisticsResponse(
            laundromatId = statistics.laundromatId,
            totalCount = statistics.totalCount,
            averageRating = statistics.averageRating,
        )
    }
}
