package com.carry.review.adapter.inbound.rest

import com.carry.review.adapter.inbound.rest.dto.CreateReviewRequest
import com.carry.review.adapter.inbound.rest.dto.ReviewResponse
import com.carry.review.adapter.inbound.rest.dto.ReviewStatisticsResponse
import com.carry.review.adapter.inbound.rest.dto.UpdateReviewRequest
import com.carry.review.application.port.inbound.CreateReviewCommand
import com.carry.review.application.port.inbound.ReviewCommandUseCase
import com.carry.review.application.port.inbound.ReviewQueryUseCase
import com.carry.review.application.port.inbound.UpdateReviewCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/reviews")
class ReviewController(
    private val reviewCommandUseCase: ReviewCommandUseCase,
    private val reviewQueryUseCase: ReviewQueryUseCase,
) {

    @PostMapping
    fun createReview(
        @RequestParam customerId: Long, // TODO: JWT에서 추출
        @RequestBody request: CreateReviewRequest,
    ): ResponseEntity<ReviewResponse> {
        val command = CreateReviewCommand(
            laundromatId = request.laundromatId,
            customerId = customerId,
            comment = request.comment,
            rating = request.rating,
            mediaUrls = request.mediaUrls,
        )
        val review = reviewCommandUseCase.createReview(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review))
    }

    @PutMapping("/{reviewId}")
    fun updateReview(
        @PathVariable reviewId: Long,
        @RequestParam customerId: Long, // TODO: JWT에서 추출
        @RequestBody request: UpdateReviewRequest,
    ): ResponseEntity<ReviewResponse> {
        val command = UpdateReviewCommand(
            reviewId = reviewId,
            customerId = customerId,
            comment = request.comment,
            rating = request.rating,
        )
        val review = reviewCommandUseCase.updateReview(command)
        return ResponseEntity.ok(ReviewResponse.from(review))
    }

    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        @RequestParam customerId: Long, // TODO: JWT에서 추출
    ): ResponseEntity<Void> {
        reviewCommandUseCase.deleteReview(reviewId, customerId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{reviewId}")
    fun getReview(@PathVariable reviewId: Long): ResponseEntity<ReviewResponse> {
        val review = reviewQueryUseCase.getReview(reviewId)
        return ResponseEntity.ok(ReviewResponse.from(review))
    }

    @GetMapping("/laundromat/{laundromatId}")
    fun getReviewsByLaundromat(
        @PathVariable laundromatId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<ReviewResponse>> {
        val reviews = reviewQueryUseCase.getReviewsByLaundromat(laundromatId, cursor, size)
        return ResponseEntity.ok(reviews.map { ReviewResponse.from(it) })
    }

    @GetMapping("/my")
    fun getMyReviews(
        @RequestParam customerId: Long, // TODO: JWT에서 추출
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<ReviewResponse>> {
        val reviews = reviewQueryUseCase.getReviewsByCustomer(customerId, cursor, size)
        return ResponseEntity.ok(reviews.map { ReviewResponse.from(it) })
    }

    @GetMapping("/laundromat/{laundromatId}/statistics")
    fun getStatistics(
        @PathVariable laundromatId: Long,
    ): ResponseEntity<ReviewStatisticsResponse> {
        val statistics = reviewQueryUseCase.getStatistics(laundromatId)
        return ResponseEntity.ok(ReviewStatisticsResponse.from(statistics))
    }
}
