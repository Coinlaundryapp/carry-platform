package com.carry.review.adapter.outbound.persistence

import com.carry.review.adapter.outbound.persistence.entity.ReviewJpaEntity
import com.carry.review.adapter.outbound.persistence.repository.ReviewJpaRepository
import com.carry.review.application.port.outbound.ReviewPersistencePort
import com.carry.review.domain.model.Review
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class ReviewPersistenceAdapter(
    private val reviewJpaRepository: ReviewJpaRepository,
) : ReviewPersistencePort {

    override fun save(review: Review): Review {
        val entity = if (review.id == null) {
            ReviewJpaEntity.fromDomain(review)
        } else {
            val existing = reviewJpaRepository.getReferenceById(review.id)
            existing.updateFrom(review)
            existing
        }
        return reviewJpaRepository.save(entity).toDomain()
    }

    override fun findById(reviewId: Long): Review? {
        return reviewJpaRepository.findById(reviewId).orElse(null)?.toDomain()
    }

    override fun findByLaundromatId(laundromatId: Long, cursor: Long?, size: Int): List<Review> {
        return reviewJpaRepository.findByLaundromatIdWithCursor(laundromatId, cursor, PageRequest.of(0, size))
            .map { it.toDomain() }
    }

    override fun findByCustomerId(customerId: Long, cursor: Long?, size: Int): List<Review> {
        return reviewJpaRepository.findByCustomerIdWithCursor(customerId, cursor, PageRequest.of(0, size))
            .map { it.toDomain() }
    }

    override fun deleteById(reviewId: Long) {
        reviewJpaRepository.deleteById(reviewId)
    }

    override fun countByLaundromatId(laundromatId: Long): Long {
        return reviewJpaRepository.countByLaundromatId(laundromatId)
    }

    override fun averageRatingByLaundromatId(laundromatId: Long): Double {
        return reviewJpaRepository.averageRatingByLaundromatId(laundromatId) ?: 0.0
    }
}
