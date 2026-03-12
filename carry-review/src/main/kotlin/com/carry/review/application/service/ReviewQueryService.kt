package com.carry.review.application.service

import com.carry.review.application.port.inbound.ReviewQueryUseCase
import com.carry.review.application.port.outbound.ReviewPersistencePort
import com.carry.review.domain.exception.ReviewNotFoundException
import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewStatistics
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewQueryService(
    private val reviewPersistencePort: ReviewPersistencePort,
) : ReviewQueryUseCase {

    override fun getReview(reviewId: Long): Review {
        return reviewPersistencePort.findById(reviewId)
            ?: throw ReviewNotFoundException(reviewId)
    }

    override fun getReviewsByLaundromat(laundromatId: Long, cursor: Long?, size: Int): List<Review> {
        return reviewPersistencePort.findByLaundromatId(laundromatId, cursor, size)
    }

    override fun getReviewsByCustomer(customerId: Long, cursor: Long?, size: Int): List<Review> {
        return reviewPersistencePort.findByCustomerId(customerId, cursor, size)
    }

    override fun getStatistics(laundromatId: Long): ReviewStatistics {
        val totalCount = reviewPersistencePort.countByLaundromatId(laundromatId)
        val averageRating = reviewPersistencePort.averageRatingByLaundromatId(laundromatId)

        return ReviewStatistics(
            laundromatId = laundromatId,
            totalCount = totalCount,
            averageRating = averageRating,
        )
    }
}
