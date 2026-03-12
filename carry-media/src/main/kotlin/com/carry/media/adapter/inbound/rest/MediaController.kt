package com.carry.media.adapter.inbound.rest

import com.carry.media.adapter.inbound.rest.dto.DownloadUrlResponse
import com.carry.media.adapter.inbound.rest.dto.MediaResponse
import com.carry.media.application.port.inbound.MediaDownloadUseCase
import com.carry.media.application.port.inbound.MediaQueryUseCase
import com.carry.media.application.port.inbound.MediaUploadUseCase
import com.carry.media.application.port.inbound.UploadFileCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v2/media")
class MediaController(
    private val mediaUploadUseCase: MediaUploadUseCase,
    private val mediaQueryUseCase: MediaQueryUseCase,
    private val mediaDownloadUseCase: MediaDownloadUseCase,
) {

    @PostMapping("/upload/{folder}")
    fun uploadFile(
        @PathVariable folder: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam uploadedBy: Long, // TODO: JWT에서 추출
    ): ResponseEntity<MediaResponse> {
        val command = UploadFileCommand(
            folder = folder,
            originalFilename = file.originalFilename ?: "unknown",
            contentType = file.contentType ?: "application/octet-stream",
            uploadedBy = uploadedBy,
            content = file.bytes,
        )
        val media = mediaUploadUseCase.uploadFile(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.from(media))
    }

    @GetMapping("/{accessKey}")
    fun getMedia(@PathVariable accessKey: UUID): ResponseEntity<MediaResponse> {
        val media = mediaQueryUseCase.getByAccessKey(accessKey)
        return ResponseEntity.ok(MediaResponse.from(media))
    }

    @GetMapping("/{accessKey}/download")
    fun getDownloadUrl(@PathVariable accessKey: UUID): ResponseEntity<DownloadUrlResponse> {
        val url = mediaDownloadUseCase.getDownloadUrl(accessKey)
        return ResponseEntity.ok(DownloadUrlResponse(url))
    }
}
