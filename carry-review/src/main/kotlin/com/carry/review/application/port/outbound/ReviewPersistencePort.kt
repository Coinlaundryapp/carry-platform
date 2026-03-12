package com.carry.review.application.port.outbound

import com.carry.review.domain.model.Review

interface ReviewPersistencePort {
    fun save(review: Review): Review
    fun findById(reviewId: Long): Review?
    fun findByLaundromatId(laundromatId: Long, cursor: Long?, size: Int): List<Review>
    fun findByCustomerId(customerId: Long, cursor: Long?, size: Int): List<Review>
    fun deleteById(reviewId: Long)
    fun countByLaundromatId(laundromatId: Long): Long
    fun averageRatingByLaundromatId(laundromatId: Long): Double
}
