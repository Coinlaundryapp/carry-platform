package com.carry.review.adapter.inbound.rest.dto

import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewStatistics
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

@Schema(description = "리뷰 작성 요청")
data class CreateReviewRequest(
    @Schema(description = "세탁소 ID")
    val laundromatId: Long,
    @Schema(description = "리뷰 내용", nullable = true)
    val comment: String?,
    @Schema(description = "평점 (1-5)", example = "5")
    @field:Min(1) @field:Max(5) val rating: Int,
    @Schema(description = "미디어 URL 목록")
    val mediaUrls: List<String> = emptyList(),
)

@Schema(description = "리뷰 수정 요청")
data class UpdateReviewRequest(
    @Schema(description = "리뷰 내용", nullable = true)
    val comment: String?,
    @Schema(description = "평점 (1-5)", example = "5")
    @field:Min(1) @field:Max(5) val rating: Int,
)

@Schema(description = "리뷰 응답")
data class ReviewResponse(
    @Schema(description = "리뷰 ID") val id: Long,
    @Schema(description = "세탁소 ID") val laundromatId: Long,
    @Schema(description = "고객 ID") val customerId: Long,
    @Schema(description = "리뷰 내용", nullable = true) val comment: String?,
    @Schema(description = "평점") val rating: Int,
    @Schema(description = "미디어 URL 목록") val mediaUrls: List<String>,
    @Schema(description = "생성 시간") val createdAt: Instant,
    @Schema(description = "수정 시간") val updatedAt: Instant,
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

@Schema(description = "리뷰 통계 응답")
data class ReviewStatisticsResponse(
    @Schema(description = "세탁소 ID") val laundromatId: Long,
    @Schema(description = "총 리뷰 수") val totalCount: Long,
    @Schema(description = "평균 평점") val averageRating: Double,
) {
    companion object {
        fun from(statistics: ReviewStatistics) = ReviewStatisticsResponse(
            laundromatId = statistics.laundromatId,
            totalCount = statistics.totalCount,
            averageRating = statistics.averageRating,
        )
    }
}
