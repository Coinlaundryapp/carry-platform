package com.carry.media.domain.model

import com.carry.common.exception.requireInput
import com.carry.media.domain.exception.InvalidMediaStatusTransitionException
import com.carry.media.domain.vo.MediaStatus
import java.time.Instant
import java.util.UUID

class MediaResource private constructor(
    val id: Long?,
    val folder: String,
    val accessKey: UUID,
    val originalFilename: String,
    val extension: String,
    val contentType: String,
    private var _status: MediaStatus,
    private var _fileSize: Long?,
    val uploadedBy: Long,
    val createdAt: Instant,
) {
    val status get() = _status
    val fileSize get() = _fileSize

    companion object {
        fun create(
            folder: String,
            originalFilename: String,
            contentType: String,
            uploadedBy: Long,
        ): MediaResource {
            requireInput(folder.isNotBlank()) { "폴더명은 비어있을 수 없습니다" }
            requireInput(originalFilename.isNotBlank()) { "파일명은 비어있을 수 없습니다" }

            val extension = originalFilename.substringAfterLast('.', "")
            requireInput(extension.isNotBlank()) { "파일 확장자가 없습니다" }

            return MediaResource(
                id = null,
                folder = folder,
                accessKey = UUID.randomUUID(),
                originalFilename = originalFilename,
                extension = extension,
                contentType = contentType,
                _status = MediaStatus.UPLOADING,
                _fileSize = null,
                uploadedBy = uploadedBy,
                createdAt = Instant.now(),
            )
        }

        fun reconstitute(
            id: Long,
            folder: String,
            accessKey: UUID,
            originalFilename: String,
            extension: String,
            contentType: String,
            status: MediaStatus,
            fileSize: Long?,
            uploadedBy: Long,
            createdAt: Instant,
        ): MediaResource = MediaResource(
            id, folder, accessKey, originalFilename, extension,
            contentType, status, fileSize, uploadedBy, createdAt,
        )
    }

    fun markCompleted(fileSize: Long) {
        transitTo(MediaStatus.COMPLETED)
        _fileSize = fileSize
    }

    fun markFailed() {
        transitTo(MediaStatus.FAILED)
    }

    fun getFilePath(): String = "$folder/${accessKey}.$extension"

    fun getPublicUrl(baseUrl: String): String = "$baseUrl/$folder/${accessKey}.$extension"

    private fun transitTo(target: MediaStatus) {
        if (!_status.canTransitionTo(target)) {
            throw InvalidMediaStatusTransitionException(_status, target)
        }
        _status = target
    }
}
