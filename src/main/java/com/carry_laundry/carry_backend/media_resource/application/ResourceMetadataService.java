package com.carry_laundry.carry_backend.media_resource.application;

import com.carry_laundry.carry_backend.media_resource.domain.ResourceMetadata;
import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import com.carry_laundry.carry_backend.media_resource.repository.ResourceMetadataRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ResourceMetadataService {

    private final ResourceMetadataRepository resourceMetadataRepository;

    public Mono<ResourceMetadata> save(String folder, String uploadFilename) {
        return resourceMetadataRepository.save(ResourceMetadata.builder()
            .folderName(folder)
            .extension(extractExtension(uploadFilename))
            .status(ResourceStatus.UPLOADING)
            .build()
        );
    }

    public Mono<ResourceMetadata> findByFolderNameAndAccessKey(String folder, UUID accessKey) {
        return resourceMetadataRepository.findByFolderNameAndAccessKey(folder, accessKey);
    }

    public Mono<Void> updateStatus(Long id, ResourceStatus status) {
        return resourceMetadataRepository.updateStatusById(id, status);
    }

    private String extractExtension(String filename) {
        int lastDotIdx = filename.lastIndexOf(".");
        if (lastDotIdx != -1 && lastDotIdx < filename.length() - 1) {
            return filename.substring(lastDotIdx + 1);
        }
        return "octet-stream";
    }
}
