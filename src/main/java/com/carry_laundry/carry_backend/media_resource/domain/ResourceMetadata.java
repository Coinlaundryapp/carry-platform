package com.carry_laundry.carry_backend.media_resource.domain;

import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("resource_metadata")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceMetadata {

    @Id
    private UUID id;
    private String filename;
    private ResourceStatus status;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private ResourceMetadata(String filename, ResourceStatus status) {
        this.id = UUID.randomUUID();
        this.filename = Objects.requireNonNull(filename, "fileName must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void complete() {
        this.status = ResourceStatus.COMPLETE;
    }
}
