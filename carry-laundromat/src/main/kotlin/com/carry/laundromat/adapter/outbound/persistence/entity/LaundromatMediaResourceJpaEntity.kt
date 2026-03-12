package com.carry.laundromat.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.laundromat.domain.vo.MediaResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "laundromat_media_resources")
class LaundromatMediaResourceJpaEntity(
    @Column(name = "media_url", nullable = false)
    val mediaUrl: String,

    @Column(nullable = false)
    val extension: String,
) : BaseEntity() {

    fun toDomain(): MediaResource = MediaResource(
        id = id,
        url = mediaUrl,
        extension = extension,
    )

    companion object {
        fun fromDomain(resource: MediaResource): LaundromatMediaResourceJpaEntity =
            LaundromatMediaResourceJpaEntity(
                mediaUrl = resource.url,
                extension = resource.extension,
            )
    }
}
