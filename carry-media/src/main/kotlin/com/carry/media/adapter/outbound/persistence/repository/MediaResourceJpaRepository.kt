package com.carry.media.adapter.outbound.persistence.repository

import com.carry.media.adapter.outbound.persistence.entity.MediaResourceJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MediaResourceJpaRepository : JpaRepository<MediaResourceJpaEntity, Long> {
    fun findByAccessKey(accessKey: UUID): MediaResourceJpaEntity?
    fun findByFolderOrderByCreatedAtDesc(folder: String): List<MediaResourceJpaEntity>
    fun findByAccessKeyIn(accessKeys: List<UUID>): List<MediaResourceJpaEntity>
}
