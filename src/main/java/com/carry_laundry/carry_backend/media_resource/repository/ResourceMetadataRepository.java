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
public interface ResourceMetadataRepository extends ReactiveCrudRepository<ResourceMetadata, UUID> {

    @Override
    @NonNull
    @Query("INSERT INTO resource_metadata (id, folder_name, extension, status) VALUES (:#{#entity.id}, :#{#entity.folderName}, :#{#entity.extension},:#{#entity.status}::resource_status) RETURNING *")
    <S extends ResourceMetadata> Mono<S> save(@NonNull S entity);

    @Query("UPDATE resource_metadata SET status=:status::resource_status, updated_at=NOW() WHERE id=:id")
    Mono<Void> updateStatusById(UUID id, ResourceStatus resourceStatus);

}
