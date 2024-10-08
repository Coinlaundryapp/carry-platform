package com.carry_laundry.carry_backend.media_resource.application;

import com.carry_laundry.carry_backend.media_resource.application.record.FileUploadStatus;
import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MediaResourceUploadService {

    private final S3Service s3Service;
    private final ResourceMetadataService resourceMetadataService;

    public Flux<FileUploadStatus> uploadFiles(Flux<FilePart> filePartFlux, String folder) {
        return filePartFlux.flatMap(filePart -> {
            String filename = filePart.filename();
            MediaType contentType = Optional.ofNullable(filePart.headers().getContentType())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return resourceMetadataService.save(folder, filename).flatMap(resourceMetadata ->
                s3Service.uploadFile(filePart.content(), resourceMetadata.getFilePath(),
                        contentType)
                    .then(resourceMetadataService.updateStatus(resourceMetadata.getId(),
                        ResourceStatus.COMPLETE))
                    .thenReturn(FileUploadStatus.success(filename, resourceMetadata.getAccessKey()))
                    .onErrorResume(
                        throwable -> resourceMetadataService.updateStatus(resourceMetadata.getId(),
                                ResourceStatus.ERROR)
                            .then(Mono.just(FileUploadStatus.error(filename,
                                resourceMetadata.getAccessKey())))));
        });
    }

}
