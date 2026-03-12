package com.carry.media.application.service

import com.carry.media.application.port.inbound.MediaQueryUseCase
import com.carry.media.application.port.outbound.MediaPersistencePort
import com.carry.media.domain.exception.MediaNotFoundException
import com.carry.media.domain.model.MediaResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MediaQueryService(
    private val mediaPersistencePort: MediaPersistencePort,
) : MediaQueryUseCase {

    override fun getByAccessKey(accessKey: UUID): MediaResource {
        return mediaPersistencePort.findByAccessKey(accessKey)
            ?: throw MediaNotFoundException(accessKey)
    }

    override fun getByFolder(folder: String): List<MediaResource> {
        return mediaPersistencePort.findByFolder(folder)
    }
}
