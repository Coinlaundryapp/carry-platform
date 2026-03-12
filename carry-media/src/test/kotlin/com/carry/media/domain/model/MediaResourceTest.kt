package com.carry.media.domain.model

import com.carry.media.domain.exception.InvalidMediaStatusTransitionException
import com.carry.media.domain.vo.MediaStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MediaResourceTest {

    private fun createMedia() = MediaResource.create(
        folder = "review",
        originalFilename = "photo.jpg",
        contentType = "image/jpeg",
        uploadedBy = 1L,
    )

    private fun reconstitutedMedia(status: MediaStatus = MediaStatus.UPLOADING) = MediaResource.reconstitute(
        id = 1L,
        folder = "review",
        accessKey = UUID.randomUUID(),
        originalFilename = "photo.jpg",
        extension = "jpg",
        contentType = "image/jpeg",
        status = status,
        fileSize = null,
        uploadedBy = 1L,
        createdAt = Instant.now(),
    )

    @Nested
    inner class Create {

        @Test
        fun `미디어를 생성하면 UPLOADING 상태이다`() {
            val media = createMedia()
            assertThat(media.status).isEqualTo(MediaStatus.UPLOADING)
            assertThat(media.id).isNull()
            assertThat(media.accessKey).isNotNull()
            assertThat(media.extension).isEqualTo("jpg")
            assertThat(media.fileSize).isNull()
        }

        @Test
        fun `빈 폴더명으로 생성하면 예외가 발생한다`() {
            assertThatThrownBy {
                MediaResource.create("", "photo.jpg", "image/jpeg", 1L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("폴더명")
        }

        @Test
        fun `빈 파일명으로 생성하면 예외가 발생한다`() {
            assertThatThrownBy {
                MediaResource.create("review", "", "image/jpeg", 1L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("파일명")
        }

        @Test
        fun `확장자 없는 파일명으로 생성하면 예외가 발생한다`() {
            assertThatThrownBy {
                MediaResource.create("review", "photo", "image/jpeg", 1L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("확장자")
        }
    }

    @Nested
    inner class StatusTransitions {

        @Test
        fun `UPLOADING에서 COMPLETED로 전이하면 파일 크기가 기록된다`() {
            val media = reconstitutedMedia(MediaStatus.UPLOADING)
            media.markCompleted(1024L)
            assertThat(media.status).isEqualTo(MediaStatus.COMPLETED)
            assertThat(media.fileSize).isEqualTo(1024L)
        }

        @Test
        fun `UPLOADING에서 FAILED로 전이할 수 있다`() {
            val media = reconstitutedMedia(MediaStatus.UPLOADING)
            media.markFailed()
            assertThat(media.status).isEqualTo(MediaStatus.FAILED)
        }

        @Test
        fun `COMPLETED에서 다른 상태로 전이하면 예외가 발생한다`() {
            val media = reconstitutedMedia(MediaStatus.COMPLETED)
            assertThatThrownBy { media.markFailed() }
                .isInstanceOf(InvalidMediaStatusTransitionException::class.java)
        }

        @Test
        fun `FAILED에서 다른 상태로 전이하면 예외가 발생한다`() {
            val media = reconstitutedMedia(MediaStatus.FAILED)
            assertThatThrownBy { media.markCompleted(1024L) }
                .isInstanceOf(InvalidMediaStatusTransitionException::class.java)
        }
    }

    @Nested
    inner class FilePath {

        @Test
        fun `파일 경로가 올바르게 생성된다`() {
            val media = createMedia()
            val path = media.getFilePath()
            assertThat(path).isEqualTo("review/${media.accessKey}.jpg")
        }

        @Test
        fun `공개 URL이 올바르게 생성된다`() {
            val media = createMedia()
            val url = media.getPublicUrl("https://cdn.example.com")
            assertThat(url).isEqualTo("https://cdn.example.com/review/${media.accessKey}.jpg")
        }
    }
}
