package com.carry.media.application.port.inbound

import com.carry.media.domain.model.MediaResource
import java.util.UUID

data class InitiateUploadCommand(
    val folder: String,
    val originalFilename: String,
    val contentType: String,
    val uploadedBy: Long,
)

data class UploadFileCommand(
    val folder: String,
    val originalFilename: String,
    val contentType: String,
    val uploadedBy: Long,
    val content: ByteArray,
)

interface MediaUploadUseCase {
    fun initiateUpload(command: InitiateUploadCommand): MediaResource
    fun completeUpload(accessKey: UUID, fileSize: Long)
    fun uploadFile(command: UploadFileCommand): MediaResource
}
