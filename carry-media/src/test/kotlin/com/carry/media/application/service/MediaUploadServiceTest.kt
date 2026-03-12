package com.carry.media.application.service

import com.carry.media.application.port.inbound.InitiateUploadCommand
import com.carry.media.application.port.inbound.UploadFileCommand
import com.carry.media.application.port.outbound.FileStoragePort
import com.carry.media.application.port.outbound.MediaPersistencePort
import com.carry.media.domain.exception.MediaNotFoundException
import com.carry.media.domain.model.MediaResource
import com.carry.media.domain.vo.MediaStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MediaUploadServiceTest {

    private val mediaPersistencePort = mockk<MediaPersistencePort>(relaxed = true)
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)

    private val sut = MediaUploadService(mediaPersistencePort, fileStoragePort)

    @Nested
    inner class InitiateUpload {

        @Test
        fun `업로드를 시작하면 UPLOADING 상태의 미디어가 생성된다`() {
            val saved = slot<MediaResource>()
            every { mediaPersistencePort.save(capture(saved)) } answers {
                MediaResource.reconstitute(
                    id = 1L,
                    folder = saved.captured.folder,
                    accessKey = saved.captured.accessKey,
                    originalFilename = saved.captured.originalFilename,
                    extension = saved.captured.extension,
                    contentType = saved.captured.contentType,
                    status = saved.captured.status,
                    fileSize = saved.captured.fileSize,
                    uploadedBy = saved.captured.uploadedBy,
                    createdAt = Instant.now(),
                )
            }

            val command = InitiateUploadCommand("review", "photo.jpg", "image/jpeg", 1L)
            val result = sut.initiateUpload(command)

            assertThat(result.id).isEqualTo(1L)
            assertThat(result.status).isEqualTo(MediaStatus.UPLOADING)
            assertThat(result.folder).isEqualTo("review")
            verify { mediaPersistencePort.save(any()) }
        }
    }

    @Nested
    inner class CompleteUpload {

        @Test
        fun `업로드를 완료하면 COMPLETED 상태로 전이하고 파일 크기가 기록된다`() {
            val accessKey = UUID.randomUUID()
            val media = MediaResource.reconstitute(
                id = 1L, folder = "review", accessKey = accessKey,
                originalFilename = "photo.jpg", extension = "jpg",
                contentType = "image/jpeg", status = MediaStatus.UPLOADING,
                fileSize = null, uploadedBy = 1L, createdAt = Instant.now(),
            )
            every { mediaPersistencePort.findByAccessKey(accessKey) } returns media

            sut.completeUpload(accessKey, 2048L)

            verify { mediaPersistencePort.save(any()) }
        }

        @Test
        fun `존재하지 않는 accessKey로 완료하면 예외가 발생한다`() {
            val accessKey = UUID.randomUUID()
            every { mediaPersistencePort.findByAccessKey(accessKey) } returns null

            assertThatThrownBy { sut.completeUpload(accessKey, 2048L) }
                .isInstanceOf(MediaNotFoundException::class.java)
        }
    }

    @Nested
    inner class UploadFile {

        @Test
        fun `파일을 업로드하면 S3에 저장하고 COMPLETED 상태의 미디어를 반환한다`() {
            val content = "file-content".toByteArray()
            val saved = slot<MediaResource>()
            every { fileStoragePort.upload(any(), content, "image/jpeg") } returns content.size.toLong()
            every { mediaPersistencePort.save(capture(saved)) } answers {
                MediaResource.reconstitute(
                    id = 1L,
                    folder = saved.captured.folder,
                    accessKey = saved.captured.accessKey,
                    originalFilename = saved.captured.originalFilename,
                    extension = saved.captured.extension,
                    contentType = saved.captured.contentType,
                    status = saved.captured.status,
                    fileSize = saved.captured.fileSize,
                    uploadedBy = saved.captured.uploadedBy,
                    createdAt = Instant.now(),
                )
            }

            val command = UploadFileCommand("review", "photo.jpg", "image/jpeg", 1L, content)
            val result = sut.uploadFile(command)

            assertThat(result.status).isEqualTo(MediaStatus.COMPLETED)
            assertThat(result.fileSize).isEqualTo(content.size.toLong())
            verify { fileStoragePort.upload(any(), content, "image/jpeg") }
            verify { mediaPersistencePort.save(any()) }
        }

        @Test
        fun `S3 업로드 실패 시 FAILED 상태로 저장하고 예외를 전파한다`() {
            val content = "file-content".toByteArray()
            every { fileStoragePort.upload(any(), content, "image/jpeg") } throws RuntimeException("S3 error")
            every { mediaPersistencePort.save(any()) } answers { firstArg() }

            val command = UploadFileCommand("review", "photo.jpg", "image/jpeg", 1L, content)

            assertThatThrownBy { sut.uploadFile(command) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("S3 error")

            val saved = slot<MediaResource>()
            verify { mediaPersistencePort.save(capture(saved)) }
            assertThat(saved.captured.status).isEqualTo(MediaStatus.FAILED)
        }
    }
}
