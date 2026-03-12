package com.carry.media.application.port.inbound

import java.util.UUID

interface MediaDownloadUseCase {
    fun getDownloadUrl(accessKey: UUID): String
}
