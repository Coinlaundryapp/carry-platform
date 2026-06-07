package com.carry.review.application.service

import com.carry.event.port.EventPublisherPort
import com.carry.event.review.ReviewCreatedEvent
import com.carry.review.application.port.inbound.CreateReviewCommand
import com.carry.review.application.port.inbound.ReviewCommandUseCase
import com.carry.review.application.port.inbound.UpdateReviewCommand
import com.carry.review.application.port.outbound.ReviewPersistencePort
import com.carry.review.domain.exception.ReviewNotFoundException
import com.carry.review.domain.exception.ReviewNotOwnedException
import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewRating
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ReviewCommandService(
    private val reviewPersistencePort: ReviewPersistencePort,
    private val eventPublisher: EventPublisherPort,
    private val clock: Clock,
) : ReviewCommandUseCase {

    @Transactional
    override fun createReview(command: CreateReviewCommand): Review {
        val review = Review.create(
            laundromatId = command.laundromatId,
            customerId = command.customerId,
            comment = command.comment,
            rating = ReviewRating.fromValue(command.rating),
            mediaUrls = command.mediaUrls,
            now = clock.instant(),
        )

        val saved = reviewPersistencePort.save(review)

        eventPublisher.publish(
            aggregateType = "Review",
            aggregateId = saved.id.toString(),
            eventType = "ReviewCreatedEvent",
            payload = ReviewCreatedEvent(
                reviewId = saved.id!!,
                laundromatId = saved.laundromatId,
                customerId = saved.customerId,
                rating = saved.rating.value,
                comment = saved.comment,
            ),
        )

        return saved
    }

    @Transactional
    override fun updateReview(command: UpdateReviewCommand): Review {
        val review = reviewPersistencePort.findById(command.reviewId)
            ?: throw ReviewNotFoundException(command.reviewId)

        if (review.customerId != command.customerId) {
            throw ReviewNotOwnedException(command.reviewId, command.customerId)
        }

        review.update(
            comment = command.comment,
            rating = ReviewRating.fromValue(command.rating),
            now = clock.instant(),
        )

        return reviewPersistencePort.save(review)
    }

    @Transactional
    override fun deleteReview(reviewId: Long, customerId: Long) {
        val review = reviewPersistencePort.findById(reviewId)
            ?: throw ReviewNotFoundException(reviewId)

        if (review.customerId != customerId) {
            throw ReviewNotOwnedException(reviewId, customerId)
        }

        reviewPersistencePort.deleteById(reviewId)
    }
}
