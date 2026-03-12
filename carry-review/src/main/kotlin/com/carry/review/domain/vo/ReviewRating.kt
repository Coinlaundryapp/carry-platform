package com.carry.review.domain.vo

enum class ReviewRating(val value: Int) {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    companion object {
        fun fromValue(value: Int): ReviewRating =
            entries.first { it.value == value }
    }
}
