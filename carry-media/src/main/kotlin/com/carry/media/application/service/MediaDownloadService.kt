package com.carry.media.application.service

import com.carry.media.application.port.inbound.MediaDownloadUseCase
import com.carry.media.application.port.outbound.FileStoragePort
import com.carry.media.application.port.outbound.MediaPersistencePort
import com.carry.media.domain.exception.MediaNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MediaDownloadService(
    private val mediaPersistencePort: MediaPersistencePort,
    private val fileStoragePort: FileStoragePort,
) : MediaDownloadUseCase {

    override fun getDownloadUrl(accessKey: UUID): String {
        val media = mediaPersistencePort.findByAccessKey(accessKey)
            ?: throw MediaNotFoundException(accessKey)
        return fileStoragePort.generatePresignedUrl(media.getFilePath())
    }
}
