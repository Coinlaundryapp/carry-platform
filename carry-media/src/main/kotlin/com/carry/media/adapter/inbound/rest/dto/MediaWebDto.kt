package com.carry.media.adapter.inbound.rest.dto

import com.carry.media.domain.model.MediaResource
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "미디어 파일 응답")
data class MediaResponse(
    @Schema(description = "미디어 ID") val id: Long,
    @Schema(description = "폴더") val folder: String,
    @Schema(description = "접근 키") val accessKey: UUID,
    @Schema(description = "원본 파일명") val originalFilename: String,
    @Schema(description = "확장자") val extension: String,
    @Schema(description = "콘텐츠 타입") val contentType: String,
    @Schema(description = "상태") val status: String,
    @Schema(description = "파일 크기(bytes)", nullable = true) val fileSize: Long?,
    @Schema(description = "업로드한 사용자 ID") val uploadedBy: Long,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(media: MediaResource) = MediaResponse(
            id = media.id!!,
            folder = media.folder,
            accessKey = media.accessKey,
            originalFilename = media.originalFilename,
            extension = media.extension,
            contentType = media.contentType,
            status = media.status.name,
            fileSize = media.fileSize,
            uploadedBy = media.uploadedBy,
            createdAt = media.createdAt,
        )
    }
}

@Schema(description = "다운로드 URL 응답")
data class DownloadUrlResponse(
    @Schema(description = "S3 Presigned URL")
    val downloadUrl: String,
)
