package com.carry_laundry.carry_backend.media_resource.repository;

import com.carry_laundry.carry_backend.media_resource.domain.ResourceMetadata;
import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ResourceMetadataRepository extends ReactiveCrudRepository<ResourceMetadata, Long> {

    @Override
    @NonNull
    @Query("INSERT INTO resource_metadata (folder_name, access_key, extension, status) VALUES (:#{#entity.folderName},:#{#entity.accessKey}, :#{#entity.extension}, :#{#entity.status}::resource_status) RETURNING *")
    <S extends ResourceMetadata> Mono<S> save(@NonNull S entity);

    @Query("UPDATE resource_metadata SET status=:status::resource_status WHERE id=:id")
    Mono<Void> updateStatusById(@NonNull Long id, @NonNull ResourceStatus resourceStatus);

    Mono<ResourceMetadata> findByFolderNameAndAccessKey(String folderName, UUID accessKey);

    @Query("UPDATE resource_metadata SET is_valid=:isValid WHERE folder_name=:folderName AND access_key=:accessKey RETURNING *")
    Mono<ResourceMetadata> updateIsValidByFolderNameAndAccessKey(@NonNull String folderName,
        @NonNull UUID accessKey, @NonNull Boolean isValid);
}
