package com.carry.media.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.media.adapter.inbound.rest.dto.DownloadUrlResponse
import com.carry.media.adapter.inbound.rest.dto.MediaResponse
import com.carry.media.application.port.inbound.MediaDownloadUseCase
import com.carry.media.application.port.inbound.MediaQueryUseCase
import com.carry.media.application.port.inbound.MediaUploadUseCase
import com.carry.media.application.port.inbound.UploadFileCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Media", description = "미디어 파일 관리 API")
@RestController
@RequestMapping("/api/v2/media")
class MediaController(
    private val mediaUploadUseCase: MediaUploadUseCase,
    private val mediaQueryUseCase: MediaQueryUseCase,
    private val mediaDownloadUseCase: MediaDownloadUseCase,
) {

    @Operation(summary = "파일 업로드", description = "미디어 파일을 S3에 업로드합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "201", description = "업로드 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 파일 형식")])
    @PostMapping("/upload/{folder}")
    fun uploadFile(
        @PathVariable folder: String,
        @RequestParam("file") file: MultipartFile,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<MediaResponse>> {
        val command = UploadFileCommand(
            folder = folder,
            originalFilename = file.originalFilename ?: "unknown",
            contentType = file.contentType ?: "application/octet-stream",
            uploadedBy = userId,
            content = file.bytes,
        )
        val media = mediaUploadUseCase.uploadFile(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(MediaResponse.from(media)))
    }

    @Operation(summary = "미디어 정보 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "미디어 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "미디어를 찾을 수 없음")])
    @GetMapping("/{accessKey}")
    fun getMedia(@PathVariable accessKey: UUID): ResponseEntity<ApiResponse<MediaResponse>> {
        val media = mediaQueryUseCase.getByAccessKey(accessKey)
        return ResponseEntity.ok(ApiResponse.success(MediaResponse.from(media)))
    }

    @Operation(summary = "다운로드 URL 조회", description = "미디어 파일의 임시 다운로드 URL을 생성합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "다운로드 URL 생성 성공"), SwaggerApiResponse(responseCode = "404", description = "미디어를 찾을 수 없음")])
    @GetMapping("/{accessKey}/download")
    fun getDownloadUrl(@PathVariable accessKey: UUID): ResponseEntity<ApiResponse<DownloadUrlResponse>> {
        val url = mediaDownloadUseCase.getDownloadUrl(accessKey)
        return ResponseEntity.ok(ApiResponse.success(DownloadUrlResponse(url)))
    }
}
