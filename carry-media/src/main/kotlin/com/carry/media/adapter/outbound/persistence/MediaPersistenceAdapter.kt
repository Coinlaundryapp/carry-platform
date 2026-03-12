package com.carry.media.adapter.outbound.persistence

import com.carry.media.adapter.outbound.persistence.entity.MediaResourceJpaEntity
import com.carry.media.adapter.outbound.persistence.repository.MediaResourceJpaRepository
import com.carry.media.application.port.outbound.MediaPersistencePort
import com.carry.media.domain.model.MediaResource
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MediaPersistenceAdapter(
    private val mediaResourceJpaRepository: MediaResourceJpaRepository,
) : MediaPersistencePort {

    override fun save(mediaResource: MediaResource): MediaResource {
        val entity = if (mediaResource.id == null) {
            MediaResourceJpaEntity.fromDomain(mediaResource)
        } else {
            val existing = mediaResourceJpaRepository.getReferenceById(mediaResource.id)
            existing.updateFrom(mediaResource)
            existing
        }
        return mediaResourceJpaRepository.save(entity).toDomain()
    }

    override fun findByAccessKey(accessKey: UUID): MediaResource? {
        return mediaResourceJpaRepository.findByAccessKey(accessKey)?.toDomain()
    }

    override fun findByFolder(folder: String): List<MediaResource> {
        return mediaResourceJpaRepository.findByFolderOrderByCreatedAtDesc(folder)
            .map { it.toDomain() }
    }

    override fun findByAccessKeys(accessKeys: List<UUID>): List<MediaResource> {
        return mediaResourceJpaRepository.findByAccessKeyIn(accessKeys)
            .map { it.toDomain() }
    }
}
