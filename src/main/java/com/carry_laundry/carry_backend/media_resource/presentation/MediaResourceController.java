package com.carry_laundry.carry_backend.media_resource.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.media_resource.application.MediaResourceUploadService;
import com.carry_laundry.carry_backend.media_resource.presentation.payload.response.MediaResourceUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaResourceController {

    private final MediaResourceUploadService mediaResourceUploadService;

    @PostMapping("/upload/{folder}")
    public Mono<ApiCommonResponse<MediaResourceUploadResponse>> uploadMedia(
        @PathVariable String folder,
        @RequestPart("files") Flux<FilePart> fileParts) {
        return mediaResourceUploadService.uploadFiles(fileParts, folder)
            .collectList()
            .map(ids -> {
                MediaResourceUploadResponse response = new MediaResourceUploadResponse(ids);
                return ApiCommonResponse.createSuccessResponse(response);
            });
    }
}
