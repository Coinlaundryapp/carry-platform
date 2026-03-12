package com.carry.media.application.port.outbound

import com.carry.media.domain.model.MediaResource
import java.util.UUID

interface MediaPersistencePort {
    fun save(mediaResource: MediaResource): MediaResource
    fun findByAccessKey(accessKey: UUID): MediaResource?
    fun findByFolder(folder: String): List<MediaResource>
    fun findByAccessKeys(accessKeys: List<UUID>): List<MediaResource>
}
