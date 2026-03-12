package com.carry.media.adapter.inbound.rest.dto

import com.carry.media.domain.model.MediaResource
import java.time.Instant
import java.util.UUID

data class MediaResponse(
    val id: Long,
    val folder: String,
    val accessKey: UUID,
    val originalFilename: String,
    val extension: String,
    val contentType: String,
    val status: String,
    val fileSize: Long?,
    val uploadedBy: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(media: MediaResource) = MediaResponse(
            id = media.id!!,
            folder = media.folder,
            accessKey = media.accessKey,
            originalFilename = media.originalFilename,
            extension = media.extension,
            contentType = media.contentType,
            status = media.status.name,
            fileSize = media.fileSize,
            uploadedBy = media.uploadedBy,
            createdAt = media.createdAt,
        )
    }
}

data class DownloadUrlResponse(
    val downloadUrl: String,
)
