package org.example.coin_laundry_app_backend.review.presentation.payload.response;

public record ReviewStatisticResponse(
    Long laundryId,
    Long reviewCount,
    Double averageRating
) {

}
