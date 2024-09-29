package com.carry_laundry.carry_backend.media_resource.repository;

import com.carry_laundry.carry_backend.media_resource.domain.ResourceMetadata;
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
    @Query("INSERT INTO resource_metadata (id, filename, status) VALUES (:#{#entity.id}, :#{#entity.filename}, :#{#entity.status}::resource_status) RETURNING *")
    <S extends ResourceMetadata> Mono<S> save(@NonNull S entity);
}
