package org.example.coin_laundry_app_backend.common.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
abstract public class AbstractBaseEntity {
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;
}
