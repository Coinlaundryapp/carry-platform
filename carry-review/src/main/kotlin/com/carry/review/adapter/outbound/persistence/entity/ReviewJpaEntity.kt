package com.carry.review.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewRating
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "review_reviews")
class ReviewJpaEntity(
    @Column(nullable = false)
    val laundromatId: Long,

    @Column(nullable = false)
    val customerId: Long,

    @Column(length = 1000)
    var comment: String?,

    @Column(nullable = false)
    var rating: Int,

    @OneToMany(mappedBy = "review", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val mediaList: MutableList<ReviewMediaJpaEntity> = mutableListOf(),
) : BaseEntity() {

    /**
     * JPA optimistic locking 카운터. 두 트랜잭션이 동일 애그리거트를 동시 변경하면
     * 두 번째 commit에서 OptimisticLockingFailureException이 발생한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    fun toDomain(): Review = Review.reconstitute(
        id = id,
        laundromatId = laundromatId,
        customerId = customerId,
        comment = comment,
        rating = ReviewRating.fromValue(rating),
        mediaUrls = mediaList.map { it.mediaUrl },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(review: Review) {
        comment = review.comment
        rating = review.rating.value

        mediaList.clear()
        review.mediaUrls.forEach { url ->
            mediaList.add(ReviewMediaJpaEntity(review = this, mediaUrl = url))
        }
    }

    companion object {
        fun fromDomain(review: Review): ReviewJpaEntity {
            val entity = ReviewJpaEntity(
                laundromatId = review.laundromatId,
                customerId = review.customerId,
                comment = review.comment,
                rating = review.rating.value,
            )
            review.mediaUrls.forEach { url ->
                entity.mediaList.add(ReviewMediaJpaEntity(review = entity, mediaUrl = url))
            }
            return entity
        }
    }
}
