package org.example.coin_laundry_app_backend.laundry.domain.model.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("laundry_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaundryImage {

    @Id
    private Long id;
    private Long laundryId;
    private String imageUrl;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public LaundryImage(Long laundryId, String imageUrl) {
        this.laundryId = laundryId;
        this.imageUrl = imageUrl;
    }
}
