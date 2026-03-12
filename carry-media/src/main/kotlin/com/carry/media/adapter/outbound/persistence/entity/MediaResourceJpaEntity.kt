package com.carry.media.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.media.domain.model.MediaResource
import com.carry.media.domain.vo.MediaStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "media_resources")
class MediaResourceJpaEntity(
    @Column(nullable = false, length = 50)
    val folder: String,

    @Column(nullable = false, unique = true)
    val accessKey: UUID,

    @Column(nullable = false)
    val originalFilename: String,

    @Column(nullable = false, length = 20)
    val extension: String,

    @Column(nullable = false, length = 100)
    val contentType: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MediaStatus,

    var fileSize: Long?,

    @Column(nullable = false)
    val uploadedBy: Long,
) : BaseEntity() {

    fun toDomain(): MediaResource = MediaResource.reconstitute(
        id = id,
        folder = folder,
        accessKey = accessKey,
        originalFilename = originalFilename,
        extension = extension,
        contentType = contentType,
        status = status,
        fileSize = fileSize,
        uploadedBy = uploadedBy,
        createdAt = createdAt,
    )

    fun updateFrom(media: MediaResource) {
        status = media.status
        fileSize = media.fileSize
    }

    companion object {
        fun fromDomain(media: MediaResource): MediaResourceJpaEntity = MediaResourceJpaEntity(
            folder = media.folder,
            accessKey = media.accessKey,
            originalFilename = media.originalFilename,
            extension = media.extension,
            contentType = media.contentType,
            status = media.status,
            fileSize = media.fileSize,
            uploadedBy = media.uploadedBy,
        )
    }
}
