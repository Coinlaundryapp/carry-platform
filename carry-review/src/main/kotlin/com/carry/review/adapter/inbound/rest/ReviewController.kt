package com.carry.review.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.review.adapter.inbound.rest.dto.CreateReviewRequest
import com.carry.review.adapter.inbound.rest.dto.ReviewResponse
import com.carry.review.adapter.inbound.rest.dto.ReviewStatisticsResponse
import com.carry.review.adapter.inbound.rest.dto.UpdateReviewRequest
import com.carry.review.application.port.inbound.CreateReviewCommand
import com.carry.review.application.port.inbound.ReviewCommandUseCase
import com.carry.review.application.port.inbound.ReviewQueryUseCase
import com.carry.review.application.port.inbound.UpdateReviewCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Review", description = "리뷰 관리 API")
@RestController
@RequestMapping("/api/v2/reviews")
class ReviewController(
    private val reviewCommandUseCase: ReviewCommandUseCase,
    private val reviewQueryUseCase: ReviewQueryUseCase,
) {

    @Operation(
        summary = "리뷰 작성",
        description = "리뷰를 작성합니다. Idempotency-Key 헤더 제공 시 동일 키 재요청은 기존 리뷰를 재생합니다(중복 작성 방지).",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "리뷰 작성 성공"),
            SwaggerApiResponse(responseCode = "409", description = "이미 작성한 리뷰 / 동일 Idempotency-Key 요청 진행 중"),
        ],
    )
    @PostMapping
    fun createReview(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateReviewRequest,
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val command = CreateReviewCommand(
            laundromatId = request.laundromatId,
            customerId = userId,
            comment = request.comment,
            rating = request.rating,
            mediaUrls = request.mediaUrls,
            idempotencyKey = idempotencyKey,
        )
        val review = reviewCommandUseCase.createReview(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(ReviewResponse.from(review)))
    }

    @Operation(summary = "리뷰 수정")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "리뷰 수정 성공"),
            SwaggerApiResponse(responseCode = "403", description = "수정 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음"),
        ],
    )
    @PutMapping("/{reviewId}")
    fun updateReview(
        @PathVariable reviewId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateReviewRequest,
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val command = UpdateReviewCommand(
            reviewId = reviewId,
            customerId = userId,
            comment = request.comment,
            rating = request.rating,
        )
        val review = reviewCommandUseCase.updateReview(command)
        return ResponseEntity.ok(ApiResponse.success(ReviewResponse.from(review)))
    }

    @Operation(summary = "리뷰 삭제")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "204", description = "리뷰 삭제 성공"),
            SwaggerApiResponse(responseCode = "403", description = "삭제 권한 없음"),
        ],
    )
    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Void> {
        reviewCommandUseCase.deleteReview(reviewId, userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "리뷰 상세 조회")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "리뷰 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음"),
        ],
    )
    @GetMapping("/{reviewId}")
    fun getReview(@PathVariable reviewId: Long): ResponseEntity<ApiResponse<ReviewResponse>> {
        val review = reviewQueryUseCase.getReview(reviewId)
        return ResponseEntity.ok(ApiResponse.success(ReviewResponse.from(review)))
    }

    @Operation(summary = "세탁소 리뷰 목록 조회", description = "커서 기반 페이지네이션으로 세탁소 리뷰를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "리뷰 목록 조회 성공")])
    @GetMapping("/laundromat/{laundromatId}")
    fun getReviewsByLaundromat(
        @PathVariable laundromatId: Long,
        @Parameter(description = "커서 (마지막 리뷰 ID)") @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<ReviewResponse>>> {
        val reviews = reviewQueryUseCase.getReviewsByLaundromat(laundromatId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(reviews.map { ReviewResponse.from(it) }))
    }

    @Operation(summary = "내 리뷰 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "내 리뷰 목록 조회 성공")])
    @GetMapping("/my")
    fun getMyReviews(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<ReviewResponse>>> {
        val reviews = reviewQueryUseCase.getReviewsByCustomer(userId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(reviews.map { ReviewResponse.from(it) }))
    }

    @Operation(summary = "세탁소 리뷰 통계", description = "세탁소의 리뷰 총 개수와 평균 평점을 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "통계 조회 성공")])
    @GetMapping("/laundromat/{laundromatId}/statistics")
    fun getStatistics(
        @PathVariable laundromatId: Long,
    ): ResponseEntity<ApiResponse<ReviewStatisticsResponse>> {
        val statistics = reviewQueryUseCase.getStatistics(laundromatId)
        return ResponseEntity.ok(ApiResponse.success(ReviewStatisticsResponse.from(statistics)))
    }
}
