package org.example.coin_laundry_app_backend.review.domain.model.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("review_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage {

    private Long reviewId;
    private String imageUrl;

}
