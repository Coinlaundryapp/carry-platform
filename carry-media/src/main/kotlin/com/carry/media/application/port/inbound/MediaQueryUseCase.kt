package com.carry.media.application.port.inbound

import com.carry.media.domain.model.MediaResource
import java.util.UUID

interface MediaQueryUseCase {
    fun getByAccessKey(accessKey: UUID): MediaResource
    fun getByFolder(folder: String): List<MediaResource>
}
