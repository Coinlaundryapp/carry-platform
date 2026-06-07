package com.carry.review.domain.model

import com.carry.common.exception.requireInput
import com.carry.review.domain.vo.ReviewRating
import java.time.Instant

class Review private constructor(
    val id: Long?,
    val laundromatId: Long,
    val customerId: Long,
    private var _comment: String?,
    private var _rating: ReviewRating,
    private val _mediaUrls: MutableList<String>,
    val createdAt: Instant,
    private var _updatedAt: Instant,
) {
    val comment get() = _comment
    val rating get() = _rating
    val mediaUrls: List<String> get() = _mediaUrls.toList()
    val updatedAt get() = _updatedAt

    companion object {
        fun create(
            laundromatId: Long,
            customerId: Long,
            comment: String?,
            rating: ReviewRating,
            mediaUrls: List<String> = emptyList(),
            now: Instant,
        ): Review {
            requireInput(rating.value in 1..5) { "평점은 1~5 사이여야 합니다" }

            return Review(
                id = null,
                laundromatId = laundromatId,
                customerId = customerId,
                _comment = comment,
                _rating = rating,
                _mediaUrls = mediaUrls.toMutableList(),
                createdAt = now,
                _updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            laundromatId: Long,
            customerId: Long,
            comment: String?,
            rating: ReviewRating,
            mediaUrls: List<String>,
            createdAt: Instant,
            updatedAt: Instant,
        ): Review = Review(
            id = id,
            laundromatId = laundromatId,
            customerId = customerId,
            _comment = comment,
            _rating = rating,
            _mediaUrls = mediaUrls.toMutableList(),
            createdAt = createdAt,
            _updatedAt = updatedAt,
        )
    }

    fun update(comment: String?, rating: ReviewRating, now: Instant) {
        _comment = comment
        _rating = rating
        _updatedAt = now
    }

    fun addMedia(url: String, now: Instant) {
        _mediaUrls.add(url)
        _updatedAt = now
    }
}
