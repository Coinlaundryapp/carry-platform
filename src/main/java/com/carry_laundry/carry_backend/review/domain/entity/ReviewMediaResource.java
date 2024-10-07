package com.carry_laundry.carry_backend.review.domain.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("review_media_resources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewMediaResource {

    @Id
    private Long id;
    private Long reviewId;
    private String mediaUri;
    private String extension;

    @Builder
    private ReviewMediaResource(Long reviewId, String mediaUri) {
        this.reviewId = Objects.requireNonNull(reviewId, "reviewId must not be null");
        this.mediaUri = Objects.requireNonNull(mediaUri, "mediaUri must not be null");
        this.extension = mediaUri.substring(mediaUri.lastIndexOf(".") + 1);
    }

}
