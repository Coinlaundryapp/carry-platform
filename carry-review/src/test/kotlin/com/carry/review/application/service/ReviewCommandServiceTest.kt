package com.carry.review.application.service

import com.carry.event.port.EventPublisherPort
import com.carry.review.application.port.inbound.CreateReviewCommand
import com.carry.review.application.port.inbound.UpdateReviewCommand
import com.carry.review.application.port.outbound.ReviewPersistencePort
import com.carry.review.domain.exception.ReviewNotFoundException
import com.carry.review.domain.exception.ReviewNotOwnedException
import com.carry.review.domain.model.Review
import com.carry.review.domain.vo.ReviewRating
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReviewCommandServiceTest {

    private val reviewPersistencePort = mockk<ReviewPersistencePort>(relaxed = true)
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = ReviewCommandService(reviewPersistencePort, eventPublisher, clock)

    private fun aCreateCommand() = CreateReviewCommand(
        laundromatId = 10L,
        customerId = 1L,
        comment = "아주 깨끗해요",
        rating = 5,
        mediaUrls = listOf("https://example.com/photo.jpg"),
    )

    private fun aReview(
        id: Long = 42L,
        customerId: Long = 1L,
        rating: ReviewRating = ReviewRating.FIVE,
    ) = Review.reconstitute(
        id = id,
        laundromatId = 10L,
        customerId = customerId,
        comment = "아주 깨끗해요",
        rating = rating,
        mediaUrls = listOf("https://example.com/photo.jpg"),
        createdAt = now,
        updatedAt = now,
    )

    @Nested
    inner class CreateReview {

        @Test
        fun `리뷰를 생성하고 Outbox 이벤트를 발행한다`() {
            val saved = slot<Review>()
            every { reviewPersistencePort.save(capture(saved)) } answers {
                Review.reconstitute(
                    id = 42L,
                    laundromatId = saved.captured.laundromatId,
                    customerId = saved.captured.customerId,
                    comment = saved.captured.comment,
                    rating = saved.captured.rating,
                    mediaUrls = saved.captured.mediaUrls,
                    createdAt = now,
                    updatedAt = now,
                )
            }

            val result = sut.createReview(aCreateCommand())

            assertThat(result.id).isEqualTo(42L)
            assertThat(result.rating).isEqualTo(ReviewRating.FIVE)
            assertThat(result.mediaUrls).hasSize(1)
            verify { eventPublisher.publish("Review", "42", "ReviewCreatedEvent", any(), any()) }
        }
    }

    @Nested
    inner class UpdateReview {

        @Test
        fun `본인의 리뷰를 수정할 수 있다`() {
            val review = aReview()
            every { reviewPersistencePort.findById(42L) } returns review
            every { reviewPersistencePort.save(any()) } answers { firstArg() }

            val command = UpdateReviewCommand(
                reviewId = 42L,
                customerId = 1L,
                comment = "보통이에요",
                rating = 3,
            )

            val result = sut.updateReview(command)

            assertThat(result.comment).isEqualTo("보통이에요")
            assertThat(result.rating).isEqualTo(ReviewRating.THREE)
            verify { reviewPersistencePort.save(any()) }
        }

        @Test
        fun `존재하지 않는 리뷰를 수정하면 예외가 발생한다`() {
            every { reviewPersistencePort.findById(999L) } returns null

            val command = UpdateReviewCommand(
                reviewId = 999L,
                customerId = 1L,
                comment = "수정",
                rating = 3,
            )

            assertThatThrownBy { sut.updateReview(command) }
                .isInstanceOf(ReviewNotFoundException::class.java)
        }

        @Test
        fun `본인의 리뷰가 아니면 수정 시 예외가 발생한다`() {
            val review = aReview(customerId = 1L)
            every { reviewPersistencePort.findById(42L) } returns review

            val command = UpdateReviewCommand(
                reviewId = 42L,
                customerId = 999L,
                comment = "수정",
                rating = 3,
            )

            assertThatThrownBy { sut.updateReview(command) }
                .isInstanceOf(ReviewNotOwnedException::class.java)
        }
    }

    @Nested
    inner class DeleteReview {

        @Test
        fun `본인의 리뷰를 삭제할 수 있다`() {
            val review = aReview()
            every { reviewPersistencePort.findById(42L) } returns review

            sut.deleteReview(42L, 1L)

            verify { reviewPersistencePort.deleteById(42L) }
        }

        @Test
        fun `존재하지 않는 리뷰를 삭제하면 예외가 발생한다`() {
            every { reviewPersistencePort.findById(999L) } returns null

            assertThatThrownBy { sut.deleteReview(999L, 1L) }
                .isInstanceOf(ReviewNotFoundException::class.java)
        }

        @Test
        fun `본인의 리뷰가 아니면 삭제 시 예외가 발생한다`() {
            val review = aReview(customerId = 1L)
            every { reviewPersistencePort.findById(42L) } returns review

            assertThatThrownBy { sut.deleteReview(42L, 999L) }
                .isInstanceOf(ReviewNotOwnedException::class.java)
        }
    }
}
