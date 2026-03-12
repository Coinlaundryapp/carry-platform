package com.carry.media.application.port.outbound

interface FileStoragePort {
    fun upload(filePath: String, content: ByteArray, contentType: String): Long
    fun generatePresignedUrl(filePath: String, expirationMinutes: Int = 60): String
    fun delete(filePath: String)
}
