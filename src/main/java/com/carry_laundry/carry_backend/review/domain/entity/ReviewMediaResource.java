package com.carry_laundry.carry_backend.review.domain.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("review_media_resources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewMediaResource {

    private Long reviewId;
    private String mediaUrl;
    private String extension;

}
