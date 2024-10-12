package com.carry_laundry.carry_backend.media_resource.application;

import com.carry_laundry.carry_backend.media_resource.domain.ResourceMetadata;
import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import com.carry_laundry.carry_backend.media_resource.repository.ResourceMetadataRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ResourceMetadataService {

    private final ResourceMetadataRepository resourceMetadataRepository;

    @Transactional
    public Mono<ResourceMetadata> save(String folder, String uploadFilename) {
        return resourceMetadataRepository.save(ResourceMetadata.builder()
            .folderName(folder)
            .extension(extractExtension(uploadFilename))
            .status(ResourceStatus.UPLOADING)
            .build()
        );
    }

    @Transactional(readOnly = true)
    public Mono<ResourceMetadata> findByFolderNameAndAccessKey(String folder, UUID accessKey) {
        return resourceMetadataRepository.findByFolderNameAndAccessKey(folder, accessKey);
    }

    @Transactional
    public Mono<Void> updateStatus(Long id, ResourceStatus status) {
        return resourceMetadataRepository.updateStatusById(id, status);
    }

    @Transactional
    public Mono<List<String>> updateValidations(String folder, List<UUID> accessKeys,
        boolean isValid) {
        return Flux.fromIterable(accessKeys)
            .flatMap(accessKey -> resourceMetadataRepository.updateIsValidByFolderNameAndAccessKey(
                folder, accessKey, isValid))
            .flatMap(resourceMetadata -> {
                if (resourceMetadata.getStatus().equals(ResourceStatus.ERROR)) {
                    return Mono.error(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Resource is in error state"));
                }
                return Mono.just(resourceMetadata.getFilePath());
            })
            .collectList();
    }

    private String extractExtension(String filename) {
        int lastDotIdx = filename.lastIndexOf(".");
        if (lastDotIdx != -1 && lastDotIdx < filename.length() - 1) {
            return filename.substring(lastDotIdx + 1);
        }
        return "octet-stream";
    }
}
