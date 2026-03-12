package com.carry.review.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "review_media")
class ReviewMediaJpaEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    val review: ReviewJpaEntity,

    @Column(nullable = false, length = 500)
    val mediaUrl: String,
) : BaseEntity()
