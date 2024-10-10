package com.carry_laundry.carry_backend.review.application.record;

import com.carry_laundry.carry_backend.review.domain.entity.ReviewMediaResource;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewDetailData(
    Long id,
    Long laundromatId,
    Long userId,
    String comment,
    Integer reviewRating,
    List<ReviewMediaResource> reviewMediaResources,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

}
