package com.carry_laundry.carry_backend.media_resource.presentation;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.media_resource.application.MediaResourceDownloadService;
import com.carry_laundry.carry_backend.media_resource.application.MediaResourceUploadService;
import com.carry_laundry.carry_backend.media_resource.application.record.FileMetadata;
import com.carry_laundry.carry_backend.media_resource.presentation.payload.response.MediaResourceUploadResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaResourceController {

    private final MediaResourceUploadService mediaResourceUploadService;
    private final MediaResourceDownloadService mediaResourceDownloadService;

    @PostMapping("/upload/{folder}")
    public Mono<ApiCommonResponse<MediaResourceUploadResponse>> uploadMedia(
        @PathVariable String folder,
        @RequestPart("files") Flux<FilePart> fileParts) {
        return mediaResourceUploadService.uploadFiles(fileParts, folder)
            .collectList()
            .map(uploadStatuses -> {
                MediaResourceUploadResponse response = new MediaResourceUploadResponse(
                    uploadStatuses);
                return ApiCommonResponse.createSuccessResponse(response);
            });
    }

    @GetMapping("/{folder}/{accessKey}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadMedia(
        @PathVariable String folder, @PathVariable UUID accessKey) {
        return mediaResourceDownloadService.downloadFile(folder, accessKey)
            .map(objects -> {
                FileMetadata metadata = objects.getT1();
                Flux<DataBuffer> dataBufferFlux = objects.getT2();
                return ResponseEntity.ok()
                    .header(CONTENT_DISPOSITION, "inline; filename=\"" + metadata.filename() + "\"")
                    .contentType(MediaType.parseMediaType(metadata.contentType()))
                    .contentLength(metadata.contentLength())
                    .body(dataBufferFlux);
            });
    }
}
