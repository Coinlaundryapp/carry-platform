package com.carry_laundry.carry_backend.media_resource.application;

import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MediaResourceUploadService {

    private final S3Service s3Service;
    private final ResourceMetadataService resourceMetadataService;

    public Flux<String> uploadFiles(Flux<FilePart> filePartFlux, String folder) {
        return filePartFlux.concatMap(filePart -> {
            String filename = filePart.filename();
            return resourceMetadataService.save(folder, filename).flatMap(resourceMetadata ->
                s3Service.uploadFile(filePart.content(), resourceMetadata.getFilePath())
                    .then(resourceMetadataService.updateStatus(resourceMetadata.getId(),
                        ResourceStatus.COMPLETE))
                    .thenReturn(resourceMetadata.getId().toString())
                    .onErrorResume(
                        throwable -> resourceMetadataService.updateStatus(resourceMetadata.getId(),
                            ResourceStatus.ERROR).then(Mono.error(throwable))));
        });
    }

}
