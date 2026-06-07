package com.carry.media.application.service

import com.carry.media.application.port.inbound.InitiateUploadCommand
import com.carry.media.application.port.inbound.MediaUploadUseCase
import com.carry.media.application.port.inbound.UploadFileCommand
import com.carry.media.application.port.outbound.FileStoragePort
import com.carry.media.application.port.outbound.MediaPersistencePort
import com.carry.media.domain.exception.MediaNotFoundException
import com.carry.media.domain.model.MediaResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class MediaUploadService(
    private val mediaPersistencePort: MediaPersistencePort,
    private val fileStoragePort: FileStoragePort,
    private val clock: Clock,
) : MediaUploadUseCase {

    @Transactional
    override fun initiateUpload(command: InitiateUploadCommand): MediaResource {
        val media = MediaResource.create(
            folder = command.folder,
            originalFilename = command.originalFilename,
            contentType = command.contentType,
            uploadedBy = command.uploadedBy,
            now = clock.instant(),
        )
        return mediaPersistencePort.save(media)
    }

    @Transactional
    override fun completeUpload(accessKey: UUID, fileSize: Long) {
        val media = mediaPersistencePort.findByAccessKey(accessKey)
            ?: throw MediaNotFoundException(accessKey)
        media.markCompleted(fileSize)
        mediaPersistencePort.save(media)
    }

    @Transactional
    override fun uploadFile(command: UploadFileCommand): MediaResource {
        val media = MediaResource.create(
            folder = command.folder,
            originalFilename = command.originalFilename,
            contentType = command.contentType,
            uploadedBy = command.uploadedBy,
            now = clock.instant(),
        )

        return try {
            val fileSize = fileStoragePort.upload(
                filePath = media.getFilePath(),
                content = command.content,
                contentType = command.contentType,
            )
            media.markCompleted(fileSize)
            mediaPersistencePort.save(media)
        } catch (e: Exception) {
            media.markFailed()
            mediaPersistencePort.save(media)
            throw e
        }
    }
}
