package com.carry.review.domain.model

import com.carry.review.domain.vo.ReviewRating
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class ReviewTest {

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    private fun createReview(
        rating: ReviewRating = ReviewRating.FIVE,
        mediaUrls: List<String> = emptyList(),
    ) = Review.create(
        laundromatId = 10L,
        customerId = 1L,
        comment = "아주 깨끗해요",
        rating = rating,
        mediaUrls = mediaUrls,
        now = now,
    )

    private fun reconstitutedReview(
        rating: ReviewRating = ReviewRating.FIVE,
        mediaUrls: List<String> = emptyList(),
    ) = Review.reconstitute(
        id = 1L,
        laundromatId = 10L,
        customerId = 1L,
        comment = "아주 깨끗해요",
        rating = rating,
        mediaUrls = mediaUrls,
        createdAt = now,
        updatedAt = now,
    )

    @Nested
    inner class Create {

        @Test
        fun `리뷰를 생성하면 id는 null이고 필드가 올바르게 설정된다`() {
            val review = createReview()
            assertThat(review.id).isNull()
            assertThat(review.laundromatId).isEqualTo(10L)
            assertThat(review.customerId).isEqualTo(1L)
            assertThat(review.comment).isEqualTo("아주 깨끗해요")
            assertThat(review.rating).isEqualTo(ReviewRating.FIVE)
            assertThat(review.mediaUrls).isEmpty()
            assertThat(review.createdAt).isEqualTo(now)
            assertThat(review.updatedAt).isEqualTo(now)
        }

        @Test
        fun `미디어 URL과 함께 리뷰를 생성할 수 있다`() {
            val urls = listOf("https://example.com/photo1.jpg", "https://example.com/photo2.jpg")
            val review = createReview(mediaUrls = urls)
            assertThat(review.mediaUrls).hasSize(2)
            assertThat(review.mediaUrls).containsExactlyElementsOf(urls)
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `리뷰를 수정하면 comment와 rating이 변경된다`() {
            val review = reconstitutedReview()
            val updatedAt = now.plusSeconds(60)

            review.update("보통이에요", ReviewRating.THREE, updatedAt)

            assertThat(review.comment).isEqualTo("보통이에요")
            assertThat(review.rating).isEqualTo(ReviewRating.THREE)
            assertThat(review.updatedAt).isEqualTo(updatedAt)
        }

        @Test
        fun `comment를 null로 수정할 수 있다`() {
            val review = reconstitutedReview()
            review.update(null, ReviewRating.FOUR, now)
            assertThat(review.comment).isNull()
            assertThat(review.rating).isEqualTo(ReviewRating.FOUR)
        }
    }

    @Nested
    inner class AddMedia {

        @Test
        fun `미디어를 추가하면 mediaUrls에 반영된다`() {
            val review = reconstitutedReview()
            assertThat(review.mediaUrls).isEmpty()

            review.addMedia("https://example.com/photo.jpg", now)

            assertThat(review.mediaUrls).hasSize(1)
            assertThat(review.mediaUrls[0]).isEqualTo("https://example.com/photo.jpg")
        }

        @Test
        fun `여러 미디어를 순차적으로 추가할 수 있다`() {
            val review = reconstitutedReview()
            review.addMedia("https://example.com/photo1.jpg", now)
            review.addMedia("https://example.com/photo2.jpg", now)
            assertThat(review.mediaUrls).hasSize(2)
        }
    }

    @Nested
    inner class Rating {

        @Test
        fun `ReviewRating의 fromValue로 올바른 값을 조회할 수 있다`() {
            assertThat(ReviewRating.fromValue(1)).isEqualTo(ReviewRating.ONE)
            assertThat(ReviewRating.fromValue(3)).isEqualTo(ReviewRating.THREE)
            assertThat(ReviewRating.fromValue(5)).isEqualTo(ReviewRating.FIVE)
        }

        @Test
        fun `유효하지 않은 값으로 fromValue 호출 시 예외가 발생한다`() {
            assertThatThrownBy { ReviewRating.fromValue(0) }
                .isInstanceOf(NoSuchElementException::class.java)

            assertThatThrownBy { ReviewRating.fromValue(6) }
                .isInstanceOf(NoSuchElementException::class.java)
        }
    }

    @Nested
    inner class Reconstitute {

        @Test
        fun `reconstitute로 리뷰를 복원할 수 있다`() {
            val review = reconstitutedReview(
                rating = ReviewRating.FOUR,
                mediaUrls = listOf("https://example.com/photo.jpg"),
            )
            assertThat(review.id).isEqualTo(1L)
            assertThat(review.rating).isEqualTo(ReviewRating.FOUR)
            assertThat(review.mediaUrls).hasSize(1)
            assertThat(review.createdAt).isEqualTo(now)
        }
    }
}
