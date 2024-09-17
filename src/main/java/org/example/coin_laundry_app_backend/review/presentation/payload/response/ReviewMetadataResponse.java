package org.example.coin_laundry_app_backend.review.presentation.payload.response;

public record ReviewMetadataResponse(
    Long laundryId,
    Long reviewCount,
    Double averageRating
) {

}
