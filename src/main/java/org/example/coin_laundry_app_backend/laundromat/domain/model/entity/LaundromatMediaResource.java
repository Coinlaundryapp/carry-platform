package org.example.coin_laundry_app_backend.laundromat.domain.model.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("laundromat_media_resources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaundromatMediaResource {

    @Id
    private Long id;
    private Long laundromatId;
    private String mediaUrl;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public LaundromatMediaResource(Long laundromatId, String imageUrl) {
        this.laundromatId = laundromatId;
        this.mediaUrl = imageUrl;
    }
}
