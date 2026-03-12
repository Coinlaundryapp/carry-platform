package com.carry.event.review

data class ReviewCreatedEvent(
    val reviewId: Long,
    val laundromatId: Long,
    val customerId: Long,
    val rating: Int,
    val comment: String?,
)
